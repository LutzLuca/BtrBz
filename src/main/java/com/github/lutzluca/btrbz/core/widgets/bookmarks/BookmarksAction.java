package com.github.lutzluca.btrbz.core.widgets.bookmarks;

public sealed interface BookmarksAction permits BookmarksAction.Open, BookmarksAction.Remove,
    BookmarksAction.Reorder {
    record Open(String productId) implements BookmarksAction {}

    record Remove(String productId) implements BookmarksAction {}

    record Reorder(String productId, int insertionIndex) implements BookmarksAction {}
}
