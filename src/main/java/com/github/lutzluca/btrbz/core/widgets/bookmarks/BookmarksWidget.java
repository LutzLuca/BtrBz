package com.github.lutzluca.btrbz.core.widgets.bookmarks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class BookmarksWidget {
    private BookmarksWidget() {}

    public static List<BookmarksWidgetData.Bookmark> sortedBookmarks(
        List<BookmarksWidgetData.Bookmark> source,
        BookmarksWidgetConfig.BookmarkSort sort
    ) {
        var data = new ArrayList<>(source);
        if (sort == BookmarksWidgetConfig.BookmarkSort.Alphabetical) {
            data.sort(Comparator.comparing(
                BookmarksWidgetData.Bookmark::productName,
                String.CASE_INSENSITIVE_ORDER));
        }
        return List.copyOf(data);
    }
}
