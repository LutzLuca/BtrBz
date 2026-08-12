package com.github.lutzluca.btrbz.core.widgets.orderbook;

import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.widgets.WidgetCacheKey;
import com.github.lutzluca.btrbz.core.widgets.WidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.WidgetId;
import com.github.lutzluca.btrbz.core.widgets.WidgetPreview;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetPreviewSessions;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetLayoutTokens;
import net.minecraft.resources.Identifier;

public final class OrderBookPriceWidgetDefinition {
    public static final WidgetId ID = WidgetId.of(Identifier.fromNamespaceAndPath("btrbz", "order_book_price"));
    private OrderBookPriceWidgetDefinition() {}
    public static WidgetDefinition<OrderBookWidgetData.Snapshot, OrderBookPriceWidgetConfig, OrderBookAction> create(
        OrderBookWidgetData provider,
        OrderBookPriceComponent embeddedWorkflow
    ) {
        return WidgetDefinition.<OrderBookWidgetData.Snapshot, OrderBookPriceWidgetConfig, OrderBookAction>builder(ID, "Order Book Price")
            .config(() -> ConfigManager.get().widgets.orderBookPrice, OrderBookPriceWidgetConfig::new,
                config -> config.frame, OrderBookPriceWidgetConfig::resetPreferences)
            .supports(session -> session.inSign() && session.product().isPresent() && session.side().isPresent())
            .runtimeData(provider::snapshot)
            .cacheKey(session -> {
                var config = ConfigManager.get().widgets.orderBookPrice;
                return new CacheKey(
                    provider.stateKey(session),
                    config.contentWidth,
                    config.visibleRows,
                    config.showOrderCount,
                    config.sideDisplay
                );
            })
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

    private record CacheKey(
        OrderBookWidgetData.StateKey state,
        int contentWidth,
        int visibleRows,
        boolean showOrderCount,
        OrderBookPriceWidgetConfig.EmbeddedSideDisplay sideDisplay
    ) implements WidgetCacheKey {}
}
