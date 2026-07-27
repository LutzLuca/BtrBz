package com.github.lutzluca.btrbz.core.widgets.ordervalue;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetConfigBinding;
import io.wispforest.owo.ui.core.UIComponent;
import static com.github.lutzluca.btrbz.core.widgets.ui.WidgetSettingsPanel.*;

public final class OrderValueWidgetSettings {
    private OrderValueWidgetSettings() {}
    public static UIComponent create(WidgetConfigBinding<OrderValueWidgetConfig> binding) {
        var panel = panel();
        integer(panel, "Content width", binding, c -> c.contentWidth, (c, v) -> c.contentWidth = v, 170, 280);
        enumeration(panel, "Display", binding, c -> c.display, (c, v) -> c.display = v);
        enumeration(panel, "Number format", binding, c -> c.numberStyle, (c, v) -> c.numberStyle = v);
        bool(panel, "Show coins suffix", binding, c -> c.showCoinsSuffix, (c, v) -> c.showCoinsSuffix = v);
        enumeration(panel, "Colors", binding, c -> c.colorMode, (c, v) -> c.colorMode = v);
        bool(panel, "Buy-order coins", binding, c -> c.buyLocked, (c, v) -> c.buyLocked = v);
        bool(panel, "Buy-order items", binding, c -> c.buyItems, (c, v) -> c.buyItems = v);
        bool(panel, "Sell claimable", binding, c -> c.sellClaimable, (c, v) -> c.sellClaimable = v);
        bool(panel, "Sell pending", binding, c -> c.sellPending, (c, v) -> c.sellPending = v);
        return panel;
    }
}
