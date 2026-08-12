package com.github.lutzluca.btrbz.core.widgets.data;

import com.github.lutzluca.btrbz.core.OrderTooltipProvider;
import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.orderbook.OrderBookScreen;
import com.github.lutzluca.btrbz.core.trackedorders.TrackedOrderManager;
import com.github.lutzluca.btrbz.core.widgets.bookmarks.BookmarkComponent;
import com.github.lutzluca.btrbz.core.widgets.dailylimit.DailyLimitComponent;
import com.github.lutzluca.btrbz.core.widgets.orderbook.OrderBookPriceComponent;
import com.github.lutzluca.btrbz.core.widgets.ordervalue.OrderValueComponent;
import com.github.lutzluca.btrbz.core.widgets.presets.OrderPreset;
import com.github.lutzluca.btrbz.core.widgets.presets.OrderPresetsComponent;
import com.github.lutzluca.btrbz.core.widgets.session.BtrBzWidgetSession;
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
public final class BazaarWidgetData {
    private static final int PRODUCT_SLOT = 13;
    private static final int SELL_INSTANTLY_SLOT = 11;
    private final com.github.lutzluca.btrbz.data.BazaarData market;
    private final TrackedOrderManager trackedOrders;
    private final OrderTooltipProvider tooltipProvider;
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
        BookmarkComponent bookmarks,
        OrderValueComponent orderValue,
        OrderBookPriceComponent orderBookPrice,
        OrderPresetsComponent presets,
        DailyLimitComponent dailyLimit
    ) {
        this.market = market;
        this.trackedOrders = trackedOrders;
        this.tooltipProvider = tooltipProvider;
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

    public BazaarWidgetViewData.OrdersData orders() {
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
            Optional<BazaarWidgetViewData.MarketInfo> marketInfo = marketInfo(
                product, snapshot.type(), snapshot.pricePerUnit(), status
            );
            List<Component> tooltip = Optional.ofNullable(live.get(snapshot.id()))
                .filter(_ -> ConfigManager.get().orderListTooltip.enabled)
                .map(order -> this.tooltipProvider.getCachedTooltip(
                    order, ConfigManager.get().orderListTooltip
                ))
                .orElseGet(List::of);
            var icon = this.orderIcons.getOrDefault(snapshot.id(), new ItemStack(Items.CHEST));
            return new BazaarWidgetViewData.Order(
                snapshot.id(),
                snapshot.type() == OrderType.Buy ? BazaarWidgetViewData.OrderSide.Buy : BazaarWidgetViewData.OrderSide.Sell,
                snapshot.productName(),
                Utils.legacyFormattedComponent(product.visualName()),
                icon,
                Math.round(snapshot.pricePerUnit()),
                snapshot.volume(),
                Optional.of(new BazaarWidgetViewData.FillProgress(
                    Math.max(0, Math.min(snapshot.fillAmountSnapshot(), snapshot.volume())),
                    snapshot.volume()
                )),
                status,
                marketInfo,
                tooltip,
                creationSequence.getOrDefault(snapshot.id(), 0L)
            );
        }).toList();
        return new BazaarWidgetViewData.OrdersData(orders, this.orderValue.filledOrderCount());
    }

    public BazaarWidgetViewData.OrderValueData orderValue() {
        var value = this.orderValue.currentBreakdown();
        return new BazaarWidgetViewData.OrderValueData(
            Math.round(value.buyLocked()),
            Math.round(value.buyItems()),
            Math.round(value.sellClaimable()),
            Math.round(value.sellPending()),
            Math.round(value.total())
        );
    }

    public BazaarWidgetViewData.OrderBookData orderBook(WidgetRenderContext context) {
        var session = (BtrBzWidgetSession) context.session();
        ProductIdentity product = null;
        String name = "Order Book";
        ItemStack icon = ItemStack.EMPTY;
        Optional<BazaarWidgetViewData.OrderSide> appropriateSide = Optional.empty();
        if (session.host() == BtrBzWidgetSession.HostKind.OrderBook
            && ScreenInfoHelper.get().getCurrInfo().getScreen() instanceof OrderBookScreen screen) {
            product = screen.product();
            name = screen.productName();
            icon = screen.productIcon();
        } else if (session.host() == BtrBzWidgetSession.HostKind.Sign) {
            product = this.orderBookPrice.currentWorkflow()
                .map(OrderBookPriceComponent.Workflow::product).orElse(null);
            if (product != null) {
                name = product.visualName();
                icon = currentProductIcon();
            }
            appropriateSide = session.side().map(side -> side == OrderType.Buy
                ? BazaarWidgetViewData.OrderSide.Buy
                : BazaarWidgetViewData.OrderSide.Sell);
        }
        if (product == null) {
            return new BazaarWidgetViewData.OrderBookData(
                name, icon, List.of(), List.of(), appropriateSide
            );
        }
        var lists = this.market.getOrderLists(product);
        return new BazaarWidgetViewData.OrderBookData(
            name,
            icon,
            lists.buyOrders().stream().map(summary -> new BazaarWidgetViewData.OrderBookEntry(
                BazaarWidgetViewData.OrderSide.Buy,
                summary.getPricePerUnit(),
                (int) summary.getAmount(),
                (int) summary.getOrders()
            )).toList(),
            lists.sellOffers().stream().map(summary -> new BazaarWidgetViewData.OrderBookEntry(
                BazaarWidgetViewData.OrderSide.Sell,
                summary.getPricePerUnit(),
                (int) summary.getAmount(),
                (int) summary.getOrders()
            )).toList(),
            appropriateSide
        );
    }

    public BazaarWidgetViewData.BookmarksData bookmarks() {
        return new BazaarWidgetViewData.BookmarksData(this.bookmarks.currentBookmarks().stream().map(bookmark ->
            new BazaarWidgetViewData.Bookmark(
                bookmark.productId(),
                bookmark.productName(),
                Utils.legacyFormattedComponent(bookmark.formattedName()),
                bookmark.itemStack(),
                bookmark.hasBuyOrder(),
                bookmark.hasSellOffer()
            )
        ).toList());
    }

    public BazaarWidgetViewData.PresetsData presets() {
        return new BazaarWidgetViewData.PresetsData(this.presets.currentPresets().stream().map(state -> {
            var preset = state.preset();
            String label = switch (preset) {
                case OrderPreset.Maximum _ -> "Maximum";
                case OrderPreset.Clipboard _ -> "Clipboard";
                case OrderPreset.Fixed fixed -> BazaarWidgetViewData.formatInt(fixed.amount());
            };
            String tooltip = switch (state) {
                case OrderPresetsComponent.PresetState.Available available ->
                    preset instanceof OrderPreset.Maximum
                        ? BazaarWidgetViewData.formatInt(available.resolvedVolume()) + " items"
                        : preset instanceof OrderPreset.Clipboard ? "From Clipboard" : "";
                case OrderPresetsComponent.PresetState.PriceUnavailable _ -> "Price unavailable";
                case OrderPresetsComponent.PresetState.PurseUnavailable _ -> "Purse unavailable";
                case OrderPresetsComponent.PresetState.InsufficientCoins _ -> "Insufficient coins";
                case OrderPresetsComponent.PresetState.CannotAffordSingleItem unavailable ->
                    "Missing " + BazaarWidgetViewData.formatCompact(unavailable.missingCoins()) + " coins";
            };
            return new BazaarWidgetViewData.Preset(
                preset,
                label,
                tooltip,
                state instanceof OrderPresetsComponent.PresetState.Available
            );
        }).toList());
    }

    public BazaarWidgetViewData.DailyLimitData dailyLimit() {
        var usage = this.dailyLimit.currentUsage();
        return new BazaarWidgetViewData.DailyLimitData(Math.round(usage.used()), Math.round(usage.limit()));
    }

    public BazaarWidgetViewData.PriceDifferenceData priceDifference() {
        var info = ScreenInfoHelper.get().getCurrInfo();
        var productStack = info.getItemStack(PRODUCT_SLOT).orElse(ItemStack.EMPTY);
        int quantity = info.getItemStack(SELL_INSTANTLY_SLOT).flatMap(this::listedCount).orElse(0);
        if (productStack.isEmpty() || quantity <= 0) {
            return new BazaarWidgetViewData.PriceDifferenceData("", ItemStack.EMPTY, 0, 0);
        }
        var product = this.market.resolveProduct(productStack);
        var spread = this.market.productSpread(product);
        if (spread.isEmpty()) {
            return new BazaarWidgetViewData.PriceDifferenceData("", ItemStack.EMPTY, 0, 0);
        }
        return new BazaarWidgetViewData.PriceDifferenceData(
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
            .orElse(ItemStack.EMPTY);
    }

    private Optional<BazaarWidgetViewData.MarketInfo> marketInfo(
        ProductIdentity product,
        OrderType side,
        double unitPrice,
        BazaarWidgetViewData.OrderStatus status
    ) {
        var best = side == OrderType.Buy
            ? this.market.highestBuyOrderPrice(product)
            : this.market.lowestSellOfferPrice(product);
        var queue = this.market.calculateQueuePosition(product, side, unitPrice);
        if (best.isEmpty() && queue.isEmpty()) return Optional.empty();
        return Optional.of(new BazaarWidgetViewData.MarketInfo(
            best.map(OptionalDouble::of).orElseGet(OptionalDouble::empty),
            best.map(value -> OptionalDouble.of(Math.abs(value - unitPrice)))
                .orElseGet(OptionalDouble::empty),
            queue.map(value -> OptionalInt.of(value.ordersAhead))
                .orElseGet(OptionalInt::empty),
            queue.map(value -> OptionalLong.of(value.itemsAhead))
                .orElseGet(OptionalLong::empty)
        ));
    }

    private static BazaarWidgetViewData.OrderStatus status(OrderStatus status) {
        return switch (status) {
            case OrderStatus.Top _ -> BazaarWidgetViewData.OrderStatus.Top;
            case OrderStatus.Matched _ -> BazaarWidgetViewData.OrderStatus.Matched;
            case OrderStatus.Undercut _ -> BazaarWidgetViewData.OrderStatus.Undercut;
            case OrderStatus.Unknown _ -> BazaarWidgetViewData.OrderStatus.Unknown;
        };
    }
}
