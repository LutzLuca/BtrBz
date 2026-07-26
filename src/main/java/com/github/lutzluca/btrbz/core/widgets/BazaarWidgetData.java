package com.github.lutzluca.btrbz.core.widgets;

import com.github.lutzluca.btrbz.core.OrderTooltipProvider;
import com.github.lutzluca.btrbz.core.ProductInfoProvider;
import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.orderbook.OrderBookScreen;
import com.github.lutzluca.btrbz.core.trackedorders.TrackedOrderManager;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrder;
import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrderId;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import com.github.lutzluca.btrbz.utils.Utils;
import com.github.lutzluca.btrbz.widgets.framework.WidgetRenderContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The one production aggregator from concrete BtrBz owners to defensive widget records. */
public final class BazaarWidgetData implements BazaarDataProvider {
    private static final int PRODUCT_SLOT = 13;
    private static final int SELL_INSTANTLY_SLOT = 11;
    private final com.github.lutzluca.btrbz.data.BazaarData market;
    private final TrackedOrderManager trackedOrders;
    private final OrderTooltipProvider tooltipProvider;
    private final ProductInfoProvider productInfoProvider;
    private final BookmarkComponent bookmarks;
    private final OrderValueComponent orderValue;
    private final OrderBookPriceComponent orderBookPrice;
    private final OrderPresetsComponent presets;
    private final DailyLimitComponent dailyLimit;
    private final Map<TrackedOrderId, ItemStack> orderIcons = new HashMap<>();

    public BazaarWidgetData(
        com.github.lutzluca.btrbz.data.BazaarData market,
        TrackedOrderManager trackedOrders,
        OrderTooltipProvider tooltipProvider,
        ProductInfoProvider productInfoProvider,
        BookmarkComponent bookmarks,
        OrderValueComponent orderValue,
        OrderBookPriceComponent orderBookPrice,
        OrderPresetsComponent presets,
        DailyLimitComponent dailyLimit
    ) {
        this.market = market;
        this.trackedOrders = trackedOrders;
        this.tooltipProvider = tooltipProvider;
        this.productInfoProvider = productInfoProvider;
        this.bookmarks = bookmarks;
        this.orderValue = orderValue;
        this.orderBookPrice = orderBookPrice;
        this.presets = presets;
        this.dailyLimit = dailyLimit;
    }

    /** Capture mutable inventory stacks immediately after an Orders-screen sync. */
    public void captureOrderIcons() {
        var info = ScreenInfoHelper.get().getCurrInfo();
        for (var order : this.trackedOrders.currentOrders()) {
            info.getItemStack(order.slot()).ifPresent(stack -> this.orderIcons.put(order.id(), stack.copy()));
        }
        var ids = this.trackedOrders.currentOrders().stream().map(TrackedOrderManager.TrackedOrderSnapshot::id).toList();
        this.orderIcons.keySet().removeIf(id -> !ids.contains(id));
    }

    @Override
    public BazaarData.OrdersData orders(WidgetRenderContext context) {
        Map<TrackedOrderId, TrackedOrder> live = new HashMap<>();
        this.trackedOrders.getTrackedOrders().forEach(order -> live.put(order.id(), order));
        Map<TrackedOrderId, Long> creationSequence = new HashMap<>();
        var canonicalOrder = this.trackedOrders.creationOrder();
        for (int index = 0; index < canonicalOrder.size(); index++) {
            creationSequence.put(canonicalOrder.get(index), (long) index);
        }
        var orders = this.trackedOrders.currentOrders().stream().map(snapshot -> {
            var status = status(snapshot.status());
            var product = snapshot.product();
            Optional<BazaarData.MarketInfo> marketInfo = marketInfo(
                product, snapshot.type(), snapshot.pricePerUnit(), status
            );
            List<Component> tooltip = Optional.ofNullable(live.get(snapshot.id()))
                .filter(ignored -> ConfigManager.get().orderListTooltip.enabled)
                .map(order -> this.tooltipProvider.getCachedTooltip(
                    order, ConfigManager.get().orderListTooltip
                ))
                .orElseGet(List::of);
            var icon = this.orderIcons.getOrDefault(snapshot.id(), new ItemStack(Items.CHEST));
            return new BazaarData.Order(
                snapshot.id(),
                snapshot.type() == OrderType.Buy ? BazaarData.OrderSide.BUY : BazaarData.OrderSide.SELL,
                snapshot.productName(),
                Component.literal(product.visualName()),
                icon,
                Math.round(snapshot.pricePerUnit()),
                snapshot.volume(),
                Optional.of(new BazaarData.FillProgress(
                    Math.max(0, Math.min(snapshot.fillAmountSnapshot(), snapshot.volume())),
                    snapshot.volume()
                )),
                status,
                marketInfo,
                tooltip,
                creationSequence.getOrDefault(snapshot.id(), 0L)
            );
        }).toList();
        return new BazaarData.OrdersData(orders, this.orderValue.filledOrderCount());
    }

    @Override
    public BazaarData.OrderValueData orderValue(WidgetRenderContext context) {
        var value = this.orderValue.currentBreakdown();
        return new BazaarData.OrderValueData(
            Math.round(value.buyLocked()),
            Math.round(value.buyItems()),
            Math.round(value.sellClaimable()),
            Math.round(value.sellPending()),
            Math.round(value.total())
        );
    }

    @Override
    public BazaarData.OrderBookData orderBook(WidgetRenderContext context) {
        var session = (BtrBzWidgetSession) context.session();
        ProductIdentity product = null;
        String name = "Order Book";
        ItemStack icon = new ItemStack(Items.BOOK);
        if (session.host() == BtrBzWidgetSession.HostKind.ORDER_BOOK
            && ScreenInfoHelper.get().getCurrInfo().getScreen() instanceof OrderBookScreen screen) {
            product = screen.product();
            name = screen.productName();
            icon = screen.productIcon();
        } else if (session.host() == BtrBzWidgetSession.HostKind.SIGN) {
            product = this.orderBookPrice.currentWorkflow()
                .map(OrderBookPriceComponent.Workflow::product).orElse(null);
            if (product != null) {
                name = product.visualName();
                icon = currentProductIcon();
            }
        }
        if (product == null) return new BazaarData.OrderBookData(name, icon, List.of(), List.of());
        var lists = this.market.getOrderLists(product);
        return new BazaarData.OrderBookData(
            name,
            icon,
            lists.buyOrders().stream().map(summary -> new BazaarData.OrderBookEntry(
                BazaarData.OrderSide.BUY,
                summary.getPricePerUnit(),
                (int) summary.getAmount(),
                (int) summary.getOrders()
            )).toList(),
            lists.sellOffers().stream().map(summary -> new BazaarData.OrderBookEntry(
                BazaarData.OrderSide.SELL,
                summary.getPricePerUnit(),
                (int) summary.getAmount(),
                (int) summary.getOrders()
            )).toList()
        );
    }

    @Override
    public BazaarData.BookmarksData bookmarks(WidgetRenderContext context) {
        return new BazaarData.BookmarksData(this.bookmarks.currentBookmarks().stream().map(bookmark ->
            new BazaarData.Bookmark(
                bookmark.productId(),
                bookmark.productName(),
                Component.literal(bookmark.formattedName()),
                bookmark.itemStack(),
                bookmark.hasBuyOrder(),
                bookmark.hasSellOffer()
            )
        ).toList());
    }

    @Override
    public BazaarData.PresetsData presets(WidgetRenderContext context) {
        return new BazaarData.PresetsData(this.presets.currentPresets().stream().map(state -> {
            var preset = state.preset();
            String label = switch (preset) {
                case OrderPreset.Maximum ignored -> "Maximum";
                case OrderPreset.Clipboard ignored -> "Clipboard";
                case OrderPreset.Fixed fixed -> BazaarData.formatInt(fixed.amount());
            };
            String tooltip = switch (state) {
                case OrderPresetsComponent.PresetState.Available available ->
                    preset instanceof OrderPreset.Maximum
                        ? BazaarData.formatInt(available.resolvedVolume()) + " items"
                        : preset instanceof OrderPreset.Clipboard ? "From Clipboard" : "";
                case OrderPresetsComponent.PresetState.PriceUnavailable ignored -> "Price unavailable";
                case OrderPresetsComponent.PresetState.PurseUnavailable ignored -> "Purse unavailable";
                case OrderPresetsComponent.PresetState.InsufficientCoins ignored -> "Insufficient coins";
                case OrderPresetsComponent.PresetState.CannotAffordSingleItem unavailable ->
                    "Missing " + BazaarData.formatCompact(unavailable.missingCoins()) + " coins";
            };
            return new BazaarData.Preset(
                preset,
                label,
                tooltip,
                state instanceof OrderPresetsComponent.PresetState.Available
            );
        }).toList());
    }

    @Override
    public BazaarData.DailyLimitData dailyLimit(WidgetRenderContext context) {
        var usage = this.dailyLimit.currentUsage();
        return new BazaarData.DailyLimitData(Math.round(usage.used()), Math.round(usage.limit()));
    }

    @Override
    public BazaarData.PriceDifferenceData priceDifference(WidgetRenderContext context) {
        var info = ScreenInfoHelper.get().getCurrInfo();
        var productStack = info.getItemStack(PRODUCT_SLOT).orElse(ItemStack.EMPTY);
        int quantity = info.getItemStack(SELL_INSTANTLY_SLOT).flatMap(this::listedCount).orElse(0);
        if (productStack.isEmpty() || quantity <= 0) {
            return new BazaarData.PriceDifferenceData("", ItemStack.EMPTY, 0, 0);
        }
        var product = this.market.resolveProduct(productStack);
        var spread = this.market.productSpread(product);
        if (spread.isEmpty()) {
            return new BazaarData.PriceDifferenceData("", ItemStack.EMPTY, 0, 0);
        }
        return new BazaarData.PriceDifferenceData(
            productStack.getHoverName().getString(), productStack.copy(), Math.round(spread.get()), quantity
        );
    }

    public boolean hasPriceDifference() {
        var info = ScreenInfoHelper.get().getCurrInfo();
        var product = info.getItemStack(PRODUCT_SLOT);
        int quantity = info.getItemStack(SELL_INSTANTLY_SLOT).flatMap(this::listedCount).orElse(0);
        return quantity > 0 && product.map(this.market::resolveProduct)
            .flatMap(this.market::productSpread).isPresent();
    }

    private Optional<Integer> listedCount(ItemStack stack) {
        return GameUtils.getLore(stack).stream()
            .filter(line -> line.startsWith("Inventory"))
            .findFirst()
            .flatMap(line -> Utils.parseUsFormattedNumber(
                line.replace("Inventory:", "").replace("items", "").trim()
            ).toJavaOptional())
            .map(Number::intValue);
    }

    private ItemStack currentProductIcon() {
        return ScreenInfoHelper.get().getPrevInfo().getItemStack(PRODUCT_SLOT)
            .or(() -> ScreenInfoHelper.get().getCurrInfo().getItemStack(PRODUCT_SLOT))
            .map(ItemStack::copy)
            .orElseGet(() -> new ItemStack(Items.BOOK));
    }

    private Optional<BazaarData.MarketInfo> marketInfo(
        ProductIdentity product,
        OrderType side,
        double unitPrice,
        BazaarData.OrderStatus status
    ) {
        var best = side == OrderType.Buy
            ? this.market.highestBuyOrderPrice(product)
            : this.market.lowestSellOfferPrice(product);
        var queue = this.market.calculateQueuePosition(product, side, unitPrice);
        if (best.isEmpty() && queue.isEmpty()) return Optional.empty();
        return Optional.of(new BazaarData.MarketInfo(
            best.map(OptionalDouble::of).orElseGet(OptionalDouble::empty),
            best.map(value -> OptionalDouble.of(Math.abs(value - unitPrice)))
                .orElseGet(OptionalDouble::empty),
            queue.map(value -> OptionalInt.of(value.ordersAhead))
                .orElseGet(OptionalInt::empty),
            queue.map(value -> OptionalLong.of(value.itemsAhead))
                .orElseGet(OptionalLong::empty)
        ));
    }

    private static BazaarData.OrderStatus status(OrderStatus status) {
        return switch (status) {
            case OrderStatus.Top _ -> BazaarData.OrderStatus.TOP;
            case OrderStatus.Matched _ -> BazaarData.OrderStatus.MATCHED;
            case OrderStatus.Undercut _ -> BazaarData.OrderStatus.UNDERCUT;
            case OrderStatus.Unknown _ -> BazaarData.OrderStatus.UNKNOWN;
        };
    }
}
