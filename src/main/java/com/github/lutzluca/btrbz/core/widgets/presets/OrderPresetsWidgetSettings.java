package com.github.lutzluca.btrbz.core.widgets.presets;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetConfigBinding;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetSettingsPanel;
import io.wispforest.owo.ui.core.UIComponent;

public final class OrderPresetsWidgetSettings {
    private OrderPresetsWidgetSettings() {}

    public static UIComponent create(WidgetConfigBinding<OrderPresetsWidgetConfig> binding) {
        var panel = WidgetSettingsPanel.panel();

        WidgetSettingsPanel.integer(panel, "Widget width", binding, c -> c.contentWidth,
            (c, v) -> c.contentWidth = v, 40, 100,
            "Controls the width of the preset buttons without changing their scale.");

        WidgetSettingsPanel.integer(panel, "Visible presets", binding, c -> c.visibleRows,
            (c, v) -> c.visibleRows = v, 3, 7,
            "Maximum presets shown before the list scrolls.");

        WidgetSettingsPanel.bool(panel, "Clipboard preset", binding, c -> c.clipboard, (c, v) -> c.clipboard = v,
            "Offers the positive whole number currently copied to the clipboard.");

        WidgetSettingsPanel.bool(panel, "Show disabled presets", binding, c -> c.showDisabled,
            (c, v) -> c.showDisabled = v,
            "Keeps unavailable presets visible so their current availability can be inspected.");

        return panel;
    }
}
