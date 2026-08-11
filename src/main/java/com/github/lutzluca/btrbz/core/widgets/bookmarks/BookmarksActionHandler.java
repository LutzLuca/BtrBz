package com.github.lutzluca.btrbz.core.widgets.bookmarks;

import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import com.github.lutzluca.btrbz.core.widgets.WidgetActionHandler;

public final class BookmarksActionHandler implements WidgetActionHandler<BookmarksAction> {
    private final BookmarkComponent bookmarks;

    public BookmarksActionHandler(BookmarkComponent bookmarks) {
        this.bookmarks = bookmarks;
    }

    @Override
    public void handle(BookmarksAction action, WidgetSession source, WidgetSession current) {
        if (!source.sameWorkflow(current) || !current.inBazaarContainer()) {
            return;
        }

        switch (action) {
            case BookmarksAction.Open open -> {
                if (this.bookmarks.contains(open.productId())) {
                    this.bookmarks.open(open.productId());
                }
            }

            case BookmarksAction.Remove remove -> {
                if (this.bookmarks.contains(remove.productId())) {
                    this.bookmarks.remove(remove.productId());
                }
            }

            case BookmarksAction.Reorder reorder -> {
                if (ConfigManager.get().widgets.bookmarks.sort == BookmarksWidgetConfig.BookmarkSort.Manual
                    && this.bookmarks.contains(reorder.productId())) {
                    this.bookmarks.reorder(reorder.productId(), reorder.insertionIndex());
                }
            }
        }
    }
}
