package com.github.lutzluca.btrbz.core.widgets.orderbook;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetConfigBinding;
import io.wispforest.owo.ui.core.UIComponent;
import static com.github.lutzluca.btrbz.core.widgets.ui.WidgetSettingsPanel.*;

public final class OrderBookWidgetSettings {
    private OrderBookWidgetSettings() {}
    public static UIComponent create(WidgetConfigBinding<OrderBookWidgetConfig> binding) {
        var panel = panel();
        integer(panel, "Widget width", binding, c -> c.contentWidth, (c, v) -> c.contentWidth = v, 220, 440);
        integer(panel, "Levels per side", binding, c -> c.visibleRows, (c, v) -> c.visibleRows = v, 1, 10);
        enumeration(panel, "Layout", binding, c -> c.layout, (c, v) -> c.layout = v);
        enumeration(panel, "Volume format", binding, c -> c.numberStyle, (c, v) -> c.numberStyle = v);
        bool(panel, "Show order count", binding, c -> c.showOrderCount, (c, v) -> c.showOrderCount = v);
        return panel;
    }
}
