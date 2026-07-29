package com.github.lutzluca.btrbz.core.widgets.bookmarks;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetConfigBinding;
import io.wispforest.owo.ui.core.UIComponent;
import static com.github.lutzluca.btrbz.core.widgets.ui.WidgetSettingsPanel.*;

public final class BookmarksWidgetSettings {
    private BookmarksWidgetSettings() {}
    public static UIComponent create(WidgetConfigBinding<BookmarksWidgetConfig> binding) {
        var panel = panel();
        integer(panel, "Content width", binding, c -> c.contentWidth, (c, v) -> c.contentWidth = v, 180, 300);
        integer(panel, "Visible rows", binding, c -> c.visibleRows, (c, v) -> c.visibleRows = v, 1, 12);
        bool(panel, "Fit to content", binding, c -> c.fitToContent, (c, v) -> c.fitToContent = v);
        enumeration(panel, "Sort order", binding, c -> c.sort, (c, v) -> c.sort = v);
        bool(panel, "Hide when no bookmarks", binding, c -> c.hideWhenEmpty, (c, v) -> c.hideWhenEmpty = v);
        bool(panel, "Show ItemStacks", binding, c -> c.showItems, (c, v) -> c.showItems = v);
        bool(panel, "Show order indicators", binding, c -> c.showIndicators, (c, v) -> c.showIndicators = v);
        bool(panel, "Abbreviate Enchanted", binding, c -> c.abbreviateEnchanted, (c, v) -> c.abbreviateEnchanted = v);
        return panel;
    }
}
