package com.github.lutzluca.btrbz.core.widgets.bookmarks;

public final class BookmarkDragController {
    private String activeProductId;
    private int startIndex;
    private int dropIndex;
    private boolean moved;

    public BookmarkDragController() {}

    boolean dragging() { return this.activeProductId != null; }
    boolean dragging(String productId) { return productId.equals(this.activeProductId); }
    int dropIndex() { return this.dropIndex; }

    void start(String productId, int index) {
        this.activeProductId = productId;
        this.startIndex = index;
        this.dropIndex = index;
        this.moved = false;
    }

    void markMoved() { this.moved = true; }
    void updateDropIndex(int dropIndex) { this.dropIndex = Math.max(0, dropIndex); }

    BookmarkDragResult finish() {
        var result = this.activeProductId == null
            ? null
            : new BookmarkDragResult(this.activeProductId, this.startIndex, this.dropIndex, this.moved);
        this.activeProductId = null;
        this.startIndex = 0;
        this.dropIndex = 0;
        this.moved = false;
        return result;
    }
}
