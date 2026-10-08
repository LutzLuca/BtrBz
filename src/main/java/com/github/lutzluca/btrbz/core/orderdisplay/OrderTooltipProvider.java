package com.github.lutzluca.btrbz.core.orderdisplay;

import com.github.lutzluca.btrbz.core.ui.UiStyles;
import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.config.ConfigStore;
import com.github.lutzluca.btrbz.cache.CacheToken;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus;
import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrder;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.mixin.AbstractContainerScreenAccessor;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.screen.ScreenTracker;
import com.github.lutzluca.btrbz.screen.ScreenTracker.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.Utils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Slf4j
public class OrderTooltipProvider {

    private final BazaarData bazaarData;
    private final OrderHighlightManager highlightManager;
    private final OrderTooltipCache listCache;
    private final OrderTooltipCache itemCache;
    @Getter
    @Accessors(fluent = true)
    private final CacheToken listSettingsChanges = CacheToken.named("config.order-list-tooltip");

    private static class OrderTooltipCache {
        private final Map<@NotNull TrackedOrder, @Nullable List<Component>> cache = new HashMap<>();
        private final String name;

        OrderTooltipCache(String name) {
            this.name = name;
        }

        public List<Component> getOrCompute(@NotNull TrackedOrder order, Supplier<List<Component>> supplier) {
            return this.cache.computeIfAbsent(order, key -> {
                log.trace("Computing {} tooltip cache for {}", this.name, key);
                return supplier.get();
            });
        }

        public void clear() {
            if (!this.cache.isEmpty()) {
                log.trace("Clearing {} tooltip cache with {} entries", this.name, this.cache.size());
            }
            this.cache.clear();
        }
    }

    public OrderTooltipProvider(BazaarData bazaarData, OrderHighlightManager highlightManager) {
        this.bazaarData = Objects.requireNonNull(bazaarData, "bazaarData cannot be null");
        this.highlightManager = Objects.requireNonNull(highlightManager, "highlightManager cannot be null");
        this.listCache = new OrderTooltipCache("list");
        this.itemCache = new OrderTooltipCache("item");

        this.bazaarData.addListener(snapshot -> {
            this.listCache.clear();
            this.itemCache.clear();
        });

        ItemTooltipCallback.EVENT.register((stack, ctx, type, lines) -> {
            if (!BtrBz.isActive()) {
                return;
            }
            var cfg = ConfigStore.get().config().orderItemTooltip;
            if (!cfg.enabled) {
                return;
            }

            if (!ScreenTracker.inMenu(BazaarMenuType.Orders) || !GameUtils.orderScreenNonOrderItemsFilter(stack)) {
                return;
            }

            var screen = ScreenTracker.get().getCurrInfo().getGenericContainerScreen().orElse(null);
            if (screen == null) {
                return;
            }

            var slot = ((AbstractContainerScreenAccessor) screen).getHoveredSlot();
            if (slot == null || GameUtils.isPlayerInventorySlot(slot) || slot.getItem() != stack) {
                return;
            }

            int idx = slot.getContainerSlot();
            var order = this.highlightManager.getTrackedOrder(idx);
            if (order == null) {
                return;
            }

            var tooltipLines = this.getCachedTooltip(order, cfg);
            lines.addAll(1, tooltipLines);
        });
    }

    public List<Component> getCachedTooltip(TrackedOrder order, OrderListTooltipConfig cfg) {
        return this.listCache.getOrCompute(order, () -> this.buildTooltipLines(order, cfg));
    }

    public List<Component> getCachedTooltip(TrackedOrder order, OrderItemTooltipConfig cfg) {
        return this.itemCache.getOrCompute(order, () -> this.buildTooltipLines(order, cfg));
    }

    public void clearCache() {
        this.listCache.clear();
        this.itemCache.clear();
    }

    public void onListSettingsChanged(String reason) {
        this.listCache.clear();
        this.listSettingsChanges.invalidate(reason);
    }

    public void onItemSettingsChanged() {
        this.itemCache.clear();
    }

    public void onQueueDisplayModeChanged() {
        this.itemCache.clear();
        this.onListSettingsChanged("order queue display mode changed");
    }

    public List<Component> buildTooltipLines(TrackedOrder order, OrderListTooltipConfig cfg) {
        var product = order.product;

        if (product.bazaarProductId().isEmpty()) {
            return List.of(Component.literal("Unknown Product: " + order.productName).withStyle(ChatFormatting.RED));
        }

        List<Component> lines = new ArrayList<>();

        if (cfg.showStatus) {
            lines.add(OrderTooltipProvider.statusLine(order));
            if (order.status instanceof OrderStatus.Undercut undercut) {
                lines.add(OrderTooltipProvider.undercutAmountLine(undercut.amount));
            }
        }

        if (cfg.showQueue && order.status instanceof OrderStatus.Undercut) {
            var queueInfo = this.bazaarData.calculateQueuePosition(
                product,
                order.type,
                order.pricePerUnit);

            queueInfo.ifPresent(orderQueueInfo -> lines.add(Component
                .literal("Queue: ")
                .withStyle(UiStyles.label())
                .append(GameUtils.buildQueueComponent(
                    orderQueueInfo.ordersAhead,
                    orderQueueInfo.itemsAhead,
                    ConfigStore.get().config().trackedOrders.queueDisplayMode))));
        }

        lines.add(Component.empty());
        lines.addAll(OrderTooltipProvider.currOrderLines(order));

        if (OrderTooltipProvider.shouldShowPrices(cfg.showPrices, cfg.showOnlyWhenUndercut, order)) {
            lines.add(Component.empty());
            lines.addAll(OrderTooltipProvider.priceLines(this.bazaarData, product));
        }

        return lines;
    }

    public List<Component> buildTooltipLines(TrackedOrder order, OrderItemTooltipConfig cfg) {
        var product = order.product;

        if (product.bazaarProductId().isEmpty()) {
            return List.of(Component.literal("Unknown Product: " + order.productName).withStyle(ChatFormatting.RED));
        }

        List<Component> lines = new ArrayList<>();

        if (cfg.showStatus) {
            lines.add(OrderTooltipProvider.statusLine(order));

            if (cfg.showEstimatedTime && order.status instanceof OrderStatus.Top) {
                int remainingVolume = order.volume - order.fillAmountSnapshot;

                this.bazaarData.getEstimatedFillTimeMinutes(product, order.type, remainingVolume).ifPresent(minutes -> {
                    var time = Component.literal(formatDuration(minutes)).withStyle(UiStyles.primary());
                    var line = Component.literal("Estimated fill time: ").withStyle(UiStyles.label()).append(time);
                    lines.add(line);
                });
            }

            if (order.status instanceof OrderStatus.Undercut undercut) {
                lines.add(OrderTooltipProvider.undercutAmountLine(undercut.amount));
            }
        }

        if (cfg.showQueue && order.status instanceof OrderStatus.Undercut) {
            var queueInfo = this.bazaarData.calculateQueuePosition(
                product,
                order.type,
                order.pricePerUnit);

            queueInfo.ifPresent(orderQueueInfo -> lines.add(Component
                .literal("Queue: ")
                .withStyle(UiStyles.label())
                .append(GameUtils.buildQueueComponent(
                    orderQueueInfo.ordersAhead,
                    orderQueueInfo.itemsAhead,
                    ConfigStore.get().config().trackedOrders.queueDisplayMode))));
        }

        if (shouldShowPrices(cfg.showPrices, cfg.showOnlyWhenUndercut, order)) {
            lines.add(Component.empty());
            lines.addAll(priceLines(this.bazaarData, product));
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
        var header = Component.literal("Your Order").withStyle(UiStyles.heading());

        var priceLine = Component
            .literal("Price: ")
            .withStyle(UiStyles.label())
            .append(Component
                .literal(Utils.formatDecimal(order.pricePerUnit, 1, true))
                .withStyle(UiStyles.money()));

        var volumeLine = Component
            .literal("Volume: ")
            .withStyle(UiStyles.label())
            .append(Component.literal(String.valueOf(order.volume)).withStyle(UiStyles.quantity()));

        return List.of(header, priceLine, volumeLine);
    }

    private static Component statusLine(TrackedOrder order) {
        return switch (order.status) {
            case OrderStatus.Top _ -> Component.literal("Best Price!")
                .withStyle(UiStyles.color(UiStyles.palette().success()).withBold(true));
            case OrderStatus.Matched _ -> Component.literal("Matched!")
                .withStyle(UiStyles.color(UiStyles.palette().matched()).withBold(true));
            case OrderStatus.Undercut _ -> Component.literal("Undercut!")
                .withStyle(UiStyles.color(UiStyles.palette().error()).withBold(true));
            case OrderStatus.Unknown _ -> Component.literal("Status Unknown")
                .withStyle(UiStyles.label());
        };
    }

    private static Component undercutAmountLine(double amount) {
        return Component
            .literal("By: ")
            .withStyle(UiStyles.label())
            .append(Component
                .literal(Utils.formatDecimal(Math.abs(amount), 1, true))
                .withStyle(UiStyles.money()));
    }

    private static List<Component> priceLines(BazaarData data, ProductIdentity product) {
        var priceInfo = data.getMarketPrices(product);

        var header = Component.literal("Current Prices").withStyle(UiStyles.heading());

        var buyOrderLine = Component
            .literal("Buy Orders: ")
            .withStyle(UiStyles.label())
            .append(priceInfo
                .highestBuyOrderPrice()
                .map(price -> Component
                    .literal(Utils.formatDecimal(price, 1, true))
                    .withStyle(UiStyles.money()))
                .orElse(Component.literal("N/A").withStyle(UiStyles.muted())));

        var sellOfferLine = Component
            .literal("Sell Offers: ")
            .withStyle(UiStyles.label())
            .append(priceInfo
                .lowestSellOfferPrice()
                .map(price -> Component
                    .literal(Utils.formatDecimal(price, 1, true))
                    .withStyle(UiStyles.money()))
                .orElse(Component.literal("N/A").withStyle(UiStyles.muted())));

        return List.of(header, buyOrderLine, sellOfferLine);
    }

    static String formatDuration(double totalMinutes) {
        if (totalMinutes < 1) {
            return "< 1m";
        }

        long hours = (long) (totalMinutes / 60);
        long minutes = (long) (totalMinutes % 60);

        if (hours > 0) {
            if (minutes > 0) {
                return String.format("%dh %dm", hours, minutes);
            }

            return String.format("%dh", hours);
        }

        return String.format("%dm", minutes);
    }

}
