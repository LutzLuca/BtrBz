package com.github.lutzluca.btrbz.core.widgets.orderbook;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetConfigBinding;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetSettingsPanel;
import io.wispforest.owo.ui.core.UIComponent;

public final class OrderBookWidgetSettings {
    private OrderBookWidgetSettings() {}

    public static UIComponent create(WidgetConfigBinding<OrderBookWidgetConfig> binding) {
        var panel = WidgetSettingsPanel.panel();

        WidgetSettingsPanel.integer(panel, "Widget width", binding, c -> c.contentWidth,
            (c, v) -> c.contentWidth = v, 220, 440,
            "Controls the total width shared by the visible order-book sides.");

        WidgetSettingsPanel.integer(panel, "Levels per side", binding, c -> c.visibleRows,
            (c, v) -> c.visibleRows = v, 1, 10,
            "Number of price levels visible on each side of the order book.");

        WidgetSettingsPanel.enumeration(panel, "Layout", binding, c -> c.layout, (c, v) -> c.layout = v,
            "Shows both sides together or gives one side the full widget width.");

        WidgetSettingsPanel.enumeration(panel, "Volume format", binding, c -> c.numberStyle,
            (c, v) -> c.numberStyle = v,
            "Exact keeps full item counts. Compact abbreviates large volumes.");

        WidgetSettingsPanel.bool(panel, "Show order count", binding, c -> c.showOrderCount,
            (c, v) -> c.showOrderCount = v,
            "Adds the number of Bazaar orders contributing to each price level.");

        return panel;
    }
}
