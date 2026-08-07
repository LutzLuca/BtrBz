package com.github.lutzluca.btrbz.core.widgets.orderbook;

import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.widgets.WidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.WidgetId;
import com.github.lutzluca.btrbz.core.widgets.WidgetPreview;
import com.github.lutzluca.btrbz.core.widgets.cache.WidgetDataSource;
import com.github.lutzluca.btrbz.core.widgets.config.WidgetConfigHandle;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetPreviewSessions;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetLayoutTokens;
import net.minecraft.resources.Identifier;

public final class OrderBookWidgetDefinition {
    public static final WidgetId ID = WidgetId.of(Identifier.fromNamespaceAndPath("btrbz", "order_book"));
    private OrderBookWidgetDefinition() {}

    public static WidgetDefinition<OrderBookWidgetData.Snapshot, OrderBookWidgetConfig, OrderBookAction> create(
        WidgetDataSource<OrderBookWidgetData.Snapshot> provider,
        OrderBookPriceComponent embeddedWorkflow
    ) {
        var config = new WidgetConfigHandle<>(ID,
            () -> ConfigManager.get().widgets.orderBookScreen, OrderBookWidgetConfig::new,
            value -> value.frame, OrderBookWidgetConfig::resetPreferences);
        return WidgetDefinition.<OrderBookWidgetData.Snapshot, OrderBookWidgetConfig, OrderBookAction>builder(ID, "Order Book")
            .config(config)
            .supports(OrderBookWidgetDefinition::supportsSession)
            .data(provider)
            .cachePrepared()
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

    public static boolean supportsSession(WidgetSession session) {
        return session.inOrderBook() && session.product().isPresent();
    }
}
