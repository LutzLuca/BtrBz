package com.github.lutzluca.btrbz.core.widgets;

import com.github.lutzluca.btrbz.widgets.framework.ui.WidgetLayoutTokens;
import com.github.lutzluca.btrbz.widgets.framework.ui.WidgetScrollListComponent;
import com.github.lutzluca.btrbz.widgets.framework.ui.WidgetScrollState;
import io.wispforest.owo.ui.base.BaseParentUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.ParentUIComponent;
import io.wispforest.owo.ui.core.Size;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/** Scrollable bookmark rows with manual-only insertion-boundary dragging. */
final class BazaarBookmarkListComponent extends BaseParentUIComponent {
    private static final int AUTO_SCROLL_THRESHOLD = 14;
    private static final double AUTO_SCROLL_STEP = 0.028;
    private final WidgetScrollListComponent scrollList;
    private final List<BazaarBookmarkRowComponent> rows = new ArrayList<>();
    private final List<UIComponent> children;
    private final boolean reorderable;
    private final BazaarData.BookmarkDragController drag;

    BazaarBookmarkListComponent(List<BazaarData.Bookmark> bookmarks,
            BazaarWidgetOptions.Bookmarks options, boolean interactive,
            WidgetScrollState scrollState, BazaarData.BookmarkDragController drag,
            Consumer<BazaarAction> actions) {
        super(Sizing.fill(100), Sizing.fixed(viewportHeight(options)));
        this.reorderable = interactive && options.sort() == BazaarWidgetOptions.BookmarkSort.MANUAL;
        this.drag = drag;
        for (int index = 0; index < bookmarks.size(); index++) {
            this.rows.add(new BazaarBookmarkRowComponent(
                bookmarks.get(index), options, interactive, index, drag, this::updateDropIndex, actions
            ));
        }
        this.scrollList = new WidgetScrollListComponent(
            this.rows, viewportHeight(options), WidgetLayoutTokens.LIST_GAP, interactive, scrollState,
            BazaarStyles.SCROLLBAR
        );
        this.children = Collections.singletonList(this.scrollList);
        this.allowOverflow(true);
    }

    private static int viewportHeight(BazaarWidgetOptions.Bookmarks options) {
        return WidgetLayoutTokens.listViewportHeight(
            BazaarBookmarkRowComponent.HEIGHT, options.visibleRows()
        );
    }

    @Override public void layout(Size space) {
        this.scrollList.inflate(this.calculateChildSpace(space));
        this.scrollList.mount(this, this.x, this.y);
    }
    @Override public List<UIComponent> children() { return this.children; }
    @Override public ParentUIComponent removeChild(UIComponent child) {
        throw new UnsupportedOperationException("Bookmark list owns its scroll container");
    }

    @Override public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        super.draw(graphics, mouseX, mouseY, partialTicks, delta);
        this.autoScroll(mouseX, mouseY);
        this.updateDropIndex(mouseY);
        this.drawChildren(graphics, mouseX, mouseY, partialTicks, delta, this.children);
        this.drawInsertionIndicator(graphics);
    }

    private void autoScroll(int mouseX, int mouseY) {
        if (!this.reorderable || !this.drag.dragging()) return;
        if (mouseX < this.scrollList.x() || mouseX > this.scrollList.x() + this.scrollList.width()) return;
        if (mouseY < this.scrollList.y() + AUTO_SCROLL_THRESHOLD) {
            this.scrollList.scrollByProgress(-AUTO_SCROLL_STEP);
        } else if (mouseY > this.scrollList.y() + this.scrollList.height() - AUTO_SCROLL_THRESHOLD) {
            this.scrollList.scrollByProgress(AUTO_SCROLL_STEP);
        }
    }

    private void updateDropIndex(int pointerY) {
        if (!this.reorderable || !this.drag.dragging()) return;
        int gap = this.rows.size();
        for (int index = 0; index < this.rows.size(); index++) {
            var row = this.rows.get(index);
            if (pointerY < row.y() + row.height() / 2) { gap = index; break; }
        }
        this.drag.updateDropIndex(gap);
    }

    private void drawInsertionIndicator(OwoUIGraphics graphics) {
        if (!this.reorderable || !this.drag.dragging() || this.rows.isEmpty()) return;
        int gap = Math.max(0, Math.min(this.drag.dropIndex(), this.rows.size()));
        int lineY = gap == 0 ? this.rows.getFirst().y() - 1
            : gap == this.rows.size() ? this.rows.getLast().y() + this.rows.getLast().height() + 1
            : this.rows.get(gap).y() - 1;
        int top = this.scrollList.y();
        int bottom = this.scrollList.y() + this.scrollList.height() - 1;
        if (lineY < top - 1 || lineY > bottom + 1) return;
        lineY = Math.max(top, Math.min(bottom, lineY));
        graphics.fill(this.scrollList.x() + 5, lineY,
            this.scrollList.x() + this.scrollList.width() - 7, lineY + 1, BazaarStyles.INSERTION);
    }
}
