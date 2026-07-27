package com.github.lutzluca.btrbz.core.widgets.pricedifference;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetConfigBinding;
import io.wispforest.owo.ui.core.UIComponent;
import static com.github.lutzluca.btrbz.core.widgets.ui.WidgetSettingsPanel.*;

public final class PriceDifferenceWidgetSettings {
    private PriceDifferenceWidgetSettings() {}
    public static UIComponent create(WidgetConfigBinding<PriceDifferenceWidgetConfig> binding) {
        var panel = panel();
        integer(panel, "Content width", binding, c -> c.contentWidth, (c, v) -> c.contentWidth = v, 150, 300);
        enumeration(panel, "Display", binding, c -> c.display, (c, v) -> c.display = v);
        enumeration(panel, "Number format", binding, c -> c.numberStyle, (c, v) -> c.numberStyle = v);
        bool(panel, "Show ItemStack", binding, c -> c.showItems, (c, v) -> c.showItems = v);
        bool(panel, "Show product name", binding, c -> c.showProduct, (c, v) -> c.showProduct = v);
        return panel;
    }
}
