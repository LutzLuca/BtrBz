package com.github.lutzluca.btrbz.core.widgets.bookmarks;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetFrameConfig;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import com.github.lutzluca.btrbz.utils.GsonUtils;
import com.github.lutzluca.btrbz.core.widgets.layout.WidgetPlacement;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Dynamic;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;

public final class BookmarksWidgetConfig {
    public enum BookmarkSort { Manual, Alphabetical }

    public WidgetFrameConfig frame = new WidgetFrameConfig(WidgetPlacement.topLeft(0.30, 0.52));
    public int contentWidth = 200;
    public int visibleRows = 6;
    public boolean fitToContent = true;
    public BookmarkSort sort = BookmarkSort.Manual;
    public boolean hideWhenEmpty = true;
    public boolean showItems = true;
    public boolean showIndicators = true;
    public boolean abbreviateEnchanted = false;
    public List<BookmarkedItem> items = new ArrayList<>();

    public static void resetPreferences(BookmarksWidgetConfig current, BookmarksWidgetConfig defaults) {
        current.contentWidth = defaults.contentWidth;
        current.visibleRows = defaults.visibleRows;
        current.fitToContent = defaults.fitToContent;
        current.sort = defaults.sort;
        current.hideWhenEmpty = defaults.hideWhenEmpty;
        current.showItems = defaults.showItems;
        current.showIndicators = defaults.showIndicators;
        current.abbreviateEnchanted = defaults.abbreviateEnchanted;
    }

    /** Durable bookmark identity and a defensive item-stack template. */
    public record BookmarkedItem(IndexedProduct product, ItemStackTemplate itemTemplate) {
        public BookmarkedItem {
            if (product == null || itemTemplate == null) {
                throw new IllegalArgumentException("Bookmark product and item template are required");
            }
        }

        public BookmarkedItem(IndexedProduct product, ItemStack itemStack) {
            this(product, ItemStackTemplate.fromNonEmptyStack(itemStack));
        }

        public String productName() { return this.product.strippedName(); }
        public ItemStack itemStack() { return this.itemTemplate.create(); }

        @Slf4j
        public static final class GsonAdapter implements JsonSerializer<BookmarkedItem>,
            JsonDeserializer<BookmarkedItem> {
            @Override
            public JsonElement serialize(BookmarkedItem src, Type type, JsonSerializationContext context) {
                var result = new JsonObject();
                result.add("product", context.serialize(src.product, IndexedProduct.class));
                var item = new JsonObject();
                item.addProperty("id", BuiltInRegistries.ITEM.getKey(src.itemTemplate.item().value()).toString());
                if (!src.itemTemplate.components().isEmpty()) {
                    var nbt = DataComponentPatch.CODEC
                        .encodeStart(NbtOps.INSTANCE, src.itemTemplate.components())
                        .getOrThrow();
                    item.addProperty("components", nbt.toString());
                }
                result.add("itemStack", item);
                return result;
            }

            @Override
            public BookmarkedItem deserialize(JsonElement json, Type type, JsonDeserializationContext context)
                throws JsonParseException {
                if (!json.isJsonObject()) {
                    log.warn("Skipping malformed bookmark entry");
                    return null;
                }
                var object = json.getAsJsonObject();
                var product = readProduct(object, context).orElse(null);
                if (product == null || !object.has("itemStack") || !object.get("itemStack").isJsonObject()) {
                    log.warn("Skipping bookmark without valid product/item data");
                    return null;
                }
                var itemData = object.getAsJsonObject("itemStack");
                var rawId = GsonUtils.optionalString(itemData, "id");
                var itemId = rawId.map(Identifier::tryParse).orElse(null);
                if (itemId == null || BuiltInRegistries.ITEM.getValue(itemId) == Items.AIR) {
                    log.warn("Skipping bookmark {} with invalid item id {}", product, rawId.orElse("<missing>"));
                    return null;
                }
                var components = DataComponentPatch.EMPTY;
                if (itemData.has("components")) {
                    try {
                        var nbt = TagParser.parseCompoundFully(itemData.get("components").getAsString());
                        components = DataComponentPatch.CODEC
                            .parse(new Dynamic<>(NbtOps.INSTANCE, nbt))
                            .getOrThrow();
                    } catch (CommandSyntaxException | RuntimeException exception) {
                        log.warn("Ignoring malformed bookmark components for {}", product, exception);
                    }
                }
                return new BookmarkedItem(
                    product,
                    new ItemStackTemplate(BuiltInRegistries.ITEM.getValue(itemId), components)
                );
            }

            private static Optional<IndexedProduct> readProduct(
                JsonObject object,
                JsonDeserializationContext context
            ) {
                try {
                    return Optional.of(context.deserialize(
                        GsonUtils.required(object, "product", "Bookmark"),
                        IndexedProduct.class
                    ));
                } catch (RuntimeException exception) {
                    log.warn("Skipping bookmark with invalid product", exception);
                    return Optional.empty();
                }
            }
        }
    }
}
