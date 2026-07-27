package com.github.lutzluca.btrbz.core.widgets.orderbook;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetConfigBinding;
import io.wispforest.owo.ui.core.UIComponent;
import static com.github.lutzluca.btrbz.core.widgets.ui.WidgetSettingsPanel.*;

public final class OrderBookPriceWidgetSettings {
    private OrderBookPriceWidgetSettings() {}
    public static UIComponent create(WidgetConfigBinding<OrderBookPriceWidgetConfig> binding) {
        var panel = panel();
        integer(panel, "Content width", binding, c -> c.contentWidth, (c, v) -> c.contentWidth = v, 240, 360);
        integer(panel, "Levels per side", binding, c -> c.visibleRows, (c, v) -> c.visibleRows = v, 1, 10);
        bool(panel, "Show buy offers", binding, c -> c.showBuy, (c, v) -> c.showBuy = v);
        bool(panel, "Show sell offers", binding, c -> c.showSell, (c, v) -> c.showSell = v);
        bool(panel, "Show amounts", binding, c -> c.showAmounts, (c, v) -> c.showAmounts = v);
        bool(panel, "Show order count", binding, c -> c.showOrderCount, (c, v) -> c.showOrderCount = v);
        bool(panel, "Show product header", binding, c -> c.showHeader, (c, v) -> c.showHeader = v);
        bool(panel, "Show ItemStack", binding, c -> c.showItem, (c, v) -> c.showItem = v);
        enumeration(panel, "Sides", binding, c -> c.sideDisplay, (c, v) -> c.sideDisplay = v);
        return panel;
    }
}
