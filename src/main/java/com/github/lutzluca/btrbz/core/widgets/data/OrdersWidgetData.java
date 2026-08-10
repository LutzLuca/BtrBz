package com.github.lutzluca.btrbz.core.widgets.data;

import com.github.lutzluca.btrbz.core.OrderTooltipProvider;
import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.trackedorders.TrackedOrderManager;
import com.github.lutzluca.btrbz.core.widgets.WidgetMath;
import com.github.lutzluca.btrbz.core.widgets.cache.CacheDependencies;
import com.github.lutzluca.btrbz.core.widgets.cache.CacheToken;
import com.github.lutzluca.btrbz.core.widgets.cache.WidgetDataSource;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrder;
import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrderId;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import com.github.lutzluca.btrbz.utils.Utils;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/** Shared defensive order snapshots used by the HUD and tracked-orders widgets. */
public final class OrdersWidgetData implements WidgetDataSource<BazaarWidgetViewData.OrdersData> {
    private final BazaarData market;
    private final TrackedOrderManager trackedOrders;
    private final @Nullable OrderTooltipProvider tooltipProvider;
    private final CacheDependencies dependencies;

    public OrdersWidgetData(
        BazaarData market,
        TrackedOrderManager trackedOrders,
        @Nullable OrderTooltipProvider tooltipProvider
    ) {
        this(
            market, trackedOrders, tooltipProvider,
            ScreenInfoHelper.get().screenTransitions(), ScreenInfoHelper.get().inventoryChanges()
        );
    }

    OrdersWidgetData(
        BazaarData market,
        TrackedOrderManager trackedOrders,
        @Nullable OrderTooltipProvider tooltipProvider,
        CacheToken screenTransitions,
        CacheToken inventoryChanges
    ) {
        this.market = market;
        this.trackedOrders = trackedOrders;
        this.tooltipProvider = tooltipProvider;

        var baseDependencies = CacheDependencies.of(
            trackedOrders.dataChanges(), market.marketChanges(), market.indexChanges(),
            screenTransitions, inventoryChanges
        );
        this.dependencies = tooltipProvider == null
            ? baseDependencies
            : baseDependencies.and(CacheDependencies.of(tooltipProvider.listSettingsChanges()));
    }

    @Override
    public CacheDependencies cacheDependencies() {
        return this.dependencies;
    }

    @Override
    public boolean sessionSensitive() {
        return false;
    }

    @Override
    public BazaarWidgetViewData.OrdersData snapshot(WidgetSession session) {
        return this.computeSnapshot();
    }

    BazaarWidgetViewData.OrdersData computeSnapshot() {
        var snapshots = this.trackedOrders.currentOrders();

        if (snapshots.isEmpty()) {
            return new BazaarWidgetViewData.OrdersData(List.of(), this.trackedOrders.filledOrderCount());
        }

        var screenInfo = ScreenInfoHelper.get().getCurrInfo();
        Map<TrackedOrderId, TrackedOrder> live = new HashMap<>();
        this.trackedOrders.getTrackedOrders().forEach(order -> live.put(order.id(), order));

        Map<TrackedOrderId, Long> creationSequence = new HashMap<>();
        var canonicalOrder = this.trackedOrders.creationOrder();

        for (int index = 0; index < canonicalOrder.size(); index++) {
            creationSequence.put(canonicalOrder.get(index), (long) index);
        }

        var orders = snapshots.stream().map(snapshot -> {
            var status = status(snapshot.status());
            var product = snapshot.product();
            var marketInfo = this.marketInfo(product, snapshot.type(), snapshot.pricePerUnit());
            List<Component> tooltip = Optional.ofNullable(live.get(snapshot.id()))
                .filter(_ -> this.tooltipProvider != null && ConfigManager.get().orderListTooltip.enabled)
                .map(order -> this.tooltipProvider.getCachedTooltip(order, ConfigManager.get().orderListTooltip))
                .orElseGet(List::of);

            return new BazaarWidgetViewData.Order(
                snapshot.id(),
                snapshot.type() == OrderType.Buy ? BazaarWidgetViewData.OrderSide.Buy : BazaarWidgetViewData.OrderSide.Sell,
                snapshot.productName(),
                Utils.legacyFormattedComponent(product.visualName()),
                this.market.productStack(product)
                    .or(() -> this.observedProductStack(screenInfo, snapshot.slot(), product)),
                snapshot.pricePerUnit(),
                snapshot.volume(),
                Optional.of(new BazaarWidgetViewData.FillProgress(
                    WidgetMath.clamp(snapshot.fillAmountSnapshot(), 0, snapshot.volume()), snapshot.volume()
                )),
                status,
                marketInfo,
                tooltip,
                creationSequence.getOrDefault(snapshot.id(), 0L)
            );
        }).toList();

        return new BazaarWidgetViewData.OrdersData(orders, this.trackedOrders.filledOrderCount());
    }

    public static BazaarWidgetViewData.OrdersData preview() {
        return new BazaarWidgetViewData.OrdersData(List.of(
            previewOrder("cookie", BazaarWidgetViewData.OrderSide.Buy, "Booster Cookie", Items.COOKIE, 9_825_000, 4, 1, BazaarWidgetViewData.OrderStatus.Matched),
            previewOrder("diamond", BazaarWidgetViewData.OrderSide.Sell, "Enchanted Diamond", Items.DIAMOND, 1_234, 640, 0, BazaarWidgetViewData.OrderStatus.Top),
            previewOrder("gold", BazaarWidgetViewData.OrderSide.Buy, "Enchanted Gold Block", Items.GOLD_BLOCK, 182_400, 32, 18, BazaarWidgetViewData.OrderStatus.Matched),
            previewOrder("pearl", BazaarWidgetViewData.OrderSide.Sell, "Enchanted Ender Pearl", Items.ENDER_PEARL, 1_840, 2_048, 512, BazaarWidgetViewData.OrderStatus.Undercut),
            previewOrder("blaze", BazaarWidgetViewData.OrderSide.Buy, "Enchanted Blaze Rod", Items.BLAZE_ROD, 1_950_000, 8, 0, BazaarWidgetViewData.OrderStatus.Unknown),
            previewOrder("quartz", BazaarWidgetViewData.OrderSide.Sell, "Enchanted Quartz", Items.QUARTZ, 2_175, 1_280, 960, BazaarWidgetViewData.OrderStatus.Top),
            previewOrder("emerald", BazaarWidgetViewData.OrderSide.Buy, "Enchanted Emerald", Items.EMERALD, 1_118, 3_584, 1_792, BazaarWidgetViewData.OrderStatus.Matched),
            previewOrder("cane", BazaarWidgetViewData.OrderSide.Sell, "Enchanted Sugar Cane", Items.SUGAR_CANE, 85_500, 96, 24, BazaarWidgetViewData.OrderStatus.Undercut)
        ), 3);
    }

    private Optional<BazaarWidgetViewData.MarketInfo> marketInfo(
        ProductIdentity product,
        OrderType side,
        double unitPrice
    ) {
        var best = side == OrderType.Buy
            ? this.market.highestBuyOrderPrice(product)
            : this.market.lowestSellOfferPrice(product);
        var queue = this.market.calculateQueuePosition(product, side, unitPrice);

        if (best.isEmpty() && queue.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new BazaarWidgetViewData.MarketInfo(
            best.map(OptionalDouble::of).orElseGet(OptionalDouble::empty),
            best.map(value -> OptionalDouble.of(Math.abs(value - unitPrice))).orElseGet(OptionalDouble::empty),
            queue.map(value -> OptionalInt.of(value.ordersAhead)).orElseGet(OptionalInt::empty),
            queue.map(value -> OptionalLong.of(value.itemsAhead)).orElseGet(OptionalLong::empty)
        ));
    }

    private Optional<ItemStack> observedProductStack(
        ScreenInfo screenInfo,
        int slot,
        ProductIdentity expectedProduct
    ) {
        if (!screenInfo.inMenu(BazaarMenuType.Orders)) {
            return Optional.empty();
        }

        return screenInfo
            .getItemStack(slot)
            .filter(stack -> sameProduct(expectedProduct, this.market.resolveProduct(stack)))
            .map(ItemStack::copy);
    }

    static boolean sameProduct(ProductIdentity expected, ProductIdentity observed) {
        return Utils
            .zipOptionals(expected.bazaarProductId(), observed.bazaarProductId())
            .map(ids -> ids.getLeft().equals(ids.getRight()))
            .orElseGet(() -> Utils.normalizeDisplayName(expected.strippedName())
                .equals(Utils.normalizeDisplayName(observed.strippedName())));
    }

    private static BazaarWidgetViewData.OrderStatus status(OrderStatus status) {
        return switch (status) {
            case OrderStatus.Top _ -> BazaarWidgetViewData.OrderStatus.Top;
            case OrderStatus.Matched _ -> BazaarWidgetViewData.OrderStatus.Matched;
            case OrderStatus.Undercut _ -> BazaarWidgetViewData.OrderStatus.Undercut;
            case OrderStatus.Unknown _ -> BazaarWidgetViewData.OrderStatus.Unknown;
        };
    }

    private static BazaarWidgetViewData.Order previewOrder(
        String key,
        BazaarWidgetViewData.OrderSide side,
        String name,
        Item item,
        long price,
        int total,
        int filled,
        BazaarWidgetViewData.OrderStatus status
    ) {
        var id = new TrackedOrderId(UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)));
        List<Component> tooltip = List.of(
            Component.literal(status.label()),
            Component.literal("Volume: " + BazaarWidgetViewData.formatInt(total)),
            Component.literal("Price each: " + BazaarWidgetViewData.formatPrice(price))
        );
        Optional<BazaarWidgetViewData.MarketInfo> market = switch (status) {
            case Undercut -> Optional.of(BazaarWidgetViewData.MarketInfo.bestPriceAndQueue(price + 0.1, 0.1, 1, total));
            case Matched -> Optional.of(BazaarWidgetViewData.MarketInfo.queue(3, total * 18L));
            case Top, Unknown -> Optional.empty();
        };

        return new BazaarWidgetViewData.Order(
            id, side, name, styled(name, item), Optional.of(new ItemStack(item)), price, total,
            Optional.of(new BazaarWidgetViewData.FillProgress(filled, total)), status, market, tooltip
        );
    }

    private static Component styled(String name, Item item) {
        var component = Component.literal(name);

        if (item == Items.COOKIE) {
            return component.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        }

        if (item == Items.DIAMOND) {
            return component.withStyle(ChatFormatting.AQUA);
        }

        if (item == Items.EMERALD) {
            return component.withStyle(ChatFormatting.GREEN);
        }

        return component.withStyle(ChatFormatting.GRAY);
    }
}
