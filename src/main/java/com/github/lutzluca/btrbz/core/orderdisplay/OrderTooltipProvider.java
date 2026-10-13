package com.github.lutzluca.btrbz.core.orderdisplay;

import com.github.lutzluca.btrbz.core.ui.UiStyles;
import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.config.ConfigStore;
import com.github.lutzluca.btrbz.cache.CacheToken;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus;
import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrder;
import com.github.lutzluca.btrbz.mixin.AbstractContainerScreenAccessor;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.screen.ScreenTracker;
import com.github.lutzluca.btrbz.screen.ScreenTracker.BazaarMenuType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.Getter;
import lombok.experimental.Accessors;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.network.chat.Component;

public class OrderTooltipProvider {

    private final BazaarData bazaarData;
    private final OrderHighlightManager highlightManager;
    private final Map<TrackedOrder, OrderTooltipData> listCache = new HashMap<>();
    private final Map<TrackedOrder, ItemTooltipPresentation> itemCache = new HashMap<>();
    @Getter
    @Accessors(fluent = true)
    private final CacheToken listSettingsChanges = CacheToken.named("config.order-list-tooltip");

    /** Native item tooltips reuse styled output without invalidating their computed content. */
    private static final class ItemTooltipPresentation {
        private final OrderTooltipData data;
        private long appearanceRevision = -1;
        private List<Component> lines = List.of();

        private ItemTooltipPresentation(OrderTooltipData data) {
            this.data = data;
        }

        private List<Component> lines() {
            if (this.appearanceRevision != UiStyles.appearanceRevision()) {
                this.lines = OrderTooltipFormatter.format(this.data);
                this.appearanceRevision = UiStyles.appearanceRevision();
            }
            return this.lines;
        }
    }

    public OrderTooltipProvider(BazaarData bazaarData, OrderHighlightManager highlightManager) {
        this.bazaarData = Objects.requireNonNull(bazaarData, "bazaarData cannot be null");
        this.highlightManager = Objects.requireNonNull(highlightManager, "highlightManager cannot be null");

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

            var tooltipLines = this.itemCache.computeIfAbsent(order,
                key -> new ItemTooltipPresentation(this.computeItemTooltip(key, cfg))).lines();
            lines.addAll(1, tooltipLines);
        });
    }

    public OrderTooltipData listTooltip(TrackedOrder order, OrderListTooltipConfig cfg) {
        return this.listCache.computeIfAbsent(order, key -> this.computeTooltip(key,
            cfg.showStatus, cfg.showQueue, cfg.showPrices, cfg.showOnlyWhenUndercut, false, true));
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

    private OrderTooltipData computeItemTooltip(TrackedOrder order, OrderItemTooltipConfig cfg) {
        return this.computeTooltip(order, cfg.showStatus, cfg.showQueue, cfg.showPrices,
            cfg.showOnlyWhenUndercut, cfg.showEstimatedTime, false);
    }

    private OrderTooltipData computeTooltip(
        TrackedOrder order,
        boolean showStatus,
        boolean showQueue,
        boolean showPrices,
        boolean onlyWhenUndercut,
        boolean showEstimatedTime,
        boolean showOwnOrder
    ) {
        var product = order.product;
        if (product.bazaarProductId().isEmpty()) {
            return new OrderTooltipData(order.productName, null, null, null, null, null);
        }
        boolean undercut = order.status instanceof OrderStatus.Undercut;
        var queue = showQueue && undercut
            ? this.bazaarData.calculateQueuePosition(product, order.type, order.pricePerUnit)
                .map(value -> new OrderTooltipData.Queue(value.ordersAhead, value.itemsAhead,
                    ConfigStore.get().config().trackedOrders.queueDisplayMode))
                .orElse(null)
            : null;
        var minutes = showStatus && showEstimatedTime && order.status instanceof OrderStatus.Top
            ? this.bazaarData.getEstimatedFillTimeMinutes(product, order.type,
                order.volume - order.fillAmountSnapshot).orElse(null)
            : null;
        var prices = showPrices && (!onlyWhenUndercut || undercut)
            ? this.bazaarData.getMarketPrices(product) : null;
        return new OrderTooltipData(null, showStatus ? order.status : null, minutes, queue,
            showOwnOrder ? new OrderTooltipData.OwnOrder(order.pricePerUnit, order.volume) : null, prices);
    }

}
