package com.github.lutzluca.btrbz.core.widgets.pricedifference;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetConfigBinding;
import io.wispforest.owo.ui.core.UIComponent;
import static com.github.lutzluca.btrbz.core.widgets.ui.WidgetSettingsPanel.*;

public final class PriceDifferenceWidgetSettings {
    private PriceDifferenceWidgetSettings() {}
    public static UIComponent create(WidgetConfigBinding<PriceDifferenceWidgetConfig> binding) {
        var panel = panel();
        integer(panel, "Widget width", binding, c -> c.contentWidth, (c, v) -> c.contentWidth = v, 150, 300);
        return panel;
    }
}
