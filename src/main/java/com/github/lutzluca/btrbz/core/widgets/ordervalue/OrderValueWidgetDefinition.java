package com.github.lutzluca.btrbz.core.widgets.ordervalue;

import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.widgets.WidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.WidgetId;
import com.github.lutzluca.btrbz.core.widgets.WidgetPreview;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetPreviewSessions;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetLayoutTokens;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import net.minecraft.resources.Identifier;

public final class OrderValueWidgetDefinition {
    public static final WidgetId ID = WidgetId.of(Identifier.fromNamespaceAndPath("btrbz", "order_value"));
    private OrderValueWidgetDefinition() {}
    public static WidgetDefinition<OrderValueWidgetData.Snapshot, OrderValueWidgetConfig, Void> create(
        OrderValueComponent component
    ) {
        var data = new OrderValueWidgetData(component);
        return WidgetDefinition.<OrderValueWidgetData.Snapshot, OrderValueWidgetConfig, Void>builder(ID, "Order Value")
            .config(() -> ConfigManager.get().widgets.orderValue, OrderValueWidgetConfig::new,
                config -> config.frame, OrderValueWidgetConfig::resetPreferences)
            .supports(session -> session.inBazaarMenu(BazaarMenuType.Orders))
            .runtimeData(_ -> data.snapshot())
            .preview(() -> new WidgetPreview<>(OrderValueWidgetData.preview(), WidgetPreviewSessions.container(BazaarMenuType.Orders), "default"))
            .viewFactory(OrderValueWidgetView::new)
            .settingsPanel(OrderValueWidgetSettings::create)
            .minSize(WidgetLayoutTokens.panelWidth(90), 32)
            .build();
    }
}
