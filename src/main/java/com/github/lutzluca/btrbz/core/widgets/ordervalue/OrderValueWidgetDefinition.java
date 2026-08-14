package com.github.lutzluca.btrbz.core.widgets.ordervalue;

import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.widgets.WidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.WidgetId;
import com.github.lutzluca.btrbz.core.widgets.WidgetPreview;
import com.github.lutzluca.btrbz.core.widgets.cache.MemoizedWidgetDataSource;
import com.github.lutzluca.btrbz.core.widgets.config.WidgetConfigHandle;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetPreviewSessions;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetLayoutTokens;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import net.minecraft.resources.Identifier;

public final class OrderValueWidgetDefinition {
    public static final WidgetId ID = WidgetId.of(Identifier.fromNamespaceAndPath("btrbz", "order_value"));

    private OrderValueWidgetDefinition() {}

    public static WidgetDefinition<OrderValueWidgetData.Snapshot, OrderValueWidgetConfig, Void> create(
        OrderValueComponent component
    ) {
        var config = new WidgetConfigHandle<>(ID,
            () -> ConfigManager.get().widgets.orderValue, OrderValueWidgetConfig::new,
            value -> value.frame, OrderValueWidgetConfig::resetPreferences);
        var data = new MemoizedWidgetDataSource<>(new OrderValueWidgetData(component));

        return WidgetDefinition.<OrderValueWidgetData.Snapshot, OrderValueWidgetConfig, Void>builder(ID, "Order Value")
            .description("Summarizes coins locked in buy orders and value waiting in sell orders.")
            .config(config)
            .supports(OrderValueWidgetDefinition::supportsSession)
            .data(data)
            .cachePrepared()
            .preview(() -> new WidgetPreview<>(OrderValueWidgetData.preview(),
                WidgetPreviewSessions.container(BazaarMenuType.Orders), "default"))
            .viewFactory(OrderValueWidgetView::new)
            .settingsPanel(OrderValueWidgetSettings::create)
            .minSize(WidgetLayoutTokens.panelWidth(90), 32)
            .build();
    }

    public static boolean supportsSession(WidgetSession session) {
        return session.inBazaarMenu(BazaarMenuType.Orders);
    }
}
