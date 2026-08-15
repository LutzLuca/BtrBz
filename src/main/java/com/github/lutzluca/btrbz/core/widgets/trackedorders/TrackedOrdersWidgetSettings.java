package com.github.lutzluca.btrbz.core.widgets.trackedorders;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetConfigBinding;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetSettingsPanel;
import io.wispforest.owo.ui.core.UIComponent;

public final class TrackedOrdersWidgetSettings {
    private TrackedOrdersWidgetSettings() {}

    public static UIComponent create(WidgetConfigBinding<TrackedOrdersWidgetConfig> binding) {
        var panel = WidgetSettingsPanel.panel();

        WidgetSettingsPanel.integer(panel, "Widget width", binding, c -> c.contentWidth,
            (c, v) -> c.contentWidth = v, 200, 300,
            "Controls horizontal space without changing text or icon scale.");

        WidgetSettingsPanel.integer(panel, "Visible rows", binding, c -> c.visibleRows,
            (c, v) -> c.visibleRows = v, 1, 10,
            "Maximum rows shown before the order list scrolls.");

        WidgetSettingsPanel.enumeration(panel, "Density", binding, c -> c.layout, (c, v) -> c.layout = v,
            "Standard shows order identity and market position. Compact keeps only essential scan information.");

        WidgetSettingsPanel.enumeration(panel, "Sort order", binding, c -> c.sort, (c, v) -> c.sort = v,
            "Manual supports drag reordering. Newest and Status arrange orders automatically.");

        return panel;
    }
}
