package com.github.lutzluca.btrbz.core.widgets.ui;

import io.wispforest.owo.ui.base.BaseParentUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.ParentUIComponent;
import io.wispforest.owo.ui.core.Size;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.jetbrains.annotations.Nullable;

/** Retained keyed rows plus shared scrolling, dragging, insertion, and auto-scroll mechanics. */
public class ReorderableScrollListComponent<K> extends BaseParentUIComponent {
    private static final int AUTO_SCROLL_THRESHOLD = 14;
    private static final double AUTO_SCROLL_STEP = 0.028;
    private static final long DRAG_HOLD_MILLIS = 120;

    private final WidgetScrollListComponent scrollList;
    private final List<UIComponent> children;
    private final RetainedRows<K, UIComponent> retainedRows = new RetainedRows<>();
    private final List<UIComponent> rows = new ArrayList<>();
    private final List<UIComponent> rowView = Collections.unmodifiableList(this.rows);
    private final int insertionColor;
    private final int insertionOutlineColor;
    private final int insertionInset;
    private final int insertionHeight;
    private boolean interactive;
    private boolean reorderable;
    private @Nullable K pendingDragKey;
    private @Nullable K draggedKey;
    private long dragReadyAt;
    private int dragStartIndex;
    private int dropIndex;
    private boolean pendingDragMoved;
    private boolean dragMoved;

    protected ReorderableScrollListComponent(
        int viewportHeight,
        int rowGap,
        boolean interactive,
        int scrollbarColor,
        int insertionColor,
        int insertionOutlineColor,
        int insertionInset,
        int insertionHeight
    ) {
        super(Sizing.fill(100), Sizing.fixed(viewportHeight));
        this.scrollList = new WidgetScrollListComponent(
            viewportHeight, rowGap, interactive, scrollbarColor
        );
        this.children = Collections.singletonList(this.scrollList);
        this.insertionColor = insertionColor;
        this.insertionOutlineColor = insertionOutlineColor;
        this.insertionInset = insertionInset;
        this.insertionHeight = insertionHeight;
        this.allowOverflow(true);
    }

    @SuppressWarnings("unchecked")
    protected final <M, C extends UIComponent> void reconcileRows(
        List<M> models,
        Function<M, K> keyExtractor,
        BiFunction<M, Integer, C> factory,
        RowUpdater<C, M> updater,
        int viewportHeight,
        boolean interactive,
        boolean reorderable
    ) {
        this.dirty = true;
        var ordered = this.retainedRows.reconcile(
            models,
            keyExtractor,
            (model, index) -> factory.apply(model, index),
            (row, model, index) -> updater.update((C) row, model, index)
        );
        if ((this.pendingDragKey != null && !this.retainedRows.contains(this.pendingDragKey))
            || (this.draggedKey != null && !this.retainedRows.contains(this.draggedKey))) {
            this.cancelDrag();
        }
        this.rows.clear();
        this.rows.addAll(ordered);
        this.interactive = interactive;
        this.reorderable = interactive && reorderable;
        if (!this.reorderable) this.cancelDrag();
        this.verticalSizing(Sizing.fixed(Math.max(1, viewportHeight)));
        this.scrollList.updateRows(this.rows, viewportHeight, interactive);
        this.updateLayout();
    }

    @Override
    public void layout(Size space) {
        this.scrollList.inflate(this.calculateChildSpace(space));
        this.scrollList.mount(this, this.x, this.y);
    }

    @Override
    public List<UIComponent> children() {
        return this.children;
    }

    @Override
    public ParentUIComponent removeChild(UIComponent child) {
        throw new UnsupportedOperationException("Reorderable list owns its scroll container");
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        super.draw(graphics, mouseX, mouseY, partialTicks, delta);
        this.activatePendingDragIfReady(System.currentTimeMillis());
        this.autoScroll(mouseX, mouseY);
        this.updateDropIndex(mouseY);
        this.beforeChildrenDraw(mouseX, mouseY);
        this.drawChildren(graphics, mouseX, mouseY, partialTicks, delta, this.children);
        this.drawInsertionIndicator(graphics);
    }

    protected void beforeChildrenDraw(int mouseX, int mouseY) {}

    protected final WidgetScrollListComponent scrollList() {
        return this.scrollList;
    }

    protected final List<UIComponent> rows() {
        return this.rowView;
    }

    public final double scrollOffset() {
        return this.scrollList.scrollOffset();
    }

    public final void scrollOffset(double offset) {
        this.scrollList.scrollOffset(offset);
    }

    public final boolean beginDrag(K key, int index) {
        if (!this.reorderable || !this.retainedRows.contains(key)) return false;
        this.pendingDragKey = key;
        this.draggedKey = null;
        this.dragReadyAt = System.currentTimeMillis() + DRAG_HOLD_MILLIS;
        this.dragStartIndex = Math.max(0, index);
        this.dropIndex = this.dragStartIndex;
        this.pendingDragMoved = false;
        this.dragMoved = false;
        return true;
    }

    public final boolean dragging(K key) {
        return this.draggedKey != null && this.draggedKey.equals(key);
    }

    public final boolean trackingDrag(K key) {
        return this.dragging(key) || this.pendingDragKey != null && this.pendingDragKey.equals(key);
    }

    public final boolean hoverSuppressed() {
        return this.pendingDragKey != null
            || this.draggedKey != null
            || this.scrollList.scrollbarOwnsMouseCapture();
    }

    public final void dragPointer(int pointerY) {
        if (this.pendingDragKey != null) {
            this.pendingDragMoved = true;
            this.activatePendingDragIfReady(System.currentTimeMillis());
        }
        if (this.draggedKey == null) return;
        this.dragMoved = true;
        this.updateDropIndex(pointerY);
    }

    public final Optional<ReorderResult<K>> finishDrag() {
        K key = this.draggedKey != null ? this.draggedKey : this.pendingDragKey;
        if (key == null) return Optional.empty();
        var result = new ReorderResult<>(
            key, this.dragStartIndex, this.dropIndex, this.dragMoved
        );
        this.cancelDrag();
        return Optional.of(result);
    }

    private void cancelDrag() {
        this.pendingDragKey = null;
        this.draggedKey = null;
        this.dragReadyAt = 0;
        this.dragStartIndex = 0;
        this.dropIndex = 0;
        this.pendingDragMoved = false;
        this.dragMoved = false;
    }

    private void activatePendingDragIfReady(long now) {
        if (this.pendingDragKey == null || now < this.dragReadyAt) return;
        this.draggedKey = this.pendingDragKey;
        this.pendingDragKey = null;
        this.dragMoved = this.pendingDragMoved;
        this.pendingDragMoved = false;
    }

    private void updateDropIndex(int pointerY) {
        if (!this.reorderable || this.draggedKey == null) return;
        int gap = this.rows.size();
        for (int index = 0; index < this.rows.size(); index++) {
            var row = this.rows.get(index);
            if (pointerY < row.y() + row.height() / 2) {
                gap = index;
                break;
            }
        }
        this.dropIndex = gap;
    }

    private void autoScroll(int mouseX, int mouseY) {
        if (!this.reorderable || this.draggedKey == null) return;
        if (mouseX < this.scrollList.x() || mouseX > this.scrollList.x() + this.scrollList.width()) return;
        if (mouseY < this.scrollList.y() + AUTO_SCROLL_THRESHOLD) {
            this.scrollList.scrollByProgress(-AUTO_SCROLL_STEP);
        } else if (mouseY > this.scrollList.y() + this.scrollList.height() - AUTO_SCROLL_THRESHOLD) {
            this.scrollList.scrollByProgress(AUTO_SCROLL_STEP);
        }
    }

    private void drawInsertionIndicator(OwoUIGraphics graphics) {
        if (!this.reorderable || this.draggedKey == null || this.rows.isEmpty()) return;
        int gap = Math.max(0, Math.min(this.dropIndex, this.rows.size()));
        int lineY = gap == 0 ? this.rows.getFirst().y() - 1
            : gap == this.rows.size() ? this.rows.getLast().y() + this.rows.getLast().height()
            : this.rows.get(gap).y() - 1;
        int viewportTop = this.scrollList.y();
        int viewportBottom = this.scrollList.y() + this.scrollList.height() - 1;
        var visibleLineY = visibleInsertionIndicatorY(lineY, viewportTop, viewportBottom);
        if (visibleLineY.isEmpty()) return;
        lineY = visibleLineY.getAsInt();
        int left = this.scrollList.x() + this.insertionInset;
        int right = this.scrollList.x() + this.scrollList.width() - this.insertionInset - 2;
        if (this.insertionOutlineColor != 0) {
            graphics.fill(left - 2, Math.max(viewportTop, lineY - 1), right + 2,
                Math.min(viewportBottom + 1, lineY + 3), this.insertionOutlineColor);
        }
        graphics.fill(left, lineY, right,
            Math.min(viewportBottom + 1, lineY + this.insertionHeight), this.insertionColor);
    }

    public static OptionalInt visibleInsertionIndicatorY(int lineY, int viewportTop, int viewportBottom) {
        if (lineY < viewportTop - 1 || lineY > viewportBottom + 1) return OptionalInt.empty();
        return OptionalInt.of(Math.max(viewportTop, Math.min(viewportBottom, lineY)));
    }

    @FunctionalInterface
    protected interface RowUpdater<C extends UIComponent, M> {
        void update(C component, M model, int index);
    }

    public record ReorderResult<K>(K key, int startIndex, int dropIndex, boolean moved) {}
}
