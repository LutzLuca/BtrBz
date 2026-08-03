package com.github.lutzluca.btrbz.core.widgets.bookmarks;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetConfigBinding;
import io.wispforest.owo.ui.core.UIComponent;
import static com.github.lutzluca.btrbz.core.widgets.ui.WidgetSettingsPanel.*;

public final class BookmarksWidgetSettings {
    private BookmarksWidgetSettings() {}
    public static UIComponent create(WidgetConfigBinding<BookmarksWidgetConfig> binding) {
        var panel = panel();
        integer(panel, "Widget width", binding, c -> c.contentWidth, (c, v) -> c.contentWidth = v, 180, 300);
        integer(panel, "Visible rows", binding, c -> c.visibleRows, (c, v) -> c.visibleRows = v, 1, 12);
        enumeration(panel, "Sort order", binding, c -> c.sort, (c, v) -> c.sort = v);
        return panel;
    }
}
