package com.github.lutzluca.btrbz.core.widgets.bookmarks;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetFrameConfig;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import com.github.lutzluca.btrbz.core.widgets.layout.WidgetPlacement;
import com.github.lutzluca.btrbz.utils.GsonUtils;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import io.vavr.control.Try;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

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

    @JsonAdapter(BookmarkedProduct.GsonAdapter.class)
    public record BookmarkedProduct(IndexedProduct product) {
        public BookmarkedProduct {
            Objects.requireNonNull(product, "product");
        }

        @Slf4j
        public static final class GsonAdapter implements JsonDeserializer<BookmarkedProduct> {
            @Override
            public BookmarkedProduct deserialize(JsonElement json, Type type, JsonDeserializationContext context) {
                return Try.of(() -> new BookmarkedProduct(context.deserialize(
                    GsonUtils.required(json.getAsJsonObject(), "product", "Bookmark"),
                    IndexedProduct.class)))
                    .onFailure(err -> log.warn("Skipping malformed bookmark entry", err))
                    .getOrElse((BookmarkedProduct) null);
            }
        }
    }
}
