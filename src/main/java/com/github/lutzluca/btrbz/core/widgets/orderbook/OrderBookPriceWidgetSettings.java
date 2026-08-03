package com.github.lutzluca.btrbz.core.widgets.orderbook;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetConfigBinding;
import io.wispforest.owo.ui.core.UIComponent;
import static com.github.lutzluca.btrbz.core.widgets.ui.WidgetSettingsPanel.*;

public final class OrderBookPriceWidgetSettings {
    private OrderBookPriceWidgetSettings() {}
    public static UIComponent create(WidgetConfigBinding<OrderBookPriceWidgetConfig> binding) {
        var panel = panel();
        integer(panel, "Widget width", binding, c -> c.contentWidth, (c, v) -> c.contentWidth = v, 240, 440);
        integer(panel, "Levels per side", binding, c -> c.visibleRows, (c, v) -> c.visibleRows = v, 1, 10);
        bool(panel, "Show order count", binding, c -> c.showOrderCount, (c, v) -> c.showOrderCount = v);
        enumeration(panel, "Sides", binding, c -> c.sideDisplay, (c, v) -> c.sideDisplay = v);
        return panel;
    }
}
