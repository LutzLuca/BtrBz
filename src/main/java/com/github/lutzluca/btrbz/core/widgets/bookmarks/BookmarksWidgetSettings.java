package com.github.lutzluca.btrbz.core.widgets.bookmarks;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetConfigBinding;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetSettingsPanel;
import io.wispforest.owo.ui.core.UIComponent;

public final class BookmarksWidgetSettings {
    private BookmarksWidgetSettings() {}

    public static UIComponent create(WidgetConfigBinding<BookmarksWidgetConfig> binding) {
        var panel = WidgetSettingsPanel.panel();

        WidgetSettingsPanel.integer(panel, "Widget width", binding, c -> c.contentWidth,
            (c, v) -> c.contentWidth = v, 200, 300,
            "Controls horizontal space without changing text or icon scale.");

        WidgetSettingsPanel.integer(panel, "Visible rows", binding, c -> c.visibleRows,
            (c, v) -> c.visibleRows = v, 1, 12,
            "Maximum bookmarks shown before the list scrolls.");

        WidgetSettingsPanel.enumeration(panel, "Sort order", binding, c -> c.sort, (c, v) -> c.sort = v,
            "Manual supports drag reordering. Alphabetical sorts by the displayed product name.");

        return panel;
    }
}
