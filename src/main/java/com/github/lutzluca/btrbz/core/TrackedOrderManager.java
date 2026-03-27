package com.github.lutzluca.btrbz.core;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.core.config.ConfigScreen.OptionGrouping;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.BazaarMessageDispatcher.BazaarMessage.OrderFilled;
import com.github.lutzluca.btrbz.data.BazaarMessageDispatcher.BazaarMessage.OrderSetup;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo.FilledOrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo.UnfilledOrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus.Matched;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus.Top;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus.Undercut;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.OrderModels.OutstandingOrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrder;
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
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;

import lombok.extern.slf4j.Slf4j;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply.Product;
import net.minecraft.network.chat.Component;

@Slf4j
public class TrackedOrderManager {

    private final BazaarData bazaarData;

    private final List<TrackedOrder> trackedOrders = new ArrayList<>();
    private final TimedStore<OutstandingOrderInfo> outstandingOrderStore;

    private final List<Consumer<TrackedOrder>> onOrderAddedListeners = new ArrayList<>();
    private final List<Consumer<TrackedOrder>> onOrderRemovedListeners = new ArrayList<>();
    private final List<Runnable> onOrdersResetListeners = new ArrayList<>();
    private BiConsumer<List<UnfilledOrderInfo>, List<FilledOrderInfo>> onSyncCompletedCallback = null;

    public TrackedOrderManager(BazaarData bazaarData) {
        this.bazaarData = bazaarData;
        this.outstandingOrderStore = new TimedStore<>(15_000L);
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
        unfilledCopy.stream().map(TrackedOrder::new).forEach(this::addTrackedOrder);

        this.onSyncCompletedCallback.accept(unfilledOrders, filledOrders);
    }

    private void removeTrackedOrder(TrackedOrder order) {
        if (this.trackedOrders.remove(order)) {
            this.onOrderRemovedListeners.forEach(listener -> listener.accept(order));
        }
    }

    public record GroupKey(String productName, OrderType type, double pricePerUnit) {}

    public sealed interface GroupStatus {
        record Undercut(double amount) implements GroupStatus {}
        record Matched() implements GroupStatus {}
        record SelfMatched() implements GroupStatus {}
    }

    public void onBazaarUpdate(Map<String, Product> products) {
        var updates = this.computeStatusUpdates(products).peek(update -> {
            update.order().status = update.curr;
        }).collect(Collectors.toList());

        this.sendNotifications(updates, products);        
    }

    // Known limitation: transitions that only change `GroupStatus` without changing the underlying
    // `OrderStatus` variant are not detected. Concretely, if a stranger cancels their order from
    // your bucket, all your orders stay `OrderStatus.Matched`, no `sameVariant` change fires, so
    // no `StatusUpdate` is produced, and `sendNotifications` never processes the group.
    // Fixing this would require a separate group-level status diff pass (tracking previous
    // `GroupStatus` across polls), which adds meaningful complexity for a low-value scenario.
    // Accepted as a known limitation (for now).
    private void sendNotifications(List<StatusUpdate> statusUpdates, Map<String, Product> products) {
        var cfg = ConfigManager.get().trackedOrders;
        if(!cfg.enabled) {
            return;
        }

        Map<GroupKey, List<TrackedOrder>> orderGroups = this.trackedOrders.stream()
            .collect(Collectors.groupingBy(order -> new GroupKey(
                order.productName,
                order.type,
                order.pricePerUnit
            )));
        Map<GroupKey, List<StatusUpdate>> statusGroups = statusUpdates.stream()
            .collect(Collectors.groupingBy(update -> new GroupKey(
                update.order().productName,
                update.order().type,
                update.order().pricePerUnit
            )));

        for(var entry : statusGroups.entrySet()) {
            var key = entry.getKey();
            var updates = entry.getValue();
            var orders = orderGroups.get(key);
            
            if(orders.size() == 1) {
                var statusUpdate = updates.getFirst();
                if(this.shouldNotify(statusUpdate)) {
                    Notifier.notifyOrderStatus(statusUpdate, bazaarData);
                }
                continue;
            }

            this.processGroupNotification(key, orders, updates, products);
        }
    }

    private void processGroupNotification(
        GroupKey key,
        List<TrackedOrder> orders,
        List<StatusUpdate> updates,
        Map<String,Product> products
    ) {
        var cfg = ConfigManager.get().trackedOrders;
        
        if(!cfg.groupOrders) {
            updates.stream()
                .filter(this::shouldNotify)
                .forEach(update -> Notifier.notifyOrderStatus(update, bazaarData));
            return;   
        }

        GroupStatus curr = this.getCurrentGroupStatus(key, orders, products);
        
        if(curr == null) {
            log.warn("Group ({}) has no settled status, skipping group notification", key);
            return;
        }

        boolean shouldNotify = switch (curr) {
            case GroupStatus.Undercut ignored -> cfg.notifyUndercut;
            case GroupStatus.Matched ignored -> cfg.notifyMatched;
            case GroupStatus.SelfMatched ignored -> cfg.notifyMatched;
        };

        if (!shouldNotify) {
            return;
        }

        GroupStatus prev = this.getPreviousGroupStatus(key, orders, updates);
        Notifier.notifyGroupOrderStatus(key, orders, curr, prev, this.bazaarData);
    }

    private @Nullable GroupStatus getCurrentGroupStatus(GroupKey key, List<TrackedOrder> orders, Map<String,Product> products) {
        boolean hasMatched = orders.stream().anyMatch(order -> order.status instanceof OrderStatus.Matched);
        boolean hasUndercut = orders.stream().anyMatch(order -> order.status instanceof OrderStatus.Undercut);
        boolean hasUnknown = orders.stream().anyMatch(order -> order.status instanceof OrderStatus.Unknown);

        if(hasUnknown) {
            log.warn("Group ({}) has Unknown-status order after poll. Likely unresolved product name, skipping group notification", key);
            return null;
        }

        if(hasMatched && hasUndercut) {
            log.warn("Group ({}) has both Matched and Undercut orders. This must be a logic error, skipping group notification", key);
            return null;
        }

        // either matched or undercut
        if(hasUndercut) {
            // per definition all the orders within the `orders` list must have undercut status
            // as well as the same (product name, type, price per unit)
            var representativeOrder = orders.getFirst();
            var undercutAmount = ((OrderStatus.Undercut) representativeOrder.status).amount;
            return new GroupStatus.Undercut(undercutAmount);
        }

        int bucketOrderCount = this.countOrdersAtBestPrice(key, this.bazaarData);
        if(bucketOrderCount == -1) {
            log.warn("Group ({}) has no orders at the given price per unit, skipping group notification", key);
            return null;
        }

        return orders.size() == bucketOrderCount ? new GroupStatus.SelfMatched() : new GroupStatus.Matched();
    }

   // Reconstruct the previous group status from two sources:
   // - Orders that did NOT change this poll are absent from `updates`, meaning their current
   //   status IS their previous status (nothing moved for them).
   // - Orders that DID change this poll have an explicit `prev` field in their `StatusUpdate`,
   //   which overrides the default.
   // Building a full `prevStatuses` map across all orders gives us a complete picture of where
   // the entire group stood before this poll, which we then reduce to a single `GroupStatus`
   // using the same precedence as `getCurrentGroupStatus`: `Undercut` > `Matched` > everything else.
    private @Nullable GroupStatus getPreviousGroupStatus(GroupKey key, List<TrackedOrder> orders, List<StatusUpdate> updates) {
        Map<TrackedOrder, OrderStatus> prevStatuses = orders.stream()
            .collect(Collectors.toMap(order -> order, order -> order.status));

        updates.forEach(update -> prevStatuses.put(update.order(), update.prev()));

        var values = prevStatuses.values();

        if (values.stream().anyMatch(status -> status instanceof OrderStatus.Undercut)) {
            // `amount` MUST be identical across all orders in the group
            double amount = values.stream()
                .filter(status -> status instanceof OrderStatus.Undercut)
                .map(status -> ((OrderStatus.Undercut) status).amount)
                .findFirst()
                .orElseThrow();
            return new GroupStatus.Undercut(amount);
        }

        if (values.stream().anyMatch(status -> status instanceof OrderStatus.Matched)) {
            return new GroupStatus.Matched();
        }

        // Top, Unknown, or mix of both -> group had no settled matched state before
        log.debug("Group ({}) had no prior matched group state. currently tracked orders: {} | updates: {}", 
            key, orders, updates);
        return null;
    }

    private int countOrdersAtBestPrice(GroupKey key, BazaarData bazaarData) {
        var productId = bazaarData.nameToId(key.productName).orElse(null);
        if (productId == null) {
            log.warn("Group ({}) has no name -> id mapping, skipping group notification", key);
            return -1;
        }
        
        var lists = bazaarData.getOrderLists(productId);
        
        var relevantSummaries = switch (key.type) {
            case Buy -> lists.buyOrders();
            case Sell -> lists.sellOffers();
        };
    
        return relevantSummaries.stream()
            .filter(summary -> summary.getPricePerUnit() == key.pricePerUnit)
            .findFirst()
            .map(summary -> (int) summary.getOrders())
            .orElse(-1);
    }

    public Stream<StatusUpdate> computeStatusUpdates(Map<String, Product> products) {
        return this.trackedOrders
            .stream()
            .map(order -> this.getTrackedStatus(order, products))
            .flatMap(Optional::stream)
            .filter(trackedStatus -> !trackedStatus.order().status.sameVariant(trackedStatus.status()))
            .map(trackedStatus -> new StatusUpdate(
                trackedStatus.order(),
                trackedStatus.status(),
                trackedStatus.order().status
            ));
    }

    private Optional<TrackedStatus> getTrackedStatus(TrackedOrder order, Map<String, Product> products) {
        var id = bazaarData.nameToId(order.productName);
        if (id.isEmpty()) {
            log.warn(
                "No name -> id mapping found for product with name: '{}'",
                order.productName
            );
            return Optional.empty();
        }

        var product = Optional.ofNullable(products.get(id.get()));
        if (product.isEmpty()) {
            log.warn(
                "No product found for item with name '{}' and mapped id '{}'",
                order.productName,
                id.get()
            );
            return Optional.empty();
        }

        var status = this.getStatus(order, product.get());
        if (status.isEmpty()) {
            log.debug(
                "Unable to determine curr for product '{}' with id '{}'",
                order.productName,
                id.get()
            );
            return Optional.empty();
        }

        return Optional.of(new TrackedStatus(order, status.get()));
    }


    private boolean shouldNotify(StatusUpdate update) {
        var cfg = ConfigManager.get().trackedOrders;

        return cfg.enabled && switch (update.curr) {
            case OrderStatus.Top ignored -> {
                if (!cfg.notifyBest) {
                    yield false;
                }

                if (cfg.onlyOnPriorityRegain) {
                    yield !(update.prev instanceof OrderStatus.Unknown);
                }

                yield true;
            }
            case OrderStatus.Matched ignored -> cfg.notifyMatched;
            case OrderStatus.Undercut ignored -> cfg.notifyUndercut;
            case OrderStatus.Unknown ignored -> false;
        };
    }

    public void resetTrackedOrders() {
        var removedSize = this.trackedOrders.size();
        this.trackedOrders.clear();

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
            .filter(order -> order.productName.equals(info.productName()) && order.type == info.type() && order.volume == info.volume())
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

    private Optional<OrderStatus> getStatus(TrackedOrder order, Product product) {
        // floating point inaccuracy for player exposure is handled see
        // `GeneralUtils.formatDecimal`
        return switch (order.type) {
            case Buy -> Utils.getFirst(product.getSellSummary()).map(summary -> {
                double bestPrice = summary.getPricePerUnit();
                if (order.pricePerUnit == bestPrice) {
                    return summary.getOrders() > 1 ? new Matched() : new Top();
                }
                if (order.pricePerUnit > bestPrice) {
                    return new Top();
                }
                return new Undercut(bestPrice - order.pricePerUnit);
            });
            case Sell -> Utils.getFirst(product.getBuySummary()).map(summary -> {
                double bestPrice = summary.getPricePerUnit();
                if (order.pricePerUnit == bestPrice) {
                    return summary.getOrders() > 1 ? new Matched() : new Top();
                }
                if (order.pricePerUnit < bestPrice) {
                    return new Top();
                }
                return new Undercut(order.pricePerUnit - bestPrice);
            });
        };
    }

    private record TrackedStatus(TrackedOrder order, OrderStatus status) { }

    public record StatusUpdate(TrackedOrder order, OrderStatus curr, OrderStatus prev) { }

    public static class OrderManagerConfig {

        public boolean enabled = true;

        public boolean notifyBest = true;
        public boolean onlyOnPriorityRegain = true;
        public boolean soundBest = false;
        public boolean notifyMatched = true;
        public boolean soundMatched = true;
        public boolean notifyUndercut = true;
        public boolean soundUndercut = true;

        public Action gotoOnMatched = Action.Order;
        public Action gotoOnUndercut = Action.Order;

        public boolean showQueueInfo = true;
        public QueueDisplayMode queueDisplayMode = QueueDisplayMode.Both;

        public boolean groupOrders = true;
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
                    this.createSoundUndercutOption()
                )
                .addSubgroups(notifyBestGroup, queueGroup);

            return OptionGroup
                .createBuilder()
                .name(Component.literal("Order Notification"))
                .description(OptionDescription.of(Component.literal(
                    "Tracked order notification settings")))
                .options(rootGroup.build())
                .collapsed(false)
                .build();
        }

        private Option.Builder<Action> createGotoMatchedOption() {
            return Option
                .<Action>createBuilder()
                .name(Component.literal("Go To - Matched"))
                .description(OptionDescription.of(Component.literal(
                    "Where to jump shortcut to when one of your tracked orders becomes matched")))
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
                .name(Component.literal("Go To - Undercut"))
                .description(OptionDescription.of(Component.literal(
                    "Where to jump shortcut to when one of your tracked orders is undercut")))
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
                    "Display how many orders/items are ahead of your matched or undercut order")))
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
                    "Whether to display the number of orders and items, or just the number of items")))
                .controller(QueueDisplayMode::controller);
        }

        private Option.Builder<Boolean> createNotifyBestOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Notify - Best"))
                .binding(true, () -> this.notifyBest, val -> this.notifyBest = val)
                .description(OptionDescription.of(Component.literal(
                    "Send a notification when a tracked order becomes the best/top order in the Bazaar")))
                .controller(ConfigScreen::createBooleanController);
        }

        private Option.Builder<Boolean> createNotifyBestOnPriorityRegain() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.nullToEmpty("Only On Priority Regain"))
                .binding(
                    true,
                    () -> this.onlyOnPriorityRegain,
                    val -> this.onlyOnPriorityRegain = val
                )
                .description(OptionDescription.of(Component.nullToEmpty(
                    "Only sends a notification when a tracked order regains it best/top curr")))
                .controller(ConfigScreen::createBooleanController);
        }

        private Option.Builder<Boolean> createNotifyMatchedOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Notify - Matched"))
                .binding(true, () -> this.notifyMatched, val -> this.notifyMatched = val)
                .description(OptionDescription.of(Component.literal(
                    "Send a notification when a tracked order is matched (multiple orders at the same best price)")))
                .controller(ConfigScreen::createBooleanController);
        }

        private Option.Builder<Boolean> createNotifyUndercutOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Notify - Undercut"))
                .binding(true, () -> this.notifyUndercut, val -> this.notifyUndercut = val)
                .description(OptionDescription.of(Component.literal(
                    "Send a notification when a tracked order is undercut / outbid by another order")))
                .controller(ConfigScreen::createBooleanController);
        }

        private Option.Builder<Boolean> createSoundBestOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Sound - Best"))
                .binding(false, () -> this.soundBest, val -> this.soundBest = val)
                .description(OptionDescription.of(Component.literal(
                    "Play a sound when a best/top order notification is triggered")))
                .controller(ConfigScreen::createBooleanController);
        }

        private Option.Builder<Boolean> createSoundMatchedOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Sound - Matched"))
                .binding(true, () -> this.soundMatched, val -> this.soundMatched = val)
                .description(OptionDescription.of(Component.literal(
                    "Play a sound when a matched notification is triggered")))
                .controller(ConfigScreen::createBooleanController);
        }

        private Option.Builder<Boolean> createSoundUndercutOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Sound - Undercut"))
                .binding(true, () -> this.soundUndercut, val -> this.soundUndercut = val)
                .description(OptionDescription.of(Component.literal(
                    "Play a sound when an undercut notification is triggered")))
                .controller(ConfigScreen::createBooleanController);
        }

        private Option.Builder<Boolean> createGroupOrdersOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Group Orders"))
                .binding(true, () -> this.groupOrders, val -> this.groupOrders = val)
                .description(OptionDescription.of(Component.literal(
                    "Group multiple orders at the same price into a single notification")))
                .controller(ConfigScreen::createBooleanController);
        }

        private Option.Builder<Boolean> createIncludePricePerUnitOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Include Price Per Unit"))
                .binding(false, () -> this.includePricePerUnit, val -> this.includePricePerUnit = val)
                .description(OptionDescription.of(Component.literal(
                    "Include the price per unit in the notification message")))
                .controller(ConfigScreen::createBooleanController);
        }

        private Option.Builder<Boolean> createEnabledOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Tracked Orders"))
                .binding(true, () -> this.enabled, val -> this.enabled = val)
                .description(OptionDescription.of(Component.literal(
                    "Enable or disable the notifications when the curr of an order changes")))
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
