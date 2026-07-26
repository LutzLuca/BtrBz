package com.github.lutzluca.btrbz.core.widgets.config;

import com.github.lutzluca.btrbz.data.IndexedProduct;
import com.github.lutzluca.btrbz.utils.GsonUtils;
import com.github.lutzluca.btrbz.widgets.framework.WidgetPlacement;
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

/** The single fixed, YACL-owned persistence shape for all production widgets. */
@Slf4j
public final class WidgetsConfig {
    public double globalFineTuneScale = 1.0;

    public BazaarOrdersConfig bazaarOrders = new BazaarOrdersConfig();
    public TrackedOrdersConfig trackedOrders = new TrackedOrdersConfig();
    public OrderValueConfig orderValue = new OrderValueConfig();
    public OrderBookConfig orderBookScreen = new OrderBookConfig();
    public OrderBookPriceConfig orderBookPrice = new OrderBookPriceConfig();
    public BookmarksConfig bookmarks = new BookmarksConfig();
    public OrderPresetsConfig orderPresets = new OrderPresetsConfig();
    public DailyLimitConfig orderLimit = new DailyLimitConfig();
    public PriceDiffConfig priceDiff = new PriceDiffConfig();

    public abstract static class SinglePlacementConfig {
        public boolean enabled = true;
        public WidgetPlacement position = WidgetPlacement.topLeft(0, 0);
        public double scale = 1.0;
        public Integer background = null;
    }

    public static final class BazaarOrdersConfig extends SinglePlacementConfig {
        public BazaarWidgetOptions.HudMode mode = BazaarWidgetOptions.HudMode.Detailed;
        public int visibleOrders = 6;
        public int contentWidth = 200;
        public boolean abbreviateEnchanted = false;
        public boolean hideWhenEmpty = true;
        public boolean showItem = true;
        public boolean showVolume = true;
        public BazaarWidgetOptions.PriceDisplay priceDisplay = BazaarWidgetOptions.PriceDisplay.Unit;
        public BazaarWidgetOptions.QueueDisplay queueDisplay = BazaarWidgetOptions.QueueDisplay.Items;
        public BazaarWidgetOptions.UndercutDetail undercutDetail =
            BazaarWidgetOptions.UndercutDetail.PriceGapAndQueue;

        public BazaarOrdersConfig() {
            this.position = WidgetPlacement.topLeft(0.04, 0.05);
        }
    }

    public static final class TrackedOrdersConfig extends SinglePlacementConfig {
        public int contentWidth = 218;
        public int visibleRows = 6;
        public boolean fitToContent = true;
        public BazaarWidgetOptions.TrackedLayout layout = BazaarWidgetOptions.TrackedLayout.Standard;
        public BazaarWidgetOptions.TrackedSort sort = BazaarWidgetOptions.TrackedSort.Manual;
        public boolean abbreviateEnchanted = false;
        public boolean hideWhenEmpty = true;
        public boolean showStatusSummary = true;
        public boolean showItem = true;
        public boolean showVolume = true;
        public BazaarWidgetOptions.PriceDisplay priceDisplay = BazaarWidgetOptions.PriceDisplay.Unit;
        public boolean showMarketInfo = true;
        public boolean showProgress = true;

        public TrackedOrdersConfig() {
            this.position = WidgetPlacement.topLeft(0.04, 0.18);
        }
    }

    public static final class OrderValueConfig extends SinglePlacementConfig {
        public int contentWidth = 205;
        public BazaarWidgetOptions.ValueDisplay display = BazaarWidgetOptions.ValueDisplay.Detailed;
        public BazaarWidgetOptions.NumberStyle numberStyle = BazaarWidgetOptions.NumberStyle.Compact;
        public boolean showCoinsSuffix = true;
        public BazaarWidgetOptions.ColorMode colorMode = BazaarWidgetOptions.ColorMode.Semantic;
        public boolean buyLocked = true;
        public boolean buyItems = true;
        public boolean sellClaimable = true;
        public boolean sellPending = true;

        public OrderValueConfig() {
            this.position = WidgetPlacement.topLeft(0.65, 0.16);
        }
    }

    public static final class OrderBookConfig extends SinglePlacementConfig {
        public int contentWidth = 330;
        public int visibleRows = 5;
        public BazaarWidgetOptions.BookLayout layout = BazaarWidgetOptions.BookLayout.Split;
        public BazaarWidgetOptions.NumberStyle numberStyle = BazaarWidgetOptions.NumberStyle.Exact;
        public boolean showOrderCount = true;
        public boolean showHeader = true;
        public boolean showItem = true;

        public OrderBookConfig() {
            this.position = WidgetPlacement.topLeft(0.55, 0.34);
        }
    }

    public static final class OrderBookPriceConfig extends SinglePlacementConfig {
        public int contentWidth = 240;
        public int visibleRows = 3;
        public boolean showBuy = true;
        public boolean showSell = true;
        public boolean showAmounts = true;
        public boolean showOrderCount = true;
        public boolean showHeader = true;
        public boolean showItem = true;
        public BazaarWidgetOptions.EmbeddedSideDisplay sideDisplay =
            BazaarWidgetOptions.EmbeddedSideDisplay.Relevant;

        public OrderBookPriceConfig() {
            this.position = WidgetPlacement.topLeft(0.04, 0.50);
        }
    }

    public static final class BookmarksConfig extends SinglePlacementConfig {
        public int contentWidth = 200;
        public int visibleRows = 6;
        public boolean fitToContent = true;
        public BazaarWidgetOptions.BookmarkSort sort = BazaarWidgetOptions.BookmarkSort.Manual;
        public boolean hideWhenEmpty = true;
        public boolean showItems = true;
        public boolean showIndicators = true;
        public boolean abbreviateEnchanted = false;
        public List<BookmarkedItem> items = new ArrayList<>();

        public BookmarksConfig() {
            this.position = WidgetPlacement.topLeft(0.30, 0.52);
        }
    }

    public static final class OrderPresetsConfig {
        public boolean enabled = true;
        public WidgetPlacement containerPosition = WidgetPlacement.topLeft(0.55, 0.58);
        public WidgetPlacement signPosition = WidgetPlacement.topLeft(0.62, 0.08);
        public double scale = 1.0;
        public Integer background = null;
        public int contentWidth = 100;
        public boolean maximum = true;
        public boolean clipboard = true;
        public boolean showDisabled = true;
        public boolean showTooltips = true;
        public List<Integer> volumes = new ArrayList<>();
    }

    public static final class DailyLimitConfig extends SinglePlacementConfig {
        public int contentWidth = 180;
        public BazaarWidgetOptions.LimitDisplay display = BazaarWidgetOptions.LimitDisplay.UsedLimit;
        public BazaarWidgetOptions.NumberStyle numberStyle = BazaarWidgetOptions.NumberStyle.Compact;
        public boolean showHeader = true;
        public int warningThreshold = 75;
        public int criticalThreshold = 90;
        public double dailyLimit = 15_000_000_000d;
        public double usedToday = 0;
        public long lastResetEpochDay = -1;

        public DailyLimitConfig() {
            this.position = WidgetPlacement.topLeft(0.76, 0.58);
        }
    }

    public static final class PriceDiffConfig extends SinglePlacementConfig {
        public int contentWidth = 190;
        public BazaarWidgetOptions.DiffDisplay display = BazaarWidgetOptions.DiffDisplay.Both;
        public BazaarWidgetOptions.NumberStyle numberStyle = BazaarWidgetOptions.NumberStyle.Compact;
        public boolean showItems = true;
        public boolean showProduct = true;

        public PriceDiffConfig() {
            this.position = WidgetPlacement.topLeft(0.76, 0.72);
        }
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

        public String productName() {
            return this.product.strippedName();
        }

        public ItemStack itemStack() {
            return this.itemTemplate.create();
        }

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
