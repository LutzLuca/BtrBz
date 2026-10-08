package com.github.lutzluca.btrbz.core.widgets.bookmarks;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetFrameConfig;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import com.github.lutzluca.btrbz.core.widgets.layout.WidgetPlacement;
import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class BookmarksWidgetConfig {
    public enum BookmarkSort {
        Manual, Alphabetical
    }

    public WidgetFrameConfig frame = new WidgetFrameConfig(WidgetPlacement.topLeft(0.145, 0.516));
    public int contentWidth = 200;
    public int visibleRows = 5;
    public BookmarkSort sort = BookmarkSort.Manual;

    @SerializedName("items")
    public List<BookmarkedProduct> products = new ArrayList<>();

    public static void resetPreferences(BookmarksWidgetConfig current, BookmarksWidgetConfig defaults) {
        current.contentWidth = defaults.contentWidth;
        current.visibleRows = defaults.visibleRows;
        current.sort = defaults.sort;
    }

    public record BookmarkedProduct(IndexedProduct product) {
        public BookmarkedProduct {
            Objects.requireNonNull(product, "product");
        }
    }
}
