package com.github.lutzluca.btrbz.core.widgets.ordervalue;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetConfigBinding;
import io.wispforest.owo.ui.core.UIComponent;
import static com.github.lutzluca.btrbz.core.widgets.ui.WidgetSettingsPanel.*;

public final class OrderValueWidgetSettings {
    private OrderValueWidgetSettings() {}
    public static UIComponent create(WidgetConfigBinding<OrderValueWidgetConfig> binding) {
        var panel = panel();
        integer(panel, "Widget width", binding, c -> c.contentWidth, (c, v) -> c.contentWidth = v, 170, 280);
        enumeration(panel, "Display", binding, c -> c.display, (c, v) -> c.display = v);
        return panel;
    }
}
