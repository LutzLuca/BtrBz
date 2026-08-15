package com.github.lutzluca.btrbz.core.widgets.orderbook;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetConfigBinding;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetSettingsPanel;
import io.wispforest.owo.ui.core.UIComponent;

public final class OrderBookPriceWidgetSettings {
    private OrderBookPriceWidgetSettings() {}

    public static UIComponent create(WidgetConfigBinding<OrderBookPriceWidgetConfig> binding) {
        var panel = WidgetSettingsPanel.panel();

        WidgetSettingsPanel.integer(panel, "Widget width", binding, c -> c.contentWidth,
            (c, v) -> c.contentWidth = v, 240, 440,
            "Controls the total width used when both price sides are visible.");

        WidgetSettingsPanel.integer(panel, "Levels per side", binding, c -> c.visibleRows,
            (c, v) -> c.visibleRows = v, 1, 10,
            "Number of selectable price levels visible for each displayed side.");

        WidgetSettingsPanel.bool(panel, "Show order count", binding, c -> c.showOrderCount,
            (c, v) -> c.showOrderCount = v,
            "Adds the number of Bazaar orders contributing to each price level.");

        WidgetSettingsPanel.enumeration(panel, "Sides", binding, c -> c.sideDisplay,
            (c, v) -> c.sideDisplay = v,
            "Relevant follows the current buy or sell workflow. Both keeps both sides visible.");

        return panel;
    }
}
