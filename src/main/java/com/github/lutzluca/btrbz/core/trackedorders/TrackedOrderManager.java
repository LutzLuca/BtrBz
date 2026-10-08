package com.github.lutzluca.btrbz.core.trackedorders;

import com.github.lutzluca.btrbz.core.config.ConfigStore;
import com.github.lutzluca.btrbz.cache.CacheToken;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.BazaarData.MarketSnapshot;
import com.github.lutzluca.btrbz.data.BazaarMessageDispatcher.BazaarMessage.OrderFilled;
import com.github.lutzluca.btrbz.data.BazaarMessageDispatcher.BazaarMessage.OrderSetup;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo.FilledOrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo.UnfilledOrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.OrderModels.OutstandingOrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrder;
import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrderId;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.data.TimedStore;
import com.github.lutzluca.btrbz.utils.Notifier;
import com.github.lutzluca.btrbz.utils.Utils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TrackedOrderManager {

    private final BazaarData bazaarData;
    private final Supplier<OrderManagerConfig> config;

    private final List<TrackedOrder> trackedOrders = new ArrayList<>();
    private final List<TrackedOrder> displayOrders = new ArrayList<>();
    private final TimedStore<OutstandingOrderInfo> outstandingOrderStore;
    private final TrackedOrderProductUpdater productUpdater;
    private final TrackedOrderStatusEvaluator statusEvaluator = new TrackedOrderStatusEvaluator();
    private final SelfUndercutDetector selfUndercutDetector = new SelfUndercutDetector();
    @Getter
    @Accessors(fluent = true)
    private final CacheToken dataChanges = CacheToken.named("tracked-orders.data");
    @Getter
    @Accessors(fluent = true)
    private int filledOrderCount;

    private final List<Consumer<TrackedOrder>> onOrderAddedListeners = new ArrayList<>();
    private final List<Consumer<TrackedOrder>> onOrderRemovedListeners = new ArrayList<>();
    private final List<Consumer<TrackedOrder>> onOrderUpdatedListeners = new ArrayList<>();
    private final List<Runnable> onOrdersResetListeners = new ArrayList<>();
    private Consumer<List<OrderInfo>> onSyncCompletedCallback = _ -> {};

    public TrackedOrderManager(BazaarData bazaarData) {
        this(bazaarData, () -> ConfigStore.get().config().trackedOrders);
    }

    public TrackedOrderManager(BazaarData bazaarData, Supplier<OrderManagerConfig> config) {
        this.bazaarData = bazaarData;
        this.config = config;
        this.productUpdater = new TrackedOrderProductUpdater(bazaarData);
        this.outstandingOrderStore = new TimedStore<>(15_000L);
        this.bazaarData.addIndexChangeListener(this::refreshTrackedOrderProducts);
    }

    private void refreshTrackedOrderProducts() {
        this.trackedOrders
            .forEach(order -> this.updateTrackedProduct(order, this.productUpdater.resolveCurrentProduct(order)));
    }

    private void updateTrackedProduct(TrackedOrder order, ProductIdentity product) {
        var mergedProduct = this.productUpdater.strongestProduct(order.product, product, order.uiProductName);
        var oldKey = TrackedOrderGrouping.productKey(order.product, order.uiProductName);
        var oldProductName = order.productName;
        var newKey = TrackedOrderGrouping.productKey(mergedProduct, order.uiProductName);
        if (oldKey.equals(newKey)
            && oldProductName.equals(mergedProduct.strippedName())
            && order.product.equals(mergedProduct)) {
            return;
        }

        var oldSelfUndercutKey = TrackedOrderGrouping.SelfUndercutMatchKey.from(order);
        order.applyProduct(mergedProduct);

        if (oldKey.equals(newKey)) {
            log.debug(
                "Updated tracked order display name for {} from '{}' to '{}'",
                newKey,
                oldProductName,
                order.productName);
            this.dataChanges.invalidate("tracked order product updated");
            this.notifyOrderUpdated(order);
            return;
        }

        // The previous status belongs to the old grouping key; the next market poll recomputes it normally.
        order.status = new OrderStatus.Unknown();
        this.selfUndercutDetector.remove(oldSelfUndercutKey);
        this.selfUndercutDetector.remove(TrackedOrderGrouping.SelfUndercutMatchKey.from(order));
        log.debug(
            "Updated tracked order identity from {} to {} using UI product '{}'",
            oldKey,
            newKey,
            order.uiProductName);
        this.dataChanges.invalidate("tracked order product updated");
        this.notifyOrderUpdated(order);
    }

    private void notifyOrderUpdated(TrackedOrder order) {
        this.onOrderUpdatedListeners.forEach(listener -> listener.accept(order));
    }

    public void addOnOrderAddedListener(Consumer<TrackedOrder> listener) {
        this.onOrderAddedListeners.add(listener);
    }

    /**
     * Add a listener for when an individual order is removed.
     * <p>
     * <b>Note:</b> This listener is NOT called when the entire list is cleared via {@link #resetTrackedOrders()}.
     * Use {@link #addOnOrdersResetListener(Runnable)} to handle batch clears.
     */
    public void addOnOrderRemovedListener(Consumer<TrackedOrder> listener) {
        this.onOrderRemovedListeners.add(listener);
    }

    public void addOnOrderUpdatedListener(Consumer<TrackedOrder> listener) {
        this.onOrderUpdatedListeners.add(listener);
    }

    public void addOnOrdersResetListener(Runnable listener) {
        this.onOrdersResetListeners.add(listener);
    }

    public void afterOrderSync(Consumer<List<OrderInfo>> cb) {
        this.onSyncCompletedCallback = cb;
    }

    public void syncOrders(List<OrderInfo> parsedOrders) {
        log.debug("Syncing orders with parsed order from the UI: {}", parsedOrders);
        var toRemove = new ArrayList<TrackedOrder>();
        var snapshot = List.copyOf(parsedOrders);

        var filledOrders = new ArrayList<FilledOrderInfo>();
        var unfilledOrders = new ArrayList<UnfilledOrderInfo>();
        for (var order : snapshot) {
            switch (order) {
                case FilledOrderInfo filled -> filledOrders.add(filled);
                case UnfilledOrderInfo unfilled -> unfilledOrders.add(unfilled);
                case OrderInfo.ExpiredOrderInfo _ -> {
                }
            }
        }

        if (this.filledOrderCount != filledOrders.size()) {
            this.filledOrderCount = filledOrders.size();
            this.dataChanges.invalidate("filled order count synchronized");
        }

        var unfilledCopy = new ArrayList<>(unfilledOrders);

        for (var tracked : this.trackedOrders) {
            var match = unfilledCopy.stream().filter(tracked::matches).findFirst();

            match.ifPresentOrElse(
                info -> {
                    unfilledCopy.remove(info);
                    this.updateTrackedProduct(tracked, info.product());
                    int slot = info.slotIdx();
                    int fill = info.filledAmountSnapshot();

                    if (tracked.slot != slot || tracked.fillAmountSnapshot != fill) {
                        tracked.slot = slot;
                        tracked.fillAmountSnapshot = fill;
                        this.dataChanges.invalidate("tracked order slot or fill updated");
                    }
                }, () -> toRemove.add(tracked));
        }

        toRemove.forEach(this::removeTrackedOrder);
        unfilledCopy
            .stream()
            .map(TrackedOrder::new)
            .forEach(this::addTrackedOrder);

        this.onSyncCompletedCallback.accept(snapshot);
    }

    private void removeTrackedOrder(TrackedOrder order) {
        if (this.trackedOrders.remove(order)) {
            this.displayOrders.remove(order);
            this.dataChanges.invalidate("tracked order removed");
            this.selfUndercutDetector.removeIfLastOrder(order, this.trackedOrders);
            this.onOrderRemovedListeners.forEach(listener -> listener.accept(order));
        }
    }

    public void onBazaarUpdate(MarketSnapshot snapshot) {
        if (!snapshot.available()) {
            if (!this.trackedOrders.isEmpty()) {
                log.debug("Market unavailable; resetting {} tracked order statuses", this.trackedOrders.size());
            }
            this.trackedOrders.forEach(order -> order.status = new OrderStatus.Unknown());
            this.selfUndercutDetector.clear();
            this.dataChanges.invalidate("market unavailable");
            return;
        }
        var statusUpdates = this.statusEvaluator
            .computeStatusUpdates(this.trackedOrders, snapshot)
            .toList();

        statusUpdates.forEach(update -> update.order().status = update.curr());
        if (!statusUpdates.isEmpty()) {
            this.dataChanges.invalidate("tracked order status updated");
        }

        var notificationUpdates = statusUpdates.stream()
            .filter(update -> !update.prev().sameVariant(update.curr()))
            .toList();

        this.sendNotifications(notificationUpdates, snapshot);
        this.resolveSelfUndercutStates(snapshot);
    }

    // Known limitation: transitions that only change `GroupStatus` without changing the underlying
    // `OrderStatus` variant are not detected. Concretely, if a stranger cancels their order from
    // your bucket, all your orders stay `OrderStatus.Matched`, no order-level status change is
    // emitted, and `sendNotifications` never processes the group.
    // Fixing this would require a separate group-level status diff pass (tracking previous
    // `GroupStatus` across polls), which adds meaningful complexity for a low-value scenario.
    // Accepted as a known limitation (for now).
    private void sendNotifications(List<StatusUpdate> statusUpdates, MarketSnapshot snapshot) {
        var cfg = this.config.get();
        if (!cfg.enabled) {
            return;
        }

        Map<TrackedOrderGrouping.GroupMatchKey, List<TrackedOrder>> orderGroups = this.trackedOrders.stream()
            .collect(Collectors.groupingBy(TrackedOrderGrouping.GroupMatchKey::from));
        Map<TrackedOrderGrouping.GroupMatchKey, List<StatusUpdate>> statusGroups = statusUpdates.stream()
            .collect(Collectors.groupingBy(update -> TrackedOrderGrouping.GroupMatchKey.from(update.order())));

        for (var entry : statusGroups.entrySet()) {
            var updates = entry.getValue();
            var orders = orderGroups.get(entry.getKey());

            if (orders.size() == 1) {
                var statusUpdate = updates.getFirst();
                if (this.shouldNotify(statusUpdate)) {
                    TrackedOrderNotifications.notifyOrderStatus(statusUpdate, this.bazaarData, cfg);
                }
                continue;
            }

            var key = GroupKey.from(orders.getFirst());
            this.processGroupNotification(key, orders, updates, snapshot);
        }
    }

    private void processGroupNotification(
        GroupKey key,
        List<TrackedOrder> orders,
        List<StatusUpdate> updates,
        MarketSnapshot snapshot
    ) {
        var cfg = this.config.get();

        if (!cfg.groupOrders) {
            updates.stream()
                .filter(this::shouldNotify)
                .forEach(update -> TrackedOrderNotifications.notifyOrderStatus(update, this.bazaarData, cfg));
            return;
        }

        GroupStatus curr = this.statusEvaluator.getCurrentGroupStatus(key, orders, snapshot);

        if (curr == null) {
            log.warn("Group ({}) has no settled status, skipping group notification", key);
            return;
        }

        boolean shouldNotify = switch (curr) {
            case GroupStatus.Undercut _ -> cfg.notifyUndercut;
            case GroupStatus.Matched _ -> cfg.notifyMatched;
            case GroupStatus.SelfMatched _ -> cfg.notifyMatched;
        };

        if (!shouldNotify) {
            return;
        }

        GroupStatus prev = this.statusEvaluator.getPreviousGroupStatus(key, orders, updates);
        TrackedOrderNotifications.notifyGroupOrderStatus(key, orders, curr, prev, this.bazaarData, cfg);
    }

    private boolean shouldNotify(StatusUpdate update) {
        var cfg = this.config.get();

        return cfg.enabled && switch (update.curr()) {
            case OrderStatus.Top _ -> {
                if (!cfg.notifyBest) {
                    yield false;
                }

                if (cfg.onlyOnPriorityRegain) {
                    yield !(update.prev() instanceof OrderStatus.Unknown);
                }

                yield true;
            }
            case OrderStatus.Matched _ -> cfg.notifyMatched;
            case OrderStatus.Undercut _ -> cfg.notifyUndercut;
            case OrderStatus.Unknown _ -> false;
        };
    }

    public void cancelOutstandingOrders() {
        this.outstandingOrderStore.clear();
    }

    public void resetTrackedOrders() {
        var removedSize = this.trackedOrders.size();
        this.trackedOrders.clear();
        this.displayOrders.clear();
        this.selfUndercutDetector.clear();
        this.filledOrderCount = 0;
        this.dataChanges.invalidate("tracked orders reset");

        log.info("Reset tracked orders (removed {})", removedSize);
        this.onOrdersResetListeners.forEach(Runnable::run);
    }

    public List<TrackedOrder> getTrackedOrders() {
        return List.copyOf(this.displayOrders);
    }

    public List<TrackedOrderSnapshot> currentOrders() {
        return this.displayOrders.stream().map(TrackedOrderSnapshot::from).toList();
    }

    /** Creation chronology, independent of the session-owned manual display order. */
    public List<TrackedOrderId> creationOrder() {
        return this.trackedOrders.stream().map(TrackedOrder::id).toList();
    }

    public boolean reorder(TrackedOrderId orderId, int dropIndex) {
        Optional<TrackedOrder> matchingOrder = this.displayOrders
            .stream()
            .filter(order -> order.id().equals(orderId))
            .findFirst();
        if (matchingOrder.isEmpty() || dropIndex < 0 || dropIndex > this.displayOrders.size()) {
            return false;
        }

        var order = matchingOrder.get();
        int sourceIdx = this.displayOrders.indexOf(order);
        int insertionIdx = dropIndex > sourceIdx ? dropIndex - 1 : dropIndex;

        if (insertionIdx == sourceIdx) {
            return false;
        }

        this.displayOrders.remove(sourceIdx);
        insertionIdx = Math.min(insertionIdx, this.displayOrders.size());
        this.displayOrders.add(insertionIdx, order);

        this.dataChanges.invalidate("tracked orders reordered");
        return true;
    }

    public void addTrackedOrder(TrackedOrder order) {
        this.trackedOrders.add(order);
        this.displayOrders.add(order);
        this.dataChanges.invalidate("tracked order added");
        this.onOrderAddedListeners.forEach(listener -> listener.accept(order));
    }

    public record TrackedOrderSnapshot(
        TrackedOrderId id,
        ProductIdentity product,
        String productName,
        String uiProductName,
        OrderType type,
        int volume,
        double pricePerUnit,
        OrderStatus status,
        int slot,
        int fillAmountSnapshot
    ) {
        private static TrackedOrderSnapshot from(TrackedOrder order) {
            return new TrackedOrderSnapshot(
                order.id(),
                order.product,
                order.productName,
                order.uiProductName,
                order.type,
                order.volume,
                order.pricePerUnit,
                order.status,
                order.slot,
                order.fillAmountSnapshot);
        }
    }

    public void handleOrderFilled(OrderFilled info) {
        this.filledOrderCount++;
        this.dataChanges.invalidate("filled order count changed");
        var orderingFactor = info.type() == OrderType.Buy ? -1 : 1;

        // noinspection SimplifyStreamApiCallChains
        this.trackedOrders
            .stream()
            .filter(order -> Utils
                .normalizeDisplayName(order.uiProductName)
                .equals(Utils.normalizeDisplayName(info.productName()))
                && order.type == info.type()
                && order.volume == info.volume())
            .sorted((t1, t2) -> orderingFactor * Double.compare(t1.pricePerUnit, t2.pricePerUnit))
            .findFirst()
            .ifPresentOrElse(
                this::removeTrackedOrder, () -> Notifier.notifyChatCommand(
                    "No matching tracked order found for filled order message. Resync orders",
                    "managebazaarorders"));
    }

    public void addOutstandingOrder(OutstandingOrderInfo info) {
        this.outstandingOrderStore.add(info);
    }

    public void confirmOutstanding(OrderSetup info) {
        this.outstandingOrderStore
            .removeFirstMatch(curr -> curr.matches(info))
            .map(TrackedOrder::new)
            .ifPresentOrElse(
                this::addTrackedOrder, () -> {
                    log.info("Failed to find a matching outstanding order for: {}", info);

                    Notifier.notifyChatCommand(
                        String.format(
                            "Failed to find a matching outstanding order for: %s for %sx %s totalling %s | "
                                + "click to resync tracked orders",
                            info.type() == OrderType.Buy ? "Buy Order" : "Sell Offer",
                            info.volume(),
                            info.productName(),
                            Utils.formatDecimal(info.total(), 1, true)),
                        "managebazaarorders");
                });
    }

    private void resolveSelfUndercutStates(MarketSnapshot snapshot) {
        var cfg = this.config.get();
        var events = this.selfUndercutDetector.resolve(this.trackedOrders, snapshot);
        if (!cfg.enabled || !cfg.notifySelfUndercut) {
            return;
        }

        for (var event : events) {
            TrackedOrderNotifications.notifySelfUndercut(
                event.key(),
                event.bestPrice(),
                event.secondBestPrice(),
                this.bazaarData);
        }
    }

}
