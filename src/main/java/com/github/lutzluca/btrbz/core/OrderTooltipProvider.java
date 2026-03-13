package com.github.lutzluca.btrbz.core;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.core.config.ConfigScreen.OptionGrouping;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus;
import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrder;
import com.github.lutzluca.btrbz.mixin.AbstractContainerScreenAccessor;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.Utils;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Slf4j
public class OrderTooltipProvider {

    private final OrderTooltipCache listCache;
    private final OrderTooltipCache itemCache;

    private static class OrderTooltipCache {
        private final Map<@NotNull TrackedOrder, @Nullable List<Component>> cache = new HashMap<>();
        private final String name;

        public OrderTooltipCache(String name) {
            this.name = name;
            log.info("Initializing OrderTooltipCache for {}", name);
        }

        public List<Component> getOrCompute(@NotNull TrackedOrder order, Supplier<List<Component>> supplier) {
            return this.cache.computeIfAbsent(order, key -> {
                log.trace("Computing {} tooltip cache for {}", this.name, key);
                return supplier.get();
            });
        }

        public void clear() {
            log.trace("Clearing {} tooltip cache with {} entries", this.name, this.cache.size());
            this.cache.clear();
        }
    }

    public OrderTooltipProvider() {
        this.listCache = new OrderTooltipCache("list");
        this.itemCache = new OrderTooltipCache("item");

        BtrBz.bazaarData().addListener(products -> {
            this.listCache.clear();
            this.itemCache.clear();
        });

        ItemTooltipCallback.EVENT.register((stack, ctx, type, lines) -> {
            var cfg = ConfigManager.get().orderItemTooltip;
            if (!cfg.enabled) {
                return;
            }

            if (!ScreenInfoHelper.inMenu(BazaarMenuType.Orders) || !GameUtils.orderScreenNonOrderItemsFilter(stack)) {
                return;
            }

            var screen = ScreenInfoHelper.get().getCurrInfo().getGenericContainerScreen().orElse(null);
            if (screen == null) {
                return;
            }

            var slot = ((AbstractContainerScreenAccessor) screen).getHoveredSlot();
            if (slot == null || GameUtils.isPlayerInventorySlot(slot) || slot.getItem() != stack) {
                return;
            }

            int idx = slot.getContainerSlot();
            var order = BtrBz.highlightManager().getTrackedOrder(idx);
            if (order == null) {
                return;
            }

            var tooltipLines = getCachedTooltip(order, cfg);
            lines.addAll(1, tooltipLines);
        });
    }

    public List<Component> getCachedTooltip(TrackedOrder order, OrderListTooltipConfig cfg) {
        return this.listCache.getOrCompute(order, () -> buildTooltipLines(order, cfg));
    }

    public List<Component> getCachedTooltip(TrackedOrder order, OrderItemTooltipConfig cfg) {
        return this.itemCache.getOrCompute(order, () -> buildTooltipLines(order, cfg));
    }

    public void clearCache() {
        this.listCache.clear();
        this.itemCache.clear();
    }

    public static List<Component> buildTooltipLines(TrackedOrder order, OrderListTooltipConfig cfg) {
        var data = BtrBz.bazaarData();
        var productId = data.nameToId(order.productName);

        if (productId.isEmpty()) {
            return List.of(Component.literal("Unknown Product").withStyle(ChatFormatting.RED));
        }

        List<Component> lines = new ArrayList<>();

        if (cfg.showStatus) {
            lines.add(statusLine(order));
            if (order.status instanceof OrderStatus.Undercut undercut) {
                lines.add(undercutAmountLine(undercut.amount));
            }
        }

        if (cfg.showQueue && order.status instanceof OrderStatus.Undercut) {
            var queueInfo = data.calculateQueuePosition(
                order.productName,
                order.type,
                order.pricePerUnit
            );

            queueInfo.ifPresent(orderQueueInfo -> lines.add(Component
                    .literal("Queue: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(GameUtils.buildQueueComponent(orderQueueInfo.ordersAhead, orderQueueInfo.itemsAhead))));
        }

        lines.add(Component.empty());
        lines.addAll(currOrderLines(order));

        if (shouldShowPrices(cfg.showPrices, cfg.showOnlyWhenUndercut, order)) {
            lines.add(Component.empty());
            lines.addAll(priceLines(data, productId.get()));
        }

        return lines;
    }

    public static List<Component> buildTooltipLines(TrackedOrder order, OrderItemTooltipConfig cfg) {
        var data = BtrBz.bazaarData();
        var productId = data.nameToId(order.productName);

        if (productId.isEmpty()) {
            return List.of(Component.literal("Unknown Product").withStyle(ChatFormatting.RED));
        }

        List<Component> lines = new ArrayList<>();

        if (cfg.showStatus) {
            lines.add(statusLine(order));
            if (order.status instanceof OrderStatus.Undercut undercut) {
                lines.add(undercutAmountLine(undercut.amount));
            }
        }

        if (cfg.showQueue && order.status instanceof OrderStatus.Undercut) {
            var queueInfo = data.calculateQueuePosition(
                order.productName,
                order.type,
                order.pricePerUnit
            );

            queueInfo.ifPresent(orderQueueInfo -> lines.add(Component
                    .literal("Queue: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(GameUtils.buildQueueComponent(orderQueueInfo.ordersAhead, orderQueueInfo.itemsAhead))));
        }

        if (shouldShowPrices(cfg.showPrices, cfg.showOnlyWhenUndercut, order)) {
            lines.add(Component.empty());
            lines.addAll(priceLines(data, productId.get()));
        }

        return lines;
    }

    private static boolean shouldShowPrices(boolean showPrices, boolean showOnlyWhenUndercut, TrackedOrder order) {
        if (!showPrices) {
            return false;
        }
        if (!showOnlyWhenUndercut) {
            return true;
        }
        return order.status instanceof OrderStatus.Undercut;
    }

    private static List<Component> currOrderLines(TrackedOrder order) {
        var header = Component.literal("Your Order").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);

        var priceLine = Component
            .literal("Price: ")
            .withStyle(ChatFormatting.GRAY)
            .append(Component
                .literal(Utils.formatDecimal(order.pricePerUnit, 1, true))
                .withStyle(ChatFormatting.WHITE));

        var volumeLine = Component
            .literal("Volume: ")
            .withStyle(ChatFormatting.GRAY)
            .append(Component.literal(String.valueOf(order.volume)).withStyle(ChatFormatting.WHITE));

        return List.of(header, priceLine, volumeLine);
    }

    private static Component statusLine(TrackedOrder order) {
        return switch (order.status) {
            case OrderStatus.Top ignored -> Component.literal("Best Price!")
                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
            case OrderStatus.Matched ignored -> Component.literal("Matched!")
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
            case OrderStatus.Undercut ignored -> Component.literal("Undercut!")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
            case OrderStatus.Unknown ignored -> Component.literal("Status Unknown")
                    .withStyle(ChatFormatting.GRAY);
        };
    }

    private static Component undercutAmountLine(double amount) {
        return Component
            .literal("By: ")
            .withStyle(ChatFormatting.GRAY)
            .append(Component
                .literal(Utils.formatDecimal(Math.abs(amount), 1, true))
                .withStyle(ChatFormatting.GOLD));
    }

    private static List<Component> priceLines(BazaarData data, String productId) {
        var priceInfo = data.getOrderPrices(productId);

        var header = Component.literal("Current Prices").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);

        var buyOrderLine = Component
            .literal("Buy Orders: ")
            .withStyle(ChatFormatting.YELLOW)
            .append(priceInfo
                .buyOrderPrice()
                .map(price -> Component
                    .literal(Utils.formatDecimal(price, 1, true))
                    .withStyle(ChatFormatting.WHITE))
                .orElse(Component.literal("N/A").withStyle(ChatFormatting.DARK_GRAY)));

        var sellOfferLine = Component
            .literal("Sell Offers: ")
            .withStyle(ChatFormatting.YELLOW)
            .append(priceInfo
                .sellOfferPrice()
                .map(price -> Component
                    .literal(Utils.formatDecimal(price, 1, true))
                    .withStyle(ChatFormatting.WHITE))
                .orElse(Component.literal("N/A").withStyle(ChatFormatting.DARK_GRAY)));

        return List.of(header, buyOrderLine, sellOfferLine);
    }

    public static class OrderListTooltipConfig {
        public boolean enabled = true;
        public boolean showStatus = true;
        public boolean showQueue = true;
        public boolean showPrices = true;
        public boolean showOnlyWhenUndercut = false;

        private static void invalidateCache() {
            BtrBz.tooltipProvider().clearCache();
        }

        public Option.Builder<Boolean> createEnabledOption() {
            return Option.<Boolean>createBuilder()
                .name(Component.literal("Order List Tooltips"))
                .binding(true, () -> this.enabled, val -> {
                    this.enabled = val;
                    invalidateCache();
                })
                .description(OptionDescription.of(Component.literal("Enable custom tooltips for order list entries in the sidebar.")))
                .controller(ConfigScreen::createBooleanController);
        }

        public Option.Builder<Boolean> createStatusOption() {
            return Option.<Boolean>createBuilder()
                .name(Component.literal("Show Status"))
                .binding(true, () -> this.showStatus, val -> {
                    this.showStatus = val;
                    invalidateCache();
                })
                .description(OptionDescription.of(Component.literal("Show order status (Top, Undercut, etc.)")))
                .controller(ConfigScreen::createBooleanController);
        }

        public Option.Builder<Boolean> createQueueOption() {
            return Option.<Boolean>createBuilder()
                .name(Component.literal("Show Queue Position"))
                .binding(true, () -> this.showQueue, val -> {
                    this.showQueue = val;
                    invalidateCache();
                })
                .description(OptionDescription.of(Component.literal("Show how many orders are ahead of yours.")))
                .controller(ConfigScreen::createBooleanController);
        }

        public Option.Builder<Boolean> createPricesOption() {
            return Option.<Boolean>createBuilder()
                .name(Component.literal("Show Current Prices"))
                .binding(false, () -> this.showPrices, val -> {
                    this.showPrices = val;
                    invalidateCache();
                })
                .description(OptionDescription.of(Component.literal("Show top buy and sell prices for the item.")))
                .controller(ConfigScreen::createBooleanController);
        }

        public Option.Builder<Boolean> createOnlyWhenUndercutOption() {
            return Option.<Boolean>createBuilder()
                .name(Component.literal("Only When Undercut"))
                .binding(false, () -> this.showOnlyWhenUndercut, val -> {
                    this.showOnlyWhenUndercut = val;
                    invalidateCache();
                })
                .description(OptionDescription.of(Component.literal("Only show prices when your order has been undercut.")))
                .controller(ConfigScreen::createBooleanController);
        }

        public OptionGroup createGroup() {
            var pricesGroup = new OptionGrouping(this.createPricesOption())
                .addOptions(this.createOnlyWhenUndercutOption());

            var root = new OptionGrouping(this.createEnabledOption())
                .addOptions(
                    this.createStatusOption(),
                    this.createQueueOption()
                )
                .addSubgroups(pricesGroup);

            return OptionGroup.createBuilder()
                .name(Component.literal("Order List Tooltips"))
                .description(OptionDescription.of(Component.literal("Settings for tooltips shown when hovering order entries in the tracked orders list.")))
                .options(root.build())
                .collapsed(false)
                .build();
        }
    }

    public static class OrderItemTooltipConfig {
        public boolean enabled = true;
        public boolean showStatus = true;
        public boolean showQueue = true;
        public boolean showPrices = false;
        public boolean showOnlyWhenUndercut = true;

        private static void invalidateCache() {
            BtrBz.tooltipProvider().clearCache();
        }

        public Option.Builder<Boolean> createEnabledOption() {
            return Option.<Boolean>createBuilder()
                .name(Component.literal("Order Item Tooltips"))
                .binding(true, () -> this.enabled, val -> {
                    this.enabled = val;
                    invalidateCache();
                })
                .description(OptionDescription.of(Component.literal("Enable custom tooltips for order items in the Bazaar orders menu.")))
                .controller(ConfigScreen::createBooleanController);
        }

        public Option.Builder<Boolean> createStatusOption() {
            return Option.<Boolean>createBuilder()
                .name(Component.literal("Show Status"))
                .binding(true, () -> this.showStatus, val -> {
                    this.showStatus = val;
                    invalidateCache();
                })
                .description(OptionDescription.of(Component.literal("Show order status (Top, Undercut, etc.)")))
                .controller(ConfigScreen::createBooleanController);
        }

        public Option.Builder<Boolean> createQueueOption() {
            return Option.<Boolean>createBuilder()
                .name(Component.literal("Show Queue Position"))
                .binding(true, () -> this.showQueue, val -> {
                    this.showQueue = val;
                    invalidateCache();
                })
                .description(OptionDescription.of(Component.literal("Show how many orders are ahead of yours.")))
                .controller(ConfigScreen::createBooleanController);
        }

        public Option.Builder<Boolean> createPricesOption() {
            return Option.<Boolean>createBuilder()
                .name(Component.literal("Show Current Prices"))
                .binding(false, () -> this.showPrices, val -> {
                    this.showPrices = val;
                    invalidateCache();
                })
                .description(OptionDescription.of(Component.literal("Show top buy and sell prices for the item.")))
                .controller(ConfigScreen::createBooleanController);
        }

        public Option.Builder<Boolean> createOnlyWhenUndercutOption() {
            return Option.<Boolean>createBuilder()
                .name(Component.literal("Only When Undercut"))
                .binding(false, () -> this.showOnlyWhenUndercut, val -> {
                    this.showOnlyWhenUndercut = val;
                    invalidateCache();
                })
                .description(OptionDescription.of(Component.literal("Only show prices when your order has been undercut.")))
                .controller(ConfigScreen::createBooleanController);
        }

        public OptionGroup createGroup() {
            var pricesGroup = new OptionGrouping(this.createPricesOption())
                .addOptions(this.createOnlyWhenUndercutOption());

            var root = new OptionGrouping(this.createEnabledOption())
                .addOptions(
                    this.createStatusOption(),
                    this.createQueueOption()
                )
                .addSubgroups(pricesGroup);

            return OptionGroup.createBuilder()
                .name(Component.literal("Order Item Tooltips"))
                .description(OptionDescription.of(Component.literal("Settings for tooltips shown when hovering order items in the Bazaar orders menu.")))
                .options(root.build())
                .collapsed(false)
                .build();
        }
    }
}
