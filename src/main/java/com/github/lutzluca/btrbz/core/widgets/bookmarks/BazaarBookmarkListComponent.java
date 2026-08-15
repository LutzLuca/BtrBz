package com.github.lutzluca.btrbz.core.widgets.bookmarks;

import com.github.lutzluca.btrbz.core.widgets.ui.BazaarStyles;
import com.github.lutzluca.btrbz.core.widgets.ui.ReorderableScrollListComponent;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetLayoutTokens;
import java.util.List;
import java.util.function.Consumer;

/** Bookmark-specific rows over one retained shared reorderable-list instance. */
final class BazaarBookmarkListComponent extends ReorderableScrollListComponent<String> {
    BazaarBookmarkListComponent() {
        super(
            BazaarBookmarkRowComponent.HEIGHT,
            WidgetLayoutTokens.LIST_GAP,
            true,
            BazaarStyles.SCROLLBAR,
            BazaarStyles.INSERTION,
            0,
            4,
            2);
    }

    void update(
        List<BookmarksWidgetData.Bookmark> bookmarks,
        BookmarksWidgetConfig options,
        boolean interactive,
        Consumer<BookmarksAction> actions
    ) {
        int viewportHeight = viewportHeight(options, bookmarks.size());
        boolean reserveScrollbarSpace = reserveScrollbarSpace(bookmarks.size(), viewportHeight);

        this.reconcileRows(
            bookmarks,
            BookmarksWidgetData.Bookmark::productId,
            (bookmark, index) -> new BazaarBookmarkRowComponent(
                this, bookmark, options, interactive, reserveScrollbarSpace, index, actions),
            (row, bookmark, index) -> row.update(
                bookmark, options, interactive, reserveScrollbarSpace, index, actions),
            viewportHeight,
            interactive,
            options.sort == BookmarksWidgetConfig.BookmarkSort.Manual);
    }

    static boolean reserveScrollbarSpace(int bookmarkCount, int viewportHeight) {
        return WidgetLayoutTokens.requiresScrollbar(
            BazaarBookmarkRowComponent.HEIGHT, bookmarkCount, viewportHeight);
    }

    private static int viewportHeight(BookmarksWidgetConfig options, int bookmarkCount) {
        return WidgetLayoutTokens.configuredListViewportHeight(
            BazaarBookmarkRowComponent.HEIGHT, bookmarkCount, options.visibleRows, true);
    }
}
