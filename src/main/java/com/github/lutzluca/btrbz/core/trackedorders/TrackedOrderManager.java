package com.github.lutzluca.btrbz.core.trackedorders;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.core.config.ConfigScreen.OptionGrouping;
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
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.data.TimedStore;
import com.github.lutzluca.btrbz.utils.Notifier;
import com.github.lutzluca.btrbz.utils.Utils;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.network.chat.Component;

@Slf4j
public class TrackedOrderManager {

    private final BazaarData bazaarData;

    private final List<TrackedOrder> trackedOrders = new ArrayList<>();
    private final TimedStore<OutstandingOrderInfo> outstandingOrderStore;
    private final TrackedOrderProductUpdater productUpdater;
    private final TrackedOrderStatusEvaluator statusEvaluator = new TrackedOrderStatusEvaluator();
    private final SelfUndercutDetector selfUndercutDetector = new SelfUndercutDetector();

    private final List<Consumer<TrackedOrder>> onOrderAddedListeners = new ArrayList<>();
    private final List<Consumer<TrackedOrder>> onOrderRemovedListeners = new ArrayList<>();
    private final List<Consumer<TrackedOrder>> onOrderUpdatedListeners = new ArrayList<>();
    private final List<Runnable> onOrdersResetListeners = new ArrayList<>();
    private BiConsumer<List<UnfilledOrderInfo>, List<FilledOrderInfo>> onSyncCompletedCallback =
        (unfilledOrders, filledOrders) -> { };

    public TrackedOrderManager(BazaarData bazaarData) {
        this.bazaarData = bazaarData;
        this.productUpdater = new TrackedOrderProductUpdater(bazaarData);
        this.outstandingOrderStore = new TimedStore<>(15_000L);
        this.bazaarData.addIndexChangeListener(this::refreshTrackedOrderProducts);
    }

    private void refreshTrackedOrderProducts() {
        this.trackedOrders.forEach(order ->
            this.updateTrackedProduct(order, this.productUpdater.resolveCurrentProduct(order))
        );
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
                order.productName
            );
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
            order.uiProductName
        );
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

    public void afterOrderSync(BiConsumer<List<UnfilledOrderInfo>, List<FilledOrderInfo>> cb) {
        this.onSyncCompletedCallback = cb;
    }

    public void syncOrders(List<OrderInfo> parsedOrders) {
        log.debug("Syncing orders with parsed order from the UI: {}", parsedOrders);
        var toRemove = new ArrayList<TrackedOrder>();
        var remaining = new ArrayList<>(parsedOrders);

        var filledOrders = new ArrayList<FilledOrderInfo>();
        var unfilledOrders = new ArrayList<UnfilledOrderInfo>();
        for (var order : remaining) {
            switch (order) {
                case FilledOrderInfo filled -> filledOrders.add(filled);
                case UnfilledOrderInfo unfilled -> unfilledOrders.add(unfilled);
            }
        }

        var unfilledCopy = new ArrayList<>(unfilledOrders);

        for (var tracked : this.trackedOrders) {
            var match = unfilledCopy.stream().filter(tracked::matches).findFirst();

            match.ifPresentOrElse(
                info -> {
                    unfilledCopy.remove(info);
                    this.updateTrackedProduct(tracked, info.product());
                    tracked.slot = info.slotIdx();
                    tracked.fillAmountSnapshot = info.filledAmountSnapshot();
                }, () -> toRemove.add(tracked)
            );
        }

        log.debug(
            "Tracked orders: {}, toRemove: {}, toAdd: {}",
            this.trackedOrders,
            toRemove,
            unfilledOrders
        );

        toRemove.forEach(this::removeTrackedOrder);
        unfilledCopy
            .stream()
            .map(TrackedOrder::new)
            .forEach(this::addTrackedOrder);

        this.onSyncCompletedCallback.accept(unfilledOrders, filledOrders);
    }

    private void removeTrackedOrder(TrackedOrder order) {
        if (this.trackedOrders.remove(order)) {
            this.selfUndercutDetector.removeIfLastOrder(order, this.trackedOrders);
            this.onOrderRemovedListeners.forEach(listener -> listener.accept(order));
        }
    }

    public void onBazaarUpdate(MarketSnapshot snapshot) {
        var updates = this.statusEvaluator
            .computeStatusUpdates(this.trackedOrders, snapshot)
            .peek(update -> update.order().status = update.curr())
            .collect(Collectors.toList());

        this.sendNotifications(updates, snapshot);
        this.resolveSelfUndercutStates(snapshot);
    }

    // Known limitation: transitions that only change `GroupStatus` without changing the underlying
    // `OrderStatus` variant are not detected. Concretely, if a stranger cancels their order from
    // your bucket, all your orders stay `OrderStatus.Matched`, no `sameVariant` change fires, so
    // no `StatusUpdate` is produced, and `sendNotifications` never processes the group.
    // Fixing this would require a separate group-level status diff pass (tracking previous
    // `GroupStatus` across polls), which adds meaningful complexity for a low-value scenario.
    // Accepted as a known limitation (for now).
    private void sendNotifications(List<StatusUpdate> statusUpdates, MarketSnapshot snapshot) {
        var cfg = ConfigManager.get().trackedOrders;
        if(!cfg.enabled) {
            return;
        }

        Map<TrackedOrderGrouping.GroupMatchKey, List<TrackedOrder>> orderGroups = this.trackedOrders.stream()
            .collect(Collectors.groupingBy(TrackedOrderGrouping.GroupMatchKey::from));
        Map<TrackedOrderGrouping.GroupMatchKey, List<StatusUpdate>> statusGroups = statusUpdates.stream()
            .collect(Collectors.groupingBy(update -> TrackedOrderGrouping.GroupMatchKey.from(update.order())));

        for(var entry : statusGroups.entrySet()) {
            var updates = entry.getValue();
            var orders = orderGroups.get(entry.getKey());
            
            if(orders.size() == 1) {
                var statusUpdate = updates.getFirst();
                if(this.shouldNotify(statusUpdate)) {
                    Notifier.notifyOrderStatus(statusUpdate, bazaarData);
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
        var cfg = ConfigManager.get().trackedOrders;
        
        if(!cfg.groupOrders) {
            updates.stream()
                .filter(this::shouldNotify)
                .forEach(update -> Notifier.notifyOrderStatus(update, bazaarData));
            return;   
        }

        GroupStatus curr = this.statusEvaluator.getCurrentGroupStatus(key, orders, snapshot);
        
        if(curr == null) {
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
        Notifier.notifyGroupOrderStatus(key, orders, curr, prev, this.bazaarData);
    }

    private boolean shouldNotify(StatusUpdate update) {
        var cfg = ConfigManager.get().trackedOrders;

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

    public void resetTrackedOrders() {
        var removedSize = this.trackedOrders.size();
        this.trackedOrders.clear();
        this.selfUndercutDetector.clear();

        log.info("Reset tracked orders (removed {})", removedSize);
        this.onOrdersResetListeners.forEach(Runnable::run);
    }

    public List<TrackedOrder> getTrackedOrders() {
        return List.copyOf(this.trackedOrders);
    }

    public void addTrackedOrder(TrackedOrder order) {
        this.trackedOrders.add(order);
        this.onOrderAddedListeners.forEach(listener -> listener.accept(order));
    }

    public void removeMatching(OrderFilled info) {
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
                    "managebazaarorders"
                )
            );
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
                            "Failed to find a matching outstanding order for: %s for %sx %s totalling %s | click to resync tracked orders",
                            info.type() == OrderType.Buy ? "Buy Order" : "Sell Offer",
                            info.volume(),
                            info.productName(),
                            Utils.formatDecimal(info.total(), 1, true)
                        ), "managebazaarorders"
                    );
                }
            );
    }

    private void resolveSelfUndercutStates(MarketSnapshot snapshot) {
        var cfg = ConfigManager.get().trackedOrders;
        var events = this.selfUndercutDetector.resolve(this.trackedOrders, snapshot);
        if (!cfg.enabled || !cfg.notifySelfUndercut) {
            return;
        }

        for (var event : events) {
            Notifier.notifySelfUndercut(
                event.key(),
                event.bestPrice(),
                event.secondBestPrice(),
                this.bazaarData
            );
        }
    }

    public static class OrderManagerConfig {

        public boolean enabled = true;

        public boolean notifyBest = true;
        public boolean onlyOnPriorityRegain = true;
        public boolean soundBest = false;
        public boolean notifyMatched = true;
        public boolean soundMatched = true;
        public boolean notifyUndercut = true;
        public boolean soundUndercut = true;
        public boolean notifySelfUndercut = true;

        public Action gotoOnMatched = Action.Order;
        public Action gotoOnUndercut = Action.Order;

        public boolean showQueueInfo = true;
        public QueueDisplayMode queueDisplayMode = QueueDisplayMode.Both;

        // Not yet fully tested, ghost order interactions with grouped status transitions
        // (e.g. Matched → SelfMatched) are still unreliable. Disabled by default until resolved.
        public boolean groupOrders = false;
        public boolean includePricePerUnit = false;

        public OptionGroup createGroup() {
            var notifyBestGroup = new OptionGrouping(this.createNotifyBestOption())
                .addOptions(
                    this.createNotifyBestOnPriorityRegain(),
                    this.createSoundBestOption()
                );

            var queueGroup = new OptionGrouping(this.createShowQueueInfoOption())
                .addOptions(this.createQueueDisplayModeOption());

            var rootGroup = new OptionGrouping(this.createEnabledOption())
                .addOptions(
                    this.createGroupOrdersOption(),
                    this.createIncludePricePerUnitOption(),
                    this.createGotoMatchedOption(),
                    this.createGotoUndercutOption(),
                    this.createNotifyMatchedOption(),
                    this.createSoundMatchedOption(),
                    this.createNotifyUndercutOption(),
                    this.createSoundUndercutOption(),
                    this.createNotifySelfUndercutOption()
                )
                .addSubgroups(notifyBestGroup, queueGroup);

            return OptionGroup
                .createBuilder()
                .name(Component.literal("Order Notifications"))
                .description(ConfigScreen.createDescription(
                    "Receive clickable messages when a tracked order becomes top, shares the best price, or is undercut.",
                    ConfigScreen.ConfigImage.ORDER_NOTIFICATION
                ))
                .options(rootGroup.build())
                .collapsed(true)
                .build();
        }

        private Option.Builder<Action> createGotoMatchedOption() {
            return Option
                .<Action>createBuilder()
                .name(Component.literal("Matched Notification Opens"))
                .description(OptionDescription.of(Component.literal(
                    "Choose where a clicked notification goes when your order shares the best price with other orders.")))
                .binding(
                    Action.Order,
                    () -> this.gotoOnMatched != null ? this.gotoOnMatched : Action.Order,
                    action -> this.gotoOnMatched = action
                )
                .controller(Action::controller);
        }

        private Option.Builder<Action> createGotoUndercutOption() {
            return Option
                .<Action>createBuilder()
                .name(Component.literal("Undercut Notification Opens"))
                .description(OptionDescription.of(Component.literal(
                    "Choose where a clicked notification goes when another order takes priority over yours.")))
                .binding(
                    Action.Order,
                    () -> this.gotoOnUndercut != null ? this.gotoOnUndercut : Action.Order,
                    action -> this.gotoOnUndercut = action
                )
                .controller(Action::controller);
        }

        private Option.Builder<Boolean> createShowQueueInfoOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Show Queue Info"))
                .binding(true, () -> this.showQueueInfo, val -> this.showQueueInfo = val)
                .description(OptionDescription.of(Component.literal(
                    "Add an estimate of the competing orders or items ahead of yours to matched and undercut notifications.")))
                .controller(ConfigScreen::createBooleanController);
        }

        private Option.Builder<QueueDisplayMode> createQueueDisplayModeOption() {
            return Option
                .<QueueDisplayMode>createBuilder()
                .name(Component.literal("Queue Display Mode"))
                .binding(
                    QueueDisplayMode.Both,
                    () -> this.queueDisplayMode != null ? this.queueDisplayMode : QueueDisplayMode.Both,
                    mode -> {
                        this.queueDisplayMode = mode;
                        BtrBz.tooltipProvider().clearCache();
                    }
                )
                .description(OptionDescription.of(Component.literal(
                    "Choose whether queue information counts competing orders, competing items, or both. Available only while Show Queue Info is enabled.")))
                .controller(QueueDisplayMode::controller);
        }

        private Option.Builder<Boolean> createNotifyBestOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Notify When Order Becomes Top"))
                .binding(true, () -> this.notifyBest, val -> this.notifyBest = val)
                .description(OptionDescription.of(Component.literal(
                    "Send a message when your order reaches the best available price and has trading priority.")))
                .controller(ConfigScreen::createBooleanController);
        }

        private Option.Builder<Boolean> createNotifyBestOnPriorityRegain() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.nullToEmpty("Only When Regaining Top Position"))
                .binding(
                    true,
                    () -> this.onlyOnPriorityRegain,
                    val -> this.onlyOnPriorityRegain = val
                )
                .description(OptionDescription.of(Component.nullToEmpty(
                    "Skip the initial top-position message and notify only after an order loses and later regains the best price. Available only while top-position notifications are enabled.")))
                .controller(ConfigScreen::createBooleanController);
        }

        private Option.Builder<Boolean> createNotifyMatchedOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Notify When Order Is Matched"))
                .binding(true, () -> this.notifyMatched, val -> this.notifyMatched = val)
                .description(OptionDescription.of(Component.literal(
                    "Send a message when your order shares the best price with one or more competing orders.")))
                .controller(ConfigScreen::createBooleanController);
        }

        private Option.Builder<Boolean> createNotifyUndercutOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Notify When Order Is Undercut"))
                .binding(true, () -> this.notifyUndercut, val -> this.notifyUndercut = val)
                .description(OptionDescription.of(Component.literal(
                    "Send a message when another buy order outbids yours or another sell offer lists for less.")))
                .controller(ConfigScreen::createBooleanController);
        }

        private Option.Builder<Boolean> createSoundBestOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Play Sound for Top Position"))
                .binding(false, () -> this.soundBest, val -> this.soundBest = val)
                .description(OptionDescription.of(Component.literal(
                    "Play a sound with the top-position notification. Available only while that notification is enabled.")))
                .controller(ConfigScreen::createBooleanController);
        }

        private Option.Builder<Boolean> createSoundMatchedOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Play Sound for Matched Order"))
                .binding(true, () -> this.soundMatched, val -> this.soundMatched = val)
                .description(OptionDescription.of(Component.literal(
                    "Play a sound when an order begins sharing the best price.")))
                .controller(ConfigScreen::createBooleanController);
        }

        private Option.Builder<Boolean> createSoundUndercutOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Play Sound for Undercut Order"))
                .binding(true, () -> this.soundUndercut, val -> this.soundUndercut = val)
                .description(OptionDescription.of(Component.literal(
                    "Play a sound when another order takes priority over yours.")))
                .controller(ConfigScreen::createBooleanController);
        }

        private Option.Builder<Boolean> createGroupOrdersOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Group Same-Price Orders (Experimental)"))
                .binding(false, () -> this.groupOrders, val -> this.groupOrders = val)
                .description(OptionDescription.of(Component.literal(
                    "Combine your orders for the same product, side, and price into one notification. Some unusual status transitions are not fully tested.")))
                .controller(ConfigScreen::createBooleanController);
        }

        private Option.Builder<Boolean> createNotifySelfUndercutOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Warn When Your Orders Compete"))
                .binding(true, () -> this.notifySelfUndercut, val -> this.notifySelfUndercut = val)
                .description(OptionDescription.of(Component.literal(
                    "Warn when you have multiple orders for the same product and one of your own prices takes priority over another.")))
                .controller(ConfigScreen::createBooleanController);
        }

        private Option.Builder<Boolean> createIncludePricePerUnitOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Include Price Per Unit"))
                .binding(false, () -> this.includePricePerUnit, val -> this.includePricePerUnit = val)
                .description(OptionDescription.of(Component.literal(
                    "Include each order's unit price in status notification messages.")))
                .controller(ConfigScreen::createBooleanController);
        }

        private Option.Builder<Boolean> createEnabledOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Enable Order Notifications"))
                .binding(true, () -> this.enabled, val -> this.enabled = val)
                .description(OptionDescription.of(Component.literal(
                    "Track your Bazaar orders and send messages when their market position changes.")))
                .controller(ConfigScreen::createBooleanController);
        }

        public enum Action {
            None,
            Item,
            Order;

            public static EnumControllerBuilder<Action> controller(Option<Action> option) {
                return EnumControllerBuilder
                    .create(option)
                    .enumClass(Action.class)
                    .formatValue(action -> switch (action) {
                        case None -> Component.literal("No action");
                        case Item -> Component.literal("Go to Item in Bazaar");
                        case Order -> Component.literal("Open Manage Bazaar Orders");
                    });
            }
        }

        public enum QueueDisplayMode {
            Both,
            ItemsOnly;

            public static EnumControllerBuilder<QueueDisplayMode> controller(Option<QueueDisplayMode> option) {
                return EnumControllerBuilder
                    .create(option)
                    .enumClass(QueueDisplayMode.class)
                    .formatValue(mode -> switch (mode) {
                        case Both -> Component.literal("Orders and Items");
                        case ItemsOnly -> Component.literal("Items Only");
                    });
            }
        }
    }
}
