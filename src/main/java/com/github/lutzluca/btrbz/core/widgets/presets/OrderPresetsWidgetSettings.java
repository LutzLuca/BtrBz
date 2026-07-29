package com.github.lutzluca.btrbz.core.widgets.presets;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetConfigBinding;
import io.wispforest.owo.ui.core.UIComponent;
import static com.github.lutzluca.btrbz.core.widgets.ui.WidgetSettingsPanel.*;

public final class OrderPresetsWidgetSettings {
    private OrderPresetsWidgetSettings() {}
    public static UIComponent create(WidgetConfigBinding<OrderPresetsWidgetConfig> binding) {
        var panel = panel();
        integer(panel, "Content width", binding, c -> c.contentWidth, (c, v) -> c.contentWidth = v, 24, 50);
        integer(panel, "Visible presets", binding, c -> c.visibleRows, (c, v) -> c.visibleRows = v, 3, 7);
        bool(panel, "Maximum preset", binding, c -> c.maximum, (c, v) -> c.maximum = v);
        bool(panel, "Clipboard preset", binding, c -> c.clipboard, (c, v) -> c.clipboard = v);
        bool(panel, "Show disabled presets", binding, c -> c.showDisabled, (c, v) -> c.showDisabled = v);
        bool(panel, "Show tooltips", binding, c -> c.showTooltips, (c, v) -> c.showTooltips = v);
        return panel;
    }
}
