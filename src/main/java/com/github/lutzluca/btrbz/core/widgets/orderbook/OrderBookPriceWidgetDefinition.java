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

public final class OrderBookPriceWidgetDefinition {
    public static final WidgetId ID = WidgetId.of(Identifier.fromNamespaceAndPath("btrbz", "order_book_price"));

    private OrderBookPriceWidgetDefinition() {}

    public static WidgetDefinition<OrderBookWidgetData.Snapshot, OrderBookPriceWidgetConfig, OrderBookAction> create(
        WidgetDataSource<OrderBookWidgetData.Snapshot> provider,
        OrderBookPriceComponent embeddedWorkflow
    ) {
        var config = new WidgetConfigHandle<>(ID,
            () -> ConfigManager.get().widgets.orderBookPrice, OrderBookPriceWidgetConfig::new,
            value -> value.frame, OrderBookPriceWidgetConfig::resetPreferences);

        return WidgetDefinition.<OrderBookWidgetData.Snapshot, OrderBookPriceWidgetConfig, OrderBookAction>builder(ID,
            "Order Book Price")
            .description("Shows the current order book while entering a price and lets you select a price.")
            .config(config)
            .supports(OrderBookPriceWidgetDefinition::supportsSession)
            .data(provider)
            .cachePrepared()
            .preview(() -> {
                var data = OrderBookWidgetData.preview();
                return new WidgetPreview<>(data, WidgetPreviewSessions.sign(data), "default");
            })
            .viewFactory(EmbeddedOrderBookWidgetView::new)
            .actionHandler(new OrderBookActionHandler(embeddedWorkflow))
            .settingsPanel(OrderBookPriceWidgetSettings::create)
            .minSize(WidgetLayoutTokens.panelWidth(118), 48)
            .build();
    }

    public static boolean supportsSession(WidgetSession session) {
        return session.inSign() && session.product().isPresent() && session.side().isPresent();
    }
}
