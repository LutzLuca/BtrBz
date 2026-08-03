package com.github.lutzluca.btrbz.core.widgets.presets;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetConfigBinding;
import io.wispforest.owo.ui.core.UIComponent;
import static com.github.lutzluca.btrbz.core.widgets.ui.WidgetSettingsPanel.*;

public final class OrderPresetsWidgetSettings {
    private OrderPresetsWidgetSettings() {}
    public static UIComponent create(WidgetConfigBinding<OrderPresetsWidgetConfig> binding) {
        var panel = panel();
        integer(panel, "Widget width", binding, c -> c.contentWidth, (c, v) -> c.contentWidth = v, 40, 100);
        integer(panel, "Visible presets", binding, c -> c.visibleRows, (c, v) -> c.visibleRows = v, 3, 7);
        bool(panel, "Clipboard preset", binding, c -> c.clipboard, (c, v) -> c.clipboard = v);
        bool(panel, "Show disabled presets", binding, c -> c.showDisabled, (c, v) -> c.showDisabled = v);
        return panel;
    }
}
