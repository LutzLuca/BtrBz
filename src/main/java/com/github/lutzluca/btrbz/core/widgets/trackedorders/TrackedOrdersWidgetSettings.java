package com.github.lutzluca.btrbz.core.widgets.trackedorders;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetConfigBinding;
import io.wispforest.owo.ui.core.UIComponent;
import static com.github.lutzluca.btrbz.core.widgets.ui.WidgetSettingsPanel.*;

public final class TrackedOrdersWidgetSettings {
    private TrackedOrdersWidgetSettings() {}
    public static UIComponent create(WidgetConfigBinding<TrackedOrdersWidgetConfig> binding) {
        var panel = panel();
        integer(panel, "Widget width", binding, c -> c.contentWidth, (c, v) -> c.contentWidth = v, 180, 320);
        integer(panel, "Visible rows", binding, c -> c.visibleRows, (c, v) -> c.visibleRows = v, 1, 10);
        enumeration(panel, "Density", binding, c -> c.layout, (c, v) -> c.layout = v);
        enumeration(panel, "Sort order", binding, c -> c.sort, (c, v) -> c.sort = v);
        return panel;
    }
}
