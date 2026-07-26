package com.github.lutzluca.btrbz.core.widgets.bookmarks;

import com.github.lutzluca.btrbz.core.widgets.action.BazaarAction;
import com.github.lutzluca.btrbz.core.widgets.config.BazaarWidgetOptions;
import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarStyles;
import com.github.lutzluca.btrbz.widgets.framework.ui.WidgetScrollState;
import io.wispforest.owo.ui.core.UIComponent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import static com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi.*;

public final class BookmarksWidget {
    private BookmarksWidget() {}

    public static UIComponent render(
        List<BazaarWidgetViewData.Bookmark> source,
        BazaarWidgetOptions.Bookmarks options,
        boolean interactive,
        WidgetScrollState scrollState,
        BookmarkDragController drag,
        Consumer<BazaarAction> actions
    ) {
        var data = sortedBookmarks(source, options.sort());
        var root = panel(options.contentWidth());
        root.child(text("Bookmarks", BazaarStyles.PRIMARY_TEXT));
        root.child(new BazaarBookmarkListComponent(data, options, interactive, scrollState, drag, actions));
        return root;
    }

    public static List<BazaarWidgetViewData.Bookmark> sortedBookmarks(
        List<BazaarWidgetViewData.Bookmark> source,
        BazaarWidgetOptions.BookmarkSort sort
    ) {
        var data = new ArrayList<>(source);
        if (sort == BazaarWidgetOptions.BookmarkSort.Alphabetical) {
            data.sort(Comparator.comparing(
                BazaarWidgetViewData.Bookmark::productName,
                String.CASE_INSENSITIVE_ORDER
            ));
        }
        return List.copyOf(data);
    }
}
