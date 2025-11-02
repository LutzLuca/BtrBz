package com.github.lutzluca.btrbz.core;

import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.core.modules.TrackedOrdersListModule;
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
import com.github.lutzluca.btrbz.utils.Util;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionEventListener.Event;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply.Product;
import net.minecraft.text.Text;

@Slf4j
public class TrackedOrderManager {

    private final BazaarData bazaarData;

    private final List<TrackedOrder> trackedOrders = new ArrayList<>();
    private final TimedStore<OutstandingOrderInfo> outstandingOrderStore;

    private final List<Consumer<TrackedOrder>> onOrderAddedListeners = new ArrayList<>();
    private final List<Consumer<TrackedOrder>> onOrderRemovedListeners = new ArrayList<>();
    private final List<Consumer<StatusUpdate>> onOrderStatusUpdate = new ArrayList<>();
    private BiConsumer<List<UnfilledOrderInfo>, List<FilledOrderInfo>> onSyncCompletedCallback = null;

    public TrackedOrderManager(BazaarData bazaarData) {
        this.bazaarData = bazaarData;
        this.outstandingOrderStore = new TimedStore<>(15_000L);
    }

    public void addOnOrderAddedListener(Consumer<TrackedOrder> listener) {
        this.onOrderAddedListeners.add(listener);
    }

    public void addOnOrderRemovedListener(Consumer<TrackedOrder> listener) {
        this.onOrderRemovedListeners.add(listener);
    }

    public void afterOrderSync(BiConsumer<List<UnfilledOrderInfo>, List<FilledOrderInfo>> cb) {
        this.onSyncCompletedCallback = cb;
    }

    public void addOnOrderStatusUpdate(Consumer<StatusUpdate> listener) {
        this.onOrderStatusUpdate.add(listener);
    }

    public void syncOrders(List<OrderInfo> parsedOrders) {
        var toRemove = new ArrayList<TrackedOrder>();
        var remaining = new ArrayList<>(parsedOrders);

        var filledOrders = new ArrayList<OrderInfo.FilledOrderInfo>();
        var unfilledOrders = new ArrayList<OrderInfo.UnfilledOrderInfo>();
        for (var order : remaining) {
            switch (order) {
                case OrderInfo.FilledOrderInfo filled -> filledOrders.add(filled);
                case OrderInfo.UnfilledOrderInfo unfilled -> unfilledOrders.add(unfilled);
            }
        }

        for (var tracked : this.trackedOrders) {
            var match = unfilledOrders.stream().filter(tracked::matches).findFirst();

            match.ifPresentOrElse(
                info -> {
                    unfilledOrders.remove(info);
                    tracked.slot = info.slotIdx();
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
        unfilledOrders.stream().map(TrackedOrder::new).forEach(this::addTrackedOrder);

        this.onSyncCompletedCallback.accept(unfilledOrders, filledOrders);
    }

    private void removeTrackedOrder(TrackedOrder order) {
        if (this.trackedOrders.remove(order)) {
            this.onOrderRemovedListeners.forEach(listener -> listener.accept(order));
        }
    }

    public void onBazaarUpdate(Map<String, Product> products) {
        this.trackedOrders
            .stream()
            .map(tracked -> {
                var id = bazaarData.nameToId(tracked.productName);
                if (id.isEmpty()) {
                    log.warn(
                        "No name -> id mapping found for product with name: '{}'",
                        tracked.productName
                    );
                    return Optional.<StatusUpdate>empty();
                }

                var product = Optional.ofNullable(products.get(id.get()));
                if (product.isEmpty()) {
                    log.warn(
                        "No product found for item with name '{}' and mapped id '{}'",
                        tracked.productName,
                        id.get()
                    );
                    return Optional.<StatusUpdate>empty();
                }

                var status = getStatus(tracked, product.get());
                if (status.isEmpty()) {
                    log.debug(
                        "Unable to determine status for product '{}' with id '{}'",
                        tracked.productName,
                        id.get()
                    );
                    return Optional.<StatusUpdate>empty();
                }

                return Optional.of(new StatusUpdate(tracked, status.get()));
            })
            .flatMap(Optional::stream)
            .filter(statusUpdate -> !statusUpdate.trackedOrder.status.sameVariant(statusUpdate.status))
            .forEach(statusUpdate -> {
                statusUpdate.trackedOrder.status = statusUpdate.status;
                this.onOrderStatusUpdate.forEach(listener -> listener.accept(statusUpdate));

                if (this.shouldNotify(statusUpdate)) {
                    Notifier.notifyOrderStatus(statusUpdate);
                }
            });
    }

    private boolean shouldNotify(StatusUpdate statusUpdate) {
        var cfg = ConfigManager.get().trackedOrders;

        return cfg.enabled && switch (statusUpdate.status) {
            case Top ignored -> cfg.notifyBest;
            case Matched ignored -> cfg.notifyMatched;
            case Undercut ignored -> cfg.notifyUndercut;
            default -> false;
        };
    }

    public void resetTrackedOrders() {
        var removed = this.trackedOrders.size();
        this.trackedOrders.clear();
        log.info("Reset tracked orders (removed {})", removed);
        ModuleManager.getInstance().getModule(TrackedOrdersListModule.class).clearList();
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
                            Util.formatDecimal(info.total(), 1, true)
                        ), "managebazaarorders"
                    );
                }
            );
    }

    private Optional<OrderStatus> getStatus(TrackedOrder order, Product product) {
        // floating point inaccuracy for player exposure is handled see
        // `GeneralUtils.formatDecimal`
        return switch (order.type) {
            case Buy -> Util.getFirst(product.getSellSummary()).map(summary -> {
                double bestPrice = summary.getPricePerUnit();
                if (order.pricePerUnit == bestPrice) {
                    return summary.getOrders() > 1 ? new Matched() : new Top();
                }
                if (order.pricePerUnit > bestPrice) {
                    return new Top();
                }
                return new Undercut(bestPrice - order.pricePerUnit);
            });
            case Sell -> Util.getFirst(product.getBuySummary()).map(summary -> {
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

    public record StatusUpdate(TrackedOrder trackedOrder, OrderStatus status) { }

    public static class OrderManagerConfig {

        public boolean enabled = true;

        public boolean notifyBest = true;
        public boolean notifyMatched = true;
        public boolean notifyUndercut = true;

        public Action gotoOnMatched = Action.Item;
        public Action gotoOnUndercut = Action.Item;

        public OptionGroup createGroup() {
            var options = List.of(
                this.createNotifyBestOption().build(),
                this.createNotifyMatchedOption().build(),
                this.createNotifyUndercutOption().build(),
                this.createGotoMatchedOption().build(),
                this.createGotoUndercutOption().build()
            );

            var enabledOption = this.createEnabledOption();
            enabledOption.addListener((option, event) -> {
                if (event == Event.STATE_CHANGE) {
                    boolean val = option.pendingValue();
                    options.forEach(opt -> opt.setAvailable(val));
                }
            });

            return OptionGroup
                .createBuilder()
                .name(Text.literal("Order Notification"))
                .description(OptionDescription.of(Text.literal("Tracked order notification settings")))
                .option(enabledOption.build())
                .options(options)
                .collapsed(false)
                .build();
        }

        private Option.Builder<Action> createGotoMatchedOption() {
            return Option
                .<Action>createBuilder()
                .name(Text.literal("Go To - Matched"))
                .description(OptionDescription.of(Text.literal(
                    "Where to jump shortcut to when one of your tracked orders becomes matched")))
                .binding(
                    this.gotoOnMatched,
                    () -> this.gotoOnMatched,
                    action -> this.gotoOnMatched = action
                )
                .controller(Action::controller)
                .available(this.enabled);
        }

        private Option.Builder<Action> createGotoUndercutOption() {
            return Option
                .<Action>createBuilder()
                .name(Text.literal("Go To - Undercut"))
                .description(OptionDescription.of(Text.literal(
                    "Where to jump shortcut to when one of your tracked orders is undercut")))
                .binding(
                    this.gotoOnUndercut,
                    () -> this.gotoOnUndercut,
                    action -> this.gotoOnUndercut = action
                )
                .controller(Action::controller)
                .available(this.enabled);
        }

        private Option.Builder<Boolean> createNotifyBestOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Text.literal("Notify - Best"))
                .binding(true, () -> this.notifyBest, val -> this.notifyBest = val)
                .description(OptionDescription.of(Text.literal(
                    "Send a notification when a tracked order becomes the best/top order in the Bazaar")))
                .controller(ConfigScreen::createBooleanController)
                .available(this.enabled);
        }

        private Option.Builder<Boolean> createNotifyMatchedOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Text.literal("Notify - Matched"))
                .binding(true, () -> this.notifyMatched, val -> this.notifyMatched = val)
                .description(OptionDescription.of(Text.literal(
                    "Send a notification when a tracked order is matched (multiple orders at the same best price)")))
                .controller(ConfigScreen::createBooleanController)
                .available(this.enabled);
        }

        private Option.Builder<Boolean> createNotifyUndercutOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Text.literal("Notify - Undercut"))
                .binding(true, () -> this.notifyUndercut, val -> this.notifyUndercut = val)
                .description(OptionDescription.of(Text.literal(
                    "Send a notification when a tracked order is undercut / outbid by another order")))
                .controller(ConfigScreen::createBooleanController)
                .available(this.enabled);
        }

        private Option.Builder<Boolean> createEnabledOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Text.literal("Tracked Orders"))
                .binding(true, () -> this.enabled, val -> this.enabled = val)
                .description(OptionDescription.of(Text.literal(
                    "Enable or disable the notifications when the status of an order changes")))
                .controller(ConfigScreen::createBooleanController);
        }

        public enum Action {
            None,
            Item,
            Order;

            public static EnumControllerBuilder<Action> controller(
                Option<Action> option
            ) {
                return EnumControllerBuilder
                    .create(option)
                    .enumClass(Action.class)
                    .formatValue(action -> switch (action) {
                        case None -> Text.literal("No action");
                        case Item -> Text.literal("Go to Item in Bazaar");
                        case Order -> Text.literal("Open Manage Bazaar Orders");
                    });
            }
        }
    }
}
