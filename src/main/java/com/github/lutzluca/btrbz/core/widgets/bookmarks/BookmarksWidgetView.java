package com.github.lutzluca.btrbz.core.widgets.bookmarks;

import com.github.lutzluca.btrbz.core.widgets.ScrollOffsetView;
import com.github.lutzluca.btrbz.core.widgets.WidgetView;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarStyles;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import java.util.function.Consumer;

import static com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi.panel;
import static com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi.text;

final class BookmarksWidgetView implements
    WidgetView<BookmarksWidgetData.Snapshot, BookmarksWidgetConfig, BookmarksAction>,
    ScrollOffsetView {
    private final FlowLayout root = panel(1);
    private final LabelComponent title = text("Bookmarks", BazaarStyles.PRIMARY_TEXT);

    private final BazaarBookmarkListComponent list = new BazaarBookmarkListComponent();

    private final BookmarkAdditionTracker additions = new BookmarkAdditionTracker();

    BookmarksWidgetView() {
        this.root.child(this.title);
        this.root.child(this.list);
    }

    @Override
    public UIComponent root() {
        return this.root;
    }

    @Override
    public double scrollOffset() {
        return this.list.scrollOffset();
    }

    @Override
    public void scrollOffset(double offset) {
        this.list.scrollOffset(offset);
    }

    @Override
    public void update(
        BookmarksWidgetData.Snapshot data,
        BookmarksWidgetConfig config,
        WidgetSession session,
        Consumer<BookmarksAction> actions
    ) {
        this.root.horizontalSizing(Sizing.fixed(config.contentWidth));

        boolean added = this.additions.update(
            data.bookmarks().stream().map(BookmarksWidgetData.Bookmark::productId).toList()
        );

        this.list.update(
            BookmarksWidget.sortedBookmarks(data.bookmarks(), config.sort),
            config,
            true,
            actions
        );

        if (added) {
            this.list.flashScrollbar();
        }
    }
}
