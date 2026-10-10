package com.github.lutzluca.btrbz.core.widgets.orderbook;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetConfigBinding;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetSettingsPanel;
import io.wispforest.owo.ui.core.UIComponent;

public final class OrderBookWidgetSettings {
    private OrderBookWidgetSettings() {}

    public static UIComponent create(WidgetConfigBinding<OrderBookWidgetConfig> binding) {
        var panel = WidgetSettingsPanel.panel();

        WidgetSettingsPanel.integer(panel, "Widget width", binding, c -> c.contentWidth,
            (c, v) -> c.contentWidth = v, OrderBookWidgetConfig.MIN_CONTENT_WIDTH, 640,
            "Preferred width shared by both sides. Exact prices and counts can require more space.");

        WidgetSettingsPanel.integer(panel, "Levels per side", binding, c -> c.visibleRows,
            (c, v) -> c.visibleRows = v, 1, 10,
            "Visible levels per side. Scroll for more. Bar scaling includes off-screen levels.");

        WidgetSettingsPanel.enumeration(panel, "Depth mode", binding, c -> c.depthMode, (c, v) -> c.depthMode = v,
            "Cumulative shows the total including better price levels. Relative shows a quantity bar at each "
                + "exact price. Hidden removes bars and totals. Bars share one scale across all supplied levels.");

        WidgetSettingsPanel.bool(panel, "Show orders column", binding, c -> c.showOrderCount,
            (c, v) -> c.showOrderCount = v,
            "Shows the number of orders at each exact price beside Quantity. Also available in row tooltips.");

        WidgetSettingsPanel.enumeration(panel, "Scrolling", binding, c -> c.scrolling, (c, v) -> c.scrolling = v,
            "Independent scrolls the side under the pointer. Together scrolls both sides to the same row position.");

        return panel;
    }
}
