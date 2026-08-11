package com.github.lutzluca.btrbz.core.widgets.bookmarks;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetConfigBinding;
import io.wispforest.owo.ui.core.UIComponent;

import static com.github.lutzluca.btrbz.core.widgets.ui.WidgetSettingsPanel.*;

public final class BookmarksWidgetSettings {
    private BookmarksWidgetSettings() {
    }

    public static UIComponent create(WidgetConfigBinding<BookmarksWidgetConfig> binding) {
        var panel = panel();

        integer(panel, "Widget width", binding, c -> c.contentWidth, (c, v) -> c.contentWidth = v, 200, 300,
            "Controls horizontal space without changing text or icon scale.");

        integer(panel, "Visible rows", binding, c -> c.visibleRows, (c, v) -> c.visibleRows = v, 1, 12,
            "Maximum bookmarks shown before the list scrolls.");

        enumeration(panel, "Sort order", binding, c -> c.sort, (c, v) -> c.sort = v,
            "Manual supports drag reordering. Alphabetical sorts by the displayed product name.");

        return panel;
    }
}
