package com.github.lutzluca.btrbz.core.modules;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Dynamic;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import io.vavr.control.Try;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.ModuleManager;
import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.core.config.ConfigScreen.OptionGrouping;
import com.github.lutzluca.btrbz.core.modules.BookmarkModule.BookMarkConfig;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.Position;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import com.github.lutzluca.btrbz.utils.slot.SlotClickContext;
import com.github.lutzluca.btrbz.utils.slot.SlotClickResult;
import com.github.lutzluca.btrbz.utils.slot.SlotHook;
import com.github.lutzluca.btrbz.utils.slot.SlotHookRegistry;
import com.github.lutzluca.btrbz.utils.slot.SlotRenderContext;
import com.github.lutzluca.btrbz.utils.slot.SlotView;
import com.github.lutzluca.btrbz.widgets.ListWidget;
import com.github.lutzluca.btrbz.widgets.Renderable;
import com.github.lutzluca.btrbz.widgets.base.DraggableWidget;

@Slf4j
public class BookmarkModule extends Module<BookMarkConfig> {

    public static final int PRODUCT_SLOT_IDX = 13;

    private ListWidget list;

    private final Set<String> orderBuySet = new HashSet<>();
    private final Set<String> orderSellSet = new HashSet<>();

    private void rebuildOrderCache() {
        this.orderBuySet.clear();
        this.orderSellSet.clear();

        BtrBz.orderManager().getTrackedOrders().forEach(order -> {
            switch (order.type) {
                case Buy -> this.orderBuySet.add(order.productName);
                case Sell -> this.orderSellSet.add(order.productName);
            }
        });
    }

    @Override
    public void onLoad() {
        this.configState.bookmarkedItems.removeIf(Objects::isNull);

        var orderManager = BtrBz.orderManager();
        this.rebuildOrderCache();
        orderManager.addOnOrderAddedListener(order -> this.rebuildOrderCache());
        orderManager.addOnOrderRemovedListener(order -> this.rebuildOrderCache());
        orderManager.addOnOrdersResetListener(this::rebuildOrderCache);

        SlotHookRegistry.register(new BookmarkedItemHook());
    }

    public void updateChildrenCount() {
        if (this.list == null) {
            return;
        }

        this.list.setMaxVisibleItems(ConfigManager.get().bookmark.maxVisibleChildren);
    }

    private boolean toggleBookmark(String productName, ItemStack itemStack) {
        final class BookmarkTag {
            boolean bookmarked;
        }

        var tag = new BookmarkTag();
        this.updateConfig(cfg -> {
            var it = cfg.bookmarkedItems.listIterator();
            while (it.hasNext()) {
                var item = it.next();
                if (item.productName().equals(productName)) {
                    it.remove();
                    tag.bookmarked = false;
                    return;
                }
            }

            it.add(new BookmarkedItem(productName, itemStack));
            tag.bookmarked = true;
        });

        if (this.list == null) {
            return tag.bookmarked;
        }

        if (tag.bookmarked) {
            this.list.addItem(new BookmarkedItemRenderable(productName, itemStack, this.orderBuySet, this.orderSellSet));
            return tag.bookmarked;
        }

        this.list
            .getItems()
            .stream()
            .filter(widget ->((BookmarkedItemRenderable) widget).getProductName().equals(productName))
            .findFirst()
            .ifPresentOrElse(
                widget -> {
                    int index = this.list.getItems().indexOf(widget);
                    if (index >= 0) {
                        this.list.removeItem(index);
                    }
                },
                () -> log.warn(
                    "Tried to remove bookmark widget for {}, but it was not found",
                    productName
                )
            );

        return tag.bookmarked;
    }

    @Override
    public boolean shouldDisplay(ScreenInfo info) {
        return this.configState.enabled && (info.inMenu(
            BazaarMenuType.Main,
            BazaarMenuType.Orders,
            BazaarMenuType.Item,
            BazaarMenuType.ItemGroup) || this.configState.showEverywhere && info.inBazaar());
    }

    @Override
    public Optional<DraggableWidget> createWidget(ScreenInfo info) {
        if (this.list != null) {
            return Optional.of(this.list);
        }

        var position = Optional.ofNullable(this.configState.position).orElse(new Position(150, 275));

        var widget = this.list = new ListWidget(position.x(), position.y(), 175, 200, "Bookmarked Items");

        widget.setItemHeight(16)
            .setItemSpacing(1)
            .setReorderable(true)
            .setRemovable(true)
            .setMaxVisibleItems(ConfigManager.get().bookmark.maxVisibleChildren);

        widget.onItemClick((self, item, idx) -> GameUtils.runCommand("bz " + ((BookmarkedItemRenderable) item).getProductName()))
            .onReorder((self, fromIdx, toIdx) -> this.syncBookmarksFromList(self.getItems()))
            .onItemRemoved((self, item, idx) -> this.syncBookmarksFromList(self.getItems()))
            .onDragEnd((self, pos) -> this.updateConfig(cfg -> cfg.position = pos));

        List<Renderable> items = this.configState.bookmarkedItems.stream()
            .map(item -> new BookmarkedItemRenderable(item.productName(), item.itemStack(), this.orderBuySet, this.orderSellSet))
            .collect(Collectors.toList());
        widget.setItems(items);

        return Optional.of(widget);
    }

    public boolean isBookmarked(String productName) {
        for (var item : this.configState.bookmarkedItems) {
            if (item.productName().equals(productName)) {
                return true;
            }
        }
        return false;
    }

    public final class BookmarkedItemHook implements SlotHook {

        private BookmarkedItemHook() { }

        @Override
        public boolean matches(SlotView view) {
            return BookmarkModule.this.configState.enabled
                && view.slotIdx() == PRODUCT_SLOT_IDX
                && view.getCurrInfo().inMenu(BazaarMenuType.Item);
        }

        @Override
        public ItemStack createDisplayStack(SlotRenderContext ctx) {
            if (!BookmarkModule.this.configState.enabled) {
                return null;
            }

            var rawStack = ctx.view().getRawStack();
            if (ctx.view().playerInventorySlot() || rawStack.isEmpty()) {
                return null;
            }

            var productNameInfo = BookmarkModule.this.context().productInfoProvider().getOpenedProductNameInfo();
            if (productNameInfo == null) {
                return null;
            }

            rawStack.set(BtrBz.BOOKMARKED, BookmarkModule.this.isBookmarked(productNameInfo.productName()));
            return rawStack;
        }

        @Override
        public SlotClickResult onClick(SlotClickContext ctx) {
            if (!BookmarkModule.this.configState.enabled) {
                return SlotClickResult.Pass;
            }

            var rawStack = ctx.view().getRawStack();
            var bookmarked = rawStack.get(BtrBz.BOOKMARKED);
            if (bookmarked == null) {
                return SlotClickResult.Pass;
            }

            String productName = rawStack.getHoverName().getString();
            var isBookmarked = BookmarkModule.this.toggleBookmark(productName, rawStack.copy());
            rawStack.set(BtrBz.BOOKMARKED, isBookmarked);
            return SlotClickResult.Consume;
        }
    }

    private void syncBookmarksFromList(List<Renderable> items) {
        log.debug("Syncing bookmarks from widget list to config");

        this.updateConfig(cfg -> cfg.bookmarkedItems = items.stream()
            .map(BookmarkedItemRenderable.class::cast)
            .map(item -> new BookmarkedItem(item.getProductName(), item.getItemStack()))
            .collect(Collectors.toList()));
    }

    public static class BookmarkedItemRenderable implements Renderable {
        @Getter
        private final String productName;
        @Getter
        private final ItemStack itemStack;

        private final int color;
        private final Component displayText;

        private final Set<String> orderBuySet;
        private final Set<String> orderSellSet;

        public BookmarkedItemRenderable(String productName, ItemStack itemStack,
                Set<String> orderBuySet, Set<String> orderSellSet) {
            this.productName = productName;
            this.itemStack = itemStack;
            this.orderBuySet = orderBuySet;
            this.orderSellSet = orderSellSet;

            this.color = (0xFF << 24) | Try
                .of(() -> itemStack.getHoverName().getSiblings().getFirst()
                    .getStyle().getColor().getValue())
                .getOrElse(0xD3D3D3);
            this.displayText = Component.literal(productName);
        }

        @Override
        public void render(
            GuiGraphicsExtractor graphics,
            int x, int y, int width, int height,
            int mouseX, int mouseY, float delta,
            boolean hovered
        ) {
            var font = Minecraft.getInstance().font;

            if (hovered) {
                graphics.fill(x, y, x + width, y + height, 0x30FFFFFF);
            }

            int iconX = x + 4;
            int iconY = y + (height - 14) / 2;
            float scale = 14f / 16f;

            var matrices = graphics.pose();
            matrices.pushMatrix();
            matrices.translate(iconX, iconY);
            matrices.scale(scale, scale);
            graphics.item(this.itemStack, 0, 0);
            matrices.popMatrix();

            int textX = iconX + 18;
            int textY = y + (height - font.lineHeight) / 2;
            graphics.text(font, this.displayText, textX, textY, this.color);

            // Draw order indicator
            if (!ConfigManager.get().bookmark.showOrderIndicators) {
                return;
            }

            boolean hasBuy = this.orderBuySet.contains(this.productName);
            boolean hasSell = this.orderSellSet.contains(this.productName);

            if (hasBuy || hasSell) {
                int centerX = x + width - 8;
                int centerY = y + (height / 2);

                if (hasBuy && hasSell) {
                    int radius = 2;
                    int leftDotX = x + width - 13;
                    int rightDotX = x + width - 6;
                    drawDot(graphics, leftDotX, centerY, radius, 0xFF55FF55);
                    drawDot(graphics, rightDotX, centerY, radius, 0xFFFFAA00);
                } else {
                    int dotColor = hasBuy ? 0xFF55FF55 : 0xFFFFAA00;
                    drawDot(graphics, centerX, centerY, 3, dotColor);
                }
            }
        }

        private static void drawDot(GuiGraphicsExtractor graphics, int cx, int cy, int radius, int color) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (dx * dx + dy * dy <= radius * radius) {
                        graphics.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
                    }
                }
            }
        }

    }

    public record BookmarkedItem(String productName, ItemStackTemplate itemTemplate) {

        public BookmarkedItem {
            if (productName == null || productName.isBlank()) {
                throw new IllegalArgumentException("Product name cannot be null or blank");
            }
            if (itemTemplate == null) {
                throw new IllegalArgumentException("Item template cannot be null");
            }
        }

        public BookmarkedItem(String productName, ItemStack itemStack) {
            this(productName, ItemStackTemplate.fromNonEmptyStack(itemStack));
        }

        public ItemStack itemStack() {
            return this.itemTemplate.create();
        }

        public static class GsonAdapter implements JsonSerializer<BookmarkedItem>,
            JsonDeserializer<BookmarkedItem> {

            @Override
            public JsonElement serialize(
                BookmarkedItem src,
                Type typeOfSrc,
                JsonSerializationContext context
            ) {
                var obj = new JsonObject();
                obj.addProperty("productName", src.productName());

                var itemData = new JsonObject();
                var template = src.itemTemplate();

                var itemId = BuiltInRegistries.ITEM.getKey(template.item().value());
                itemData.addProperty("id", itemId.toString());

                var components = template.components();
                if (!components.isEmpty()) {
                    var nbt = DataComponentPatch.CODEC
                        .encodeStart(NbtOps.INSTANCE, components)
                        .getOrThrow();

                    itemData.addProperty("components", nbt.toString());
                }

                obj.add("itemStack", itemData);
                return obj;
            }

            @Override
            public BookmarkedItem deserialize(
                JsonElement json,
                Type typeOfT,
                JsonDeserializationContext context
            ) throws JsonParseException {
                var obj = json.getAsJsonObject();

                var productName = obj.get("productName").getAsString();
                var itemData = obj.getAsJsonObject("itemStack");
                var itemIdString = itemData.get("id").getAsString();
                var itemId = Identifier.tryParse(itemIdString);
                if (itemId == null) {
                    log.warn(
                        "Skipping bookmark {} with invalid item id {}",
                        productName,
                        itemIdString
                    );
                    return null;
                }

                var item = BuiltInRegistries.ITEM.getValue(itemId);
                if (item == Items.AIR) {
                    log.warn("Skipping bookmark {} with unknown item id {}", productName, itemId);
                    return null;
                }

                var components = DataComponentPatch.EMPTY;
                if (itemData.has("components")) {
                    String componentString = itemData.get("components").getAsString();

                    try {
                        var componentNbt = TagParser.parseCompoundFully(componentString);
                        components = DataComponentPatch.CODEC
                            .parse(new Dynamic<>(NbtOps.INSTANCE, componentNbt))
                            .getOrThrow();
                    } catch (CommandSyntaxException err) {
                        log.warn("Ignoring invalid components for bookmark {}", productName, err);
                    } catch (RuntimeException err) {
                        log.warn("Ignoring malformed components for bookmark {}", productName, err);
                    }
                }

                var template = new ItemStackTemplate(item, components);
                return new BookmarkedItem(productName, template);
            }
        }

    }

    public static class BookMarkConfig {

        // TODO option for cropping at current displayed items instead of occupying the full space
        // always
        public List<BookmarkedItem> bookmarkedItems = new ArrayList<>();
        public Position position;
        public boolean enabled = true;
        public boolean showEverywhere = true;
        public int maxVisibleChildren = 8;
        public boolean showOrderIndicators = true;

        public Option.Builder<Boolean> createEnabledOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Bookmarked Items Module"))
                .description(OptionDescription.of(Component.literal(
                    "Display a list of bookmarked bazaar items for quick access")))
                .binding(true, () -> this.enabled, enabled -> this.enabled = enabled)
                .controller(ConfigScreen::createBooleanController);
        }


        public Option.Builder<Boolean> createShowEverywhereOption() {
            return Option
                    .<Boolean>createBuilder()
                    .name(Component.literal("Display everywhere in Bazaar"))
                    .description(OptionDescription.of(Component.literal(
                            "Whether to display the bookmarked list everywhere in the bazaar and not only in the item menus")))
                    .binding(true, () -> this.showEverywhere, enabled -> this.showEverywhere = enabled)
                    .controller(ConfigScreen::createBooleanController);
        }

        public Option.Builder<Integer> createMaxVisibleOption() {
            return Option
                .<Integer>createBuilder()
                .name(Component.literal("Max Visible Items"))
                .description(OptionDescription.of(Component.literal(
                    "Maximum number of bookmarks visible at once before scrolling")))
                .binding(
                    8, () -> this.maxVisibleChildren, val -> {
                        this.maxVisibleChildren = val;
                        ModuleManager
                            .getInstance()
                            .getModule(BookmarkModule.class)
                            .updateChildrenCount();
                    }
                )
                .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(3, 14).step(1));
        }

        public Option.Builder<Boolean> createShowOrderIndicatorsOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Show Order Indicators"))
                .description(OptionDescription.of(Component.literal(
                    "Show colored dots on bookmarked items that have active tracked orders")))
                .binding(true, () -> this.showOrderIndicators, val -> this.showOrderIndicators = val)
                .controller(ConfigScreen::createBooleanController);
        }

        public OptionGroup createGroup() {
            var rootGroup = new OptionGrouping(this.createEnabledOption()).addOptions(
                this.createShowEverywhereOption(),
                this.createMaxVisibleOption(),
                this.createShowOrderIndicatorsOption()
            );

            return OptionGroup
                .createBuilder()
                .name(Component.literal("Bookmarked Items"))
                .description(OptionDescription.of(Component.literal(
                    "Settings for the bookmarked items quick-access list")))
                .options(rootGroup.build())
                .collapsed(false)
                .build();
        }
    }
}
