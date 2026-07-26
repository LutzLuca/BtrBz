package com.github.lutzluca.btrbz.core.widgets;

import com.github.lutzluca.btrbz.widgets.framework.WidgetAnchorSpace;
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
        BazaarDataProvider runtimeData,
        BazaarDataProvider previewData,
        WidgetActionHandler<BazaarAction> actionHandler
    ) {
        registry.registerHud(WidgetDefinition.<BazaarData.OrdersData>readOnlyBuilder(
                BAZAAR_ORDERS_ID, "Bazaar Orders"
            )
            .defaultPlacement(WidgetPlacement.topLeft(0.04, 0.05))
            .defaultActive(true)
            .minSize(WidgetLayoutTokens.panelWidth(BazaarHudOptions.MINIMUM_CONTENT_WIDTH), 28)
            .configurationPanel(() -> configurationPanels.apply(BAZAAR_ORDERS_ID))
            .dataProvider(runtimeData::orders)
            .previewDataProvider(previewData::orders)
            .displayWhen(context -> session(context).host() == BtrBzWidgetSession.HostKind.HUD)
            .displayWhenData(data -> !options.get().hud().hideWhenEmpty()
                || !data.orders().isEmpty()
                || data.filledOrderCount() > 0)
            .componentFactory((snapshot, context) -> {
                var hud = options.get().hud();
                return BazaarComponents.bazaarOrdersHud(
                    snapshot,
                    context.layout().availableHeight(),
                    hud
                );
            })
            .build());

        registry.registerBazaar(WidgetDefinition.<BazaarData.OrdersData, BazaarAction>builder(
                TRACKED_ORDERS_ID, "Tracked Orders"
            )
            .defaultPlacement(WidgetPlacement.topLeft(0.04, 0.18))
            .defaultActive(true)
            .minSize(WidgetLayoutTokens.panelWidth(180), 46)
            .configurationPanel(() -> configurationPanels.apply(TRACKED_ORDERS_ID))
            .dataProvider(runtimeData::orders)
            .previewDataProvider(previewData::orders)
            .displayWhen(context -> session(context).host() == BtrBzWidgetSession.HostKind.CONTAINER
                && session(context).menu().isPresent())
            .actionHandler(actionHandler)
            .componentFactory((snapshot, context) -> {
                var tracked = options.get().trackedOrders();
                return BazaarComponents.trackedOrdersList(
                    snapshot,
                    tracked,
                    context.interactive(),
                    context.instanceState().getOrCreate("scroll", WidgetScrollState.class, WidgetScrollState::new),
                    context.instanceState().getOrCreate("drag", BazaarData.DragController.class, BazaarData.DragController::new),
                    context.instanceState().getOrCreate("hover", BazaarData.HoverController.class, BazaarData.HoverController::new),
                    context.actions()
                );
            })
            .build());

        registry.registerBazaar(WidgetDefinition.<BazaarData.OrderValueData>readOnlyBuilder(
                ORDER_VALUE_ID, "Order Value"
            )
            .defaultPlacement(WidgetPlacement.topLeft(0.65, 0.16))
            .defaultActive(true)
            .minSize(WidgetLayoutTokens.panelWidth(170), 32)
            .configurationPanel(() -> configurationPanels.apply(ORDER_VALUE_ID))
            .dataProvider(runtimeData::orderValue)
            .previewDataProvider(previewData::orderValue)
            .displayWhen(context -> session(context).host() == BtrBzWidgetSession.HostKind.CONTAINER
                && session(context).menu().filter(menu -> menu == BazaarMenuType.Orders).isPresent())
            .componentFactory((snapshot, context) -> {
                var orderValue = options.get().orderValue();
                return BazaarComponents.orderValue(snapshot, orderValue);
            })
            .build());

        registry.registerBazaar(WidgetDefinition.<BazaarData.OrderBookData, BazaarAction>builder(
                ORDER_BOOK_SCREEN_ID, "Order Book"
            )
            .defaultPlacement(WidgetPlacement.topLeft(0.55, 0.34))
            .defaultActive(true)
            .minSize(WidgetLayoutTokens.panelWidth(220), 48)
            .configurationPanel(() -> configurationPanels.apply(ORDER_BOOK_SCREEN_ID))
            .dataProvider(runtimeData::orderBook)
            .previewDataProvider(previewData::orderBook)
            .displayWhen(context -> session(context).host() == BtrBzWidgetSession.HostKind.ORDER_BOOK
                && session(context).productId().isPresent())
            .actionHandler(actionHandler)
            .componentFactory((snapshot, context) -> {
                var orderBook = options.get().orderBook();
                return BazaarComponents.orderBook(
                    snapshot,
                    orderBook,
                    context.interactive(),
                    context.instanceState().getOrCreate("buy-scroll", WidgetScrollState.class, WidgetScrollState::new),
                    context.instanceState().getOrCreate("sell-scroll", WidgetScrollState.class, WidgetScrollState::new),
                    context.actions()
                );
            })
            .build());

        registry.registerBazaar(WidgetDefinition.<BazaarData.OrderBookData, BazaarAction>builder(
                ORDER_BOOK_PRICE_ID, "Order Book Price"
            )
            .defaultPlacement(WidgetPlacement.topLeft(0.04, 0.50))
            .defaultActive(true)
            .minSize(WidgetLayoutTokens.panelWidth(180), 48)
            .configurationPanel(() -> configurationPanels.apply(ORDER_BOOK_PRICE_ID))
            .dataProvider(runtimeData::orderBook)
            .previewDataProvider(previewData::orderBook)
            .displayWhen(context -> session(context).host() == BtrBzWidgetSession.HostKind.SIGN
                && session(context).productId().isPresent()
                && session(context).side().isPresent())
            .actionHandler(actionHandler)
            .componentFactory((book, context) -> {
                var embeddedOrderBook = options.get().embeddedOrderBook();
                return BazaarExtraComponents.embeddedOrderBook(
                    book,
                    embeddedOrderBook,
                    context.interactive(),
                    context.instanceState().getOrCreate("buy-scroll", WidgetScrollState.class, WidgetScrollState::new),
                    context.instanceState().getOrCreate("sell-scroll", WidgetScrollState.class, WidgetScrollState::new),
                    context.actions()
                );
            })
            .build());

        registry.registerBazaar(WidgetDefinition.<BazaarData.BookmarksData, BazaarAction>builder(
                BOOKMARKS_ID, "Bookmarks"
            )
            .defaultPlacement(WidgetPlacement.topLeft(0.30, 0.52))
            .defaultActive(true)
            .minSize(WidgetLayoutTokens.panelWidth(150), 42)
            .configurationPanel(() -> configurationPanels.apply(BOOKMARKS_ID))
            .dataProvider(runtimeData::bookmarks)
            .previewDataProvider(previewData::bookmarks)
            .displayWhen(context -> session(context).host() == BtrBzWidgetSession.HostKind.CONTAINER
                && session(context).menu().isPresent())
            .actionHandler(actionHandler)
            .componentFactory((data, context) -> {
                var bookmarks = options.get().bookmarks();
                return BazaarExtraComponents.bookmarks(
                    data.bookmarks(),
                    bookmarks,
                    context.interactive(),
                    context.instanceState().getOrCreate("scroll", WidgetScrollState.class, WidgetScrollState::new),
                    context.instanceState().getOrCreate(
                        "drag", BazaarData.BookmarkDragController.class, BazaarData.BookmarkDragController::new
                    ),
                    context.actions()
                );
            })
            .build());

        registry.registerBazaar(WidgetDefinition.<BazaarData.PresetsData, BazaarAction>builder(
                ORDER_PRESETS_ID, "Order Presets"
            )
            .defaultPlacement(WidgetPlacement.topLeft(0.55, 0.58))
            .defaultPlacement("sign", WidgetPlacement.topLeft(0.62, 0.08))
            .defaultActive(true)
            .minSize(WidgetLayoutTokens.panelWidth(90), 42)
            .configurationPanel(() -> configurationPanels.apply(ORDER_PRESETS_ID))
            .dataProvider(runtimeData::presets)
            .previewDataProvider(previewData::presets)
            .displayWhen(context -> {
                var session = session(context);
                return session.host() == BtrBzWidgetSession.HostKind.CONTAINER
                    && session.menu().filter(menu -> menu == BazaarMenuType.BuyOrderSetupVolume).isPresent()
                    || session.host() == BtrBzWidgetSession.HostKind.SIGN
                    && session.previousMenu().filter(menu -> menu == BazaarMenuType.BuyOrderSetupVolume).isPresent();
            })
            .actionHandler(actionHandler)
            .componentFactory((data, context) -> {
                var presets = options.get().presets();
                return BazaarExtraComponents.presets(
                    data.presets(),
                    presets,
                    context.interactive(),
                    context.instanceState().getOrCreate("scroll", WidgetScrollState.class, WidgetScrollState::new),
                    context.actions()
                );
            })
            .build());

        registry.registerBazaar(WidgetDefinition.<BazaarData.DailyLimitData>readOnlyBuilder(
                ORDER_LIMIT_ID, "Daily Limit"
            )
            .defaultPlacement(WidgetPlacement.topLeft(0.76, 0.58))
            .defaultActive(true)
            .anchorSpace(WidgetAnchorSpace.CONTENT)
            .minSize(WidgetLayoutTokens.panelWidth(140), 30)
            .configurationPanel(() -> configurationPanels.apply(ORDER_LIMIT_ID))
            .dataProvider(runtimeData::dailyLimit)
            .previewDataProvider(previewData::dailyLimit)
            .displayWhen(context -> {
                var session = session(context);
                return session.host() == BtrBzWidgetSession.HostKind.CONTAINER
                    && session.menu().filter(menu -> menu == BazaarMenuType.Main
                        || menu == BazaarMenuType.ItemGroup).isPresent();
            })
            .componentFactory((limit, context) -> {
                var orderLimit = options.get().orderLimit();
                return BazaarExtraComponents.orderLimit(
                    limit.used(),
                    limit.limit(),
                    orderLimit
                );
            })
            .build());

        registry.registerBazaar(WidgetDefinition.<BazaarData.PriceDifferenceData>readOnlyBuilder(
                PRICE_DIFF_ID, "Price Difference"
            )
            .defaultPlacement(WidgetPlacement.topLeft(0.76, 0.72))
            .defaultActive(true)
            .anchorSpace(WidgetAnchorSpace.CONTENT)
            .minSize(WidgetLayoutTokens.panelWidth(150), 36)
            .configurationPanel(() -> configurationPanels.apply(PRICE_DIFF_ID))
            .dataProvider(runtimeData::priceDifference)
            .previewDataProvider(previewData::priceDifference)
            .displayWhen(context -> {
                var session = session(context);
                return session.host() == BtrBzWidgetSession.HostKind.CONTAINER
                    && session.menu().filter(menu -> menu == BazaarMenuType.Item).isPresent();
            })
            .displayWhenData(data -> data.quantity() > 0)
            .componentFactory((diff, context) -> {
                var priceDiff = options.get().priceDiff();
                return BazaarExtraComponents.priceDiff(diff, priceDiff);
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
