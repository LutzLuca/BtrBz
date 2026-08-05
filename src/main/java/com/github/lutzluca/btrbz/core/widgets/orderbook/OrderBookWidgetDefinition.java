package com.github.lutzluca.btrbz.core.widgets.orderbook;

import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.widgets.WidgetCacheKey;
import com.github.lutzluca.btrbz.core.widgets.WidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.WidgetId;
import com.github.lutzluca.btrbz.core.widgets.WidgetPreview;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetPreviewSessions;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetDisplayOptions.NumberStyle;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetLayoutTokens;
import net.minecraft.resources.Identifier;

public final class OrderBookWidgetDefinition {
    public static final WidgetId ID = WidgetId.of(Identifier.fromNamespaceAndPath("btrbz", "order_book_screen"));
    private OrderBookWidgetDefinition() {}
    public static WidgetDefinition<OrderBookWidgetData.Snapshot, OrderBookWidgetConfig, OrderBookAction> create(
        OrderBookWidgetData provider,
        OrderBookPriceComponent embeddedWorkflow
    ) {
        return WidgetDefinition.<OrderBookWidgetData.Snapshot, OrderBookWidgetConfig, OrderBookAction>builder(ID, "Order Book")
            .config(() -> ConfigManager.get().widgets.orderBookScreen, OrderBookWidgetConfig::new,
                config -> config.frame, OrderBookWidgetConfig::resetPreferences)
            .supports(session -> session.inOrderBook() && session.product().isPresent())
            .runtimeData(provider::snapshot)
            .cacheKey(session -> {
                var config = ConfigManager.get().widgets.orderBookScreen;
                return new CacheKey(
                    provider.stateKey(session),
                    config.contentWidth,
                    config.visibleRows,
                    config.layout,
                    config.numberStyle,
                    config.showOrderCount
                );
            })
            .preview(() -> {
                var data = OrderBookWidgetData.preview();
                return new WidgetPreview<>(data, WidgetPreviewSessions.orderBook(data), "default");
            })
            .viewFactory(FullOrderBookWidgetView::new)
            .actionHandler(new OrderBookActionHandler(embeddedWorkflow))
            .settingsPanel(OrderBookWidgetSettings::create)
            .minSize(WidgetLayoutTokens.panelWidth(220), 48)
            .build();
    }

    private record CacheKey(
        OrderBookWidgetData.StateKey state,
        int contentWidth,
        int visibleRows,
        OrderBookWidgetConfig.BookLayout layout,
        NumberStyle numberStyle,
        boolean showOrderCount
    ) implements WidgetCacheKey {}
}
