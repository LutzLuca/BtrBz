package com.github.lutzluca.btrbz.core.widgets;

import com.github.lutzluca.btrbz.core.widgets.action.BazaarAction;
import com.github.lutzluca.btrbz.core.widgets.bookmarks.BookmarkDragController;
import com.github.lutzluca.btrbz.core.widgets.bookmarks.BookmarksWidget;
import com.github.lutzluca.btrbz.core.widgets.config.BazaarWidgetOptions;
import com.github.lutzluca.btrbz.core.widgets.dailylimit.DailyLimitWidget;
import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetData;
import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetPreviewData;
import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import com.github.lutzluca.btrbz.core.widgets.hud.BazaarHudOptions;
import com.github.lutzluca.btrbz.core.widgets.hud.BazaarHudWidget;
import com.github.lutzluca.btrbz.core.widgets.orderbook.OrderBookWidget;
import com.github.lutzluca.btrbz.core.widgets.ordervalue.OrderValueWidget;
import com.github.lutzluca.btrbz.core.widgets.presets.OrderPresetsWidget;
import com.github.lutzluca.btrbz.core.widgets.pricedifference.PriceDifferenceWidget;
import com.github.lutzluca.btrbz.core.widgets.session.BtrBzWidgetSession;
import com.github.lutzluca.btrbz.core.widgets.trackedorders.TrackedOrderDragController;
import com.github.lutzluca.btrbz.core.widgets.trackedorders.TrackedOrderHoverController;
import com.github.lutzluca.btrbz.core.widgets.trackedorders.TrackedOrdersWidget;
import com.github.lutzluca.btrbz.widgets.framework.WidgetActionHandler;
import com.github.lutzluca.btrbz.widgets.framework.WidgetDefinition;
import com.github.lutzluca.btrbz.widgets.framework.WidgetId;
import com.github.lutzluca.btrbz.widgets.framework.WidgetPlacement;
import com.github.lutzluca.btrbz.widgets.framework.WidgetRegistry;
import com.github.lutzluca.btrbz.widgets.framework.ui.WidgetLayoutTokens;
import com.github.lutzluca.btrbz.widgets.framework.ui.WidgetScrollState;
import io.wispforest.owo.ui.core.UIComponent;
import net.minecraft.resources.Identifier;

import java.util.function.Function;
import java.util.function.Supplier;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;

public final class BazaarWidgets {
    private static final String NAMESPACE = "btrbz";
    public static final WidgetId BAZAAR_ORDERS_ID = id("bazaar_orders");
    public static final WidgetId TRACKED_ORDERS_ID = id("tracked_orders_list");
    public static final WidgetId ORDER_VALUE_ID = id("order_value");
    public static final WidgetId ORDER_BOOK_SCREEN_ID = id("order_book_screen");
    public static final WidgetId ORDER_BOOK_PRICE_ID = id("order_book_price");
    public static final WidgetId BOOKMARKS_ID = id("bookmarks");
    public static final WidgetId ORDER_PRESETS_ID = id("order_presets");
    public static final WidgetId ORDER_LIMIT_ID = id("order_limit");
    public static final WidgetId PRICE_DIFF_ID = id("price_diff");

    private BazaarWidgets() {}

    public static void register(
        WidgetRegistry registry,
        Supplier<BazaarWidgetOptions> options,
        Function<WidgetId, UIComponent> configurationPanels,
        BazaarWidgetData runtimeData,
        BazaarWidgetPreviewData previewData,
        WidgetActionHandler<BazaarAction> actionHandler
    ) {
        registry.registerHud(WidgetDefinition.<BazaarWidgetViewData.OrdersData>readOnlyBuilder(
                BAZAAR_ORDERS_ID, "Bazaar Orders"
            )
            .defaultPlacement(WidgetPlacement.topLeft(0.04, 0.05))
            .defaultActive(true)
            .minSize(WidgetLayoutTokens.panelWidth(BazaarHudOptions.MINIMUM_CONTENT_WIDTH), 28)
            .configurationPanel(() -> configurationPanels.apply(BAZAAR_ORDERS_ID))
            .dataProvider(_ -> runtimeData.orders())
            .previewDataProvider(previewData::orders)
            .displayWhen(context -> session(context).host() == BtrBzWidgetSession.HostKind.Hud)
            .displayWhenData(data -> !options.get().hud().hideWhenEmpty()
                || !data.orders().isEmpty()
                || data.filledOrderCount() > 0)
            .componentFactory((snapshot, context) -> {
                var hud = options.get().hud();
                return BazaarHudWidget.render(
                    snapshot,
                    context.layout().availableHeight(),
                    hud
                );
            })
            .build());

        registry.registerBazaar(WidgetDefinition.<BazaarWidgetViewData.OrdersData, BazaarAction>builder(
                TRACKED_ORDERS_ID, "Tracked Orders"
            )
            .defaultPlacement(WidgetPlacement.topLeft(0.04, 0.18))
            .defaultActive(true)
            .minSize(WidgetLayoutTokens.panelWidth(180), 16)
            .configurationPanel(() -> configurationPanels.apply(TRACKED_ORDERS_ID))
            .dataProvider(_ -> runtimeData.orders())
            .previewDataProvider(previewData::orders)
            .displayWhen(context -> session(context).host() == BtrBzWidgetSession.HostKind.Container
                && session(context).menu().isPresent())
            .displayWhenData(data -> !options.get().trackedOrders().hideWhenEmpty()
                || !data.orders().isEmpty())
            .actionHandler(actionHandler)
            .componentFactory((snapshot, context) -> {
                var tracked = options.get().trackedOrders();
                return TrackedOrdersWidget.render(
                    snapshot,
                    tracked,
                    context.interactive(),
                    context.instanceState().getOrCreate("scroll", WidgetScrollState.class, WidgetScrollState::new),
                    context.instanceState().getOrCreate("drag", TrackedOrderDragController.class, TrackedOrderDragController::new),
                    context.instanceState().getOrCreate("hover", TrackedOrderHoverController.class, TrackedOrderHoverController::new),
                    context.actions()
                );
            })
            .build());

        registry.registerBazaar(WidgetDefinition.<BazaarWidgetViewData.OrderValueData>readOnlyBuilder(
                ORDER_VALUE_ID, "Order Value"
            )
            .defaultPlacement(WidgetPlacement.topLeft(0.65, 0.16))
            .defaultActive(true)
            .minSize(WidgetLayoutTokens.panelWidth(170), 32)
            .configurationPanel(() -> configurationPanels.apply(ORDER_VALUE_ID))
            .dataProvider(_ -> runtimeData.orderValue())
            .previewDataProvider(previewData::orderValue)
            .displayWhen(context -> session(context).host() == BtrBzWidgetSession.HostKind.Container
                && session(context).menu().filter(menu -> menu == BazaarMenuType.Orders).isPresent())
            .componentFactory((snapshot, _) -> {
                var orderValue = options.get().orderValue();
                return OrderValueWidget.render(snapshot, orderValue);
            })
            .build());

        registry.registerBazaar(WidgetDefinition.<BazaarWidgetViewData.OrderBookData, BazaarAction>builder(
                ORDER_BOOK_SCREEN_ID, "Order Book"
            )
            .defaultPlacement(WidgetPlacement.topLeft(0.55, 0.34))
            .defaultActive(true)
            .minSize(WidgetLayoutTokens.panelWidth(220), 48)
            .configurationPanel(() -> configurationPanels.apply(ORDER_BOOK_SCREEN_ID))
            .dataProvider(runtimeData::orderBook)
            .previewDataProvider(previewData::orderBook)
            .displayWhen(context -> session(context).host() == BtrBzWidgetSession.HostKind.OrderBook
                && session(context).productId().isPresent())
            .actionHandler(actionHandler)
            .componentFactory((snapshot, context) -> {
                var orderBook = options.get().orderBook();
                return OrderBookWidget.full(
                    snapshot,
                    orderBook,
                    context.interactive(),
                    context.instanceState().getOrCreate("buy-scroll", WidgetScrollState.class, WidgetScrollState::new),
                    context.instanceState().getOrCreate("sell-scroll", WidgetScrollState.class, WidgetScrollState::new),
                    context.actions()
                );
            })
            .build());

        registry.registerBazaar(WidgetDefinition.<BazaarWidgetViewData.OrderBookData, BazaarAction>builder(
                ORDER_BOOK_PRICE_ID, "Order Book Price"
            )
            .defaultPlacement(WidgetPlacement.topLeft(0.04, 0.50))
            .defaultActive(true)
            .minSize(WidgetLayoutTokens.panelWidth(118), 48)
            .configurationPanel(() -> configurationPanels.apply(ORDER_BOOK_PRICE_ID))
            .dataProvider(runtimeData::orderBook)
            .previewDataProvider(previewData::orderBook)
            .displayWhen(context -> session(context).host() == BtrBzWidgetSession.HostKind.Sign
                && session(context).productId().isPresent()
                && session(context).side().isPresent())
            .actionHandler(actionHandler)
            .componentFactory((book, context) -> {
                var embeddedOrderBook = options.get().embeddedOrderBook();
                return OrderBookWidget.embedded(
                    book,
                    embeddedOrderBook,
                    context.interactive(),
                    context.instanceState().getOrCreate("buy-scroll", WidgetScrollState.class, WidgetScrollState::new),
                    context.instanceState().getOrCreate("sell-scroll", WidgetScrollState.class, WidgetScrollState::new),
                    context.actions()
                );
            })
            .build());

        registry.registerBazaar(WidgetDefinition.<BazaarWidgetViewData.BookmarksData, BazaarAction>builder(
                BOOKMARKS_ID, "Bookmarks"
            )
            .defaultPlacement(WidgetPlacement.topLeft(0.30, 0.52))
            .defaultActive(true)
            .minSize(WidgetLayoutTokens.panelWidth(150), 16)
            .configurationPanel(() -> configurationPanels.apply(BOOKMARKS_ID))
            .dataProvider(_ -> runtimeData.bookmarks())
            .previewDataProvider(previewData::bookmarks)
            .displayWhen(context -> session(context).host() == BtrBzWidgetSession.HostKind.Container
                && session(context).menu().isPresent())
            .displayWhenData(data -> !options.get().bookmarks().hideWhenEmpty()
                || !data.bookmarks().isEmpty())
            .actionHandler(actionHandler)
            .componentFactory((data, context) -> {
                var bookmarks = options.get().bookmarks();
                return BookmarksWidget.render(
                    data.bookmarks(),
                    bookmarks,
                    context.interactive(),
                    context.instanceState().getOrCreate("scroll", WidgetScrollState.class, WidgetScrollState::new),
                    context.instanceState().getOrCreate(
                        "drag", BookmarkDragController.class, BookmarkDragController::new
                    ),
                    context.actions()
                );
            })
            .build());

        registry.registerBazaar(WidgetDefinition.<BazaarWidgetViewData.PresetsData, BazaarAction>builder(
                ORDER_PRESETS_ID, "Order Presets"
            )
            .defaultPlacement(WidgetPlacement.topLeft(0.55, 0.58))
            .defaultPlacement("sign", WidgetPlacement.topLeft(0.62, 0.08))
            .defaultActive(true)
            .minSize(WidgetLayoutTokens.panelWidth(90), 42)
            .configurationPanel(() -> configurationPanels.apply(ORDER_PRESETS_ID))
            .dataProvider(_ -> runtimeData.presets())
            .previewDataProvider(previewData::presets)
            .displayWhen(context -> {
                var session = session(context);
                return session.host() == BtrBzWidgetSession.HostKind.Container
                    && session.menu().filter(menu -> menu == BazaarMenuType.BuyOrderSetupVolume).isPresent()
                    || session.host() == BtrBzWidgetSession.HostKind.Sign
                    && session.previousMenu().filter(menu -> menu == BazaarMenuType.BuyOrderSetupVolume).isPresent();
            })
            .actionHandler(actionHandler)
            .componentFactory((data, context) -> {
                var presets = options.get().presets();
                return OrderPresetsWidget.render(
                    data.presets(),
                    presets,
                    context.interactive(),
                    context.instanceState().getOrCreate("scroll", WidgetScrollState.class, WidgetScrollState::new),
                    context.actions()
                );
            })
            .build());

        registry.registerBazaar(WidgetDefinition.<BazaarWidgetViewData.DailyLimitData>readOnlyBuilder(
                ORDER_LIMIT_ID, "Daily Limit"
            )
            .defaultPlacement(WidgetPlacement.topLeft(0.76, 0.58))
            .defaultActive(true)
            .minSize(WidgetLayoutTokens.panelWidth(140), 30)
            .configurationPanel(() -> configurationPanels.apply(ORDER_LIMIT_ID))
            .dataProvider(_ -> runtimeData.dailyLimit())
            .previewDataProvider(previewData::dailyLimit)
            .displayWhen(context -> {
                var session = session(context);
                return session.host() == BtrBzWidgetSession.HostKind.Container
                    && session.menu().filter(menu -> menu == BazaarMenuType.Main
                        || menu == BazaarMenuType.ItemGroup).isPresent();
            })
            .componentFactory((limit, _) -> {
                var orderLimit = options.get().orderLimit();
                return DailyLimitWidget.render(
                    limit.used(),
                    limit.limit(),
                    orderLimit
                );
            })
            .build());

        registry.registerBazaar(WidgetDefinition.<BazaarWidgetViewData.PriceDifferenceData>readOnlyBuilder(
                PRICE_DIFF_ID, "Price Difference"
            )
            .defaultPlacement(WidgetPlacement.topLeft(0.76, 0.72))
            .defaultActive(true)
            .minSize(WidgetLayoutTokens.panelWidth(150), 36)
            .configurationPanel(() -> configurationPanels.apply(PRICE_DIFF_ID))
            .dataProvider(_ -> runtimeData.priceDifference())
            .previewDataProvider(previewData::priceDifference)
            .displayWhen(context -> {
                var session = session(context);
                return session.host() == BtrBzWidgetSession.HostKind.Container
                    && session.menu().filter(menu -> menu == BazaarMenuType.Item).isPresent();
            })
            .displayWhenData(data -> data.quantity() > 0)
            .componentFactory((diff, _) -> {
                var priceDiff = options.get().priceDiff();
                return PriceDifferenceWidget.render(diff, priceDiff);
            })
            .build());
    }

    private static WidgetId id(String path) {
        return WidgetId.of(Identifier.fromNamespaceAndPath(NAMESPACE, path));
    }

    private static BtrBzWidgetSession session(com.github.lutzluca.btrbz.widgets.framework.WidgetRenderContext context) {
        return (BtrBzWidgetSession) context.session();
    }
}
