package com.github.lutzluca.btrbz.core.widgets.trackedorders;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetConfigBinding;
import io.wispforest.owo.ui.core.UIComponent;
import static com.github.lutzluca.btrbz.core.widgets.ui.WidgetSettingsPanel.*;

public final class TrackedOrdersWidgetSettings {
    private TrackedOrdersWidgetSettings() {}
    public static UIComponent create(WidgetConfigBinding<TrackedOrdersWidgetConfig> binding) {
        var panel = panel();
        integer(panel, "Widget width", binding, c -> c.contentWidth, (c, v) -> c.contentWidth = v, 180, 320,
            "Controls horizontal space without changing text or icon scale.");
        integer(panel, "Visible rows", binding, c -> c.visibleRows, (c, v) -> c.visibleRows = v, 1, 10,
            "Maximum rows shown before the order list scrolls.");
        enumeration(panel, "Density", binding, c -> c.layout, (c, v) -> c.layout = v,
            "Standard shows order identity and market position. Compact keeps only essential scan information.");
        enumeration(panel, "Sort order", binding, c -> c.sort, (c, v) -> c.sort = v,
            "Manual supports drag reordering. Newest and Status arrange orders automatically.");
        return panel;
    }
}
