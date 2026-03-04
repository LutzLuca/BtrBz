package com.github.lutzluca.btrbz.core.modules;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.ModuleManager;
import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.core.config.ConfigScreen.OptionGrouping;
import com.github.lutzluca.btrbz.core.modules.BookmarkModule.BookMarkConfig;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.ItemOverrideManager;
import com.github.lutzluca.btrbz.utils.Position;
import com.github.lutzluca.btrbz.utils.ScreenActionManager;
import com.github.lutzluca.btrbz.utils.ScreenActionManager.ScreenClickRule;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import com.github.lutzluca.btrbz.utils.Utils;
import com.github.lutzluca.btrbz.widgets.base.DraggableWidget;
import com.github.lutzluca.btrbz.widgets.Renderable;
import com.github.lutzluca.btrbz.widgets.ListWidget;
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
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

@Slf4j
public class BookmarkModule extends Module<BookMarkConfig> {

    private ListWidget list;

    @Override
    public void onLoad() {
        ItemOverrideManager.register((info, slot, original) -> {
            if (slot.getContainerSlot() != 13 || !info.inMenu(BazaarMenuType.Item)) {
                return Optional.empty();
            }

            if (GameUtils.isPlayerInventorySlot(slot) || original == ItemStack.EMPTY) {
                return Optional.empty();
            }

            if (original.get(BtrBz.BOOKMARKED) != null) {
                return Optional.of(original);
            }

            String productName = original.getHoverName().getString();
            if (BtrBz.bazaarData().nameToId(productName).isEmpty()) {
                return Optional.empty();
            }

            boolean isBookmarked = this.isBookmarked(productName);

            original.set(BtrBz.BOOKMARKED, isBookmarked);

            return Optional.of(original);
        });

        ScreenActionManager.register(new ScreenClickRule() {
            @Override
            public boolean applies(ScreenInfo info, Slot slot, int button) {
                return slot != null && slot.getContainerSlot() == 13 && info.inMenu(BazaarMenuType.Item);
            }

            @Override
            public boolean onClick(ScreenInfo info, Slot slot, int button) {
                var bookmarked = slot.getItem().get(BtrBz.BOOKMARKED);
                if (bookmarked == null) {
                    return false;
                }

                String productName = slot.getItem().getHoverName().getString();

                var isBookmarked = toggleBookmark(productName, slot.getItem().copy());
                slot.getItem().set(BtrBz.BOOKMARKED, isBookmarked);
                return true;
            }
        });
    }

    public void updateChildrenCount() {
        if (this.list == null) {
            return;
        }

        this.list.setMaxVisibleItems(ConfigManager.get().bookmark.maxVisibleChildren);
    }

    private boolean toggleBookmark(String productName, ItemStack itemStack) {
        final class Bookmarked {

            boolean bookmarked;
        }

        var bookmarked = new Bookmarked();
        this.updateConfig(cfg -> {
            var it = cfg.bookmarkedItems.listIterator();
            while (it.hasNext()) {
                var item = it.next();
                if (item.productName.equals(productName)) {
                    it.remove();
                    bookmarked.bookmarked = false;
                    return;
                }
            }

            it.add(new BookmarkedItem(productName, itemStack));
            bookmarked.bookmarked = true;
        });

        if (this.list == null) {
            return bookmarked.bookmarked;
        }

        if (bookmarked.bookmarked) {
            this.list.addItem(new BookmarkedItemRenderable(productName, itemStack));
            return bookmarked.bookmarked;
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

        return bookmarked.bookmarked;
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
    public List<DraggableWidget> createWidgets(ScreenInfo info) {
        if (this.list != null) {
            return List.of(this.list);
        }

        var position = this.getConfigPosition().orElse(new Position(10, 10));

        var widget = this.list = new ListWidget(position.x(), position.y(), 175, 200, "Bookmarked Items");

        widget.setItemHeight(16)
            .setItemSpacing(1)
            .setReorderable(true)
            .setRemovable(true)
            .setMaxVisibleItems(ConfigManager.get().bookmark.maxVisibleChildren);

        widget.onItemClick((self, item, idx) -> GameUtils.runCommand("bz " + ((BookmarkedItemRenderable) item).getProductName()))
            .onReorder((self, fromIdx, toIdx) -> this.syncBookmarksFromList(self.getItems()))
            .onRemove((self, item) -> this.syncBookmarksFromList(self.getItems()))
            .onDragEnd((self, pos) -> this.savePosition(pos));

        List<Renderable> items = this.configState.bookmarkedItems.stream()
            .map(item -> new BookmarkedItemRenderable(item.productName(), item.itemStack()))
            .collect(Collectors.toList());
        widget.setItems(items);

        return List.of(widget);
    }

    public boolean isBookmarked(String productName) {
        return this.configState.bookmarkedItems
            .stream()
            .anyMatch(item -> item.productName.equals(productName));
    }

    private void syncBookmarksFromList(List<Renderable> items) {
        log.debug("Syncing bookmarks from widget list to config");

        this.updateConfig(cfg -> {
            cfg.bookmarkedItems = items.stream()
                .map(BookmarkedItemRenderable.class::cast)
                .map(item -> new BookmarkedItem(item.getProductName(), item.getItemStack()))
                .collect(Collectors.toList());
        });
    }

    private Optional<Position> getConfigPosition() {
        return Utils
            .zipNullables(this.configState.x, this.configState.y)
            .map(pair -> new Position(pair.getLeft(), pair.getRight()));
    }

    private void savePosition(Position pos) {
        log.debug("Saving new position for BookmarkedItemsModule: {}", pos);
        this.updateConfig(cfg -> {
            cfg.x = pos.x();
            cfg.y = pos.y();
        });
    }

    public static class BookmarkedItemRenderable implements Renderable {
        @Getter
        private final String productName;
        @Getter
        private final ItemStack itemStack;

        private final int color;
        private final Component displayText;

        public BookmarkedItemRenderable(String productName, ItemStack itemStack) {
            this.productName = productName;
            this.itemStack = itemStack;

            //noinspection DataFlowIssue
            this.color = (0xFF << 24) | Try
                .of(() -> itemStack.getHoverName().getSiblings().getFirst()
                    .getStyle().getColor().getValue())
                .getOrElse(0xD3D3D3);
            this.displayText = Component.literal(productName);
        }

        @Override
        public void render(
            GuiGraphics graphics,
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
            graphics.renderItem(this.itemStack, 0, 0);
            matrices.popMatrix();

            int textX = iconX + 18;
            int textY = y + (height - font.lineHeight) / 2;
            graphics.drawString(font, this.displayText, textX, textY, this.color);
        }

    }

    public record BookmarkedItem(String productName, ItemStack itemStack) {

        public static class BookmarkedItemSerializer implements JsonSerializer<BookmarkedItem>,
            JsonDeserializer<BookmarkedItem> {

            @Override
            public JsonElement serialize(
                BookmarkedItem src,
                Type typeOfSrc,
                JsonSerializationContext context
            ) {
                var obj = new JsonObject();
                obj.addProperty("productName", src.productName);

                var itemData = new JsonObject();
                var stack = src.itemStack;

                var itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
                itemData.addProperty("id", itemId.toString());

                var components = stack.getComponentsPatch();
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

                //? if >=1.21.11 {
                /*var itemId =  Identifier.parse(itemData.get("id").getAsString());
                *///?} else {
                var itemId =  ResourceLocation.parse(itemData.get("id").getAsString());
                //?}

                var item = BuiltInRegistries.ITEM.getValue(itemId);
                var stack = new ItemStack(item);

                if (itemData.has("components")) {
                    String componentString = itemData.get("components").getAsString();
                    try {
                        var componentNbt = TagParser.parseCompoundFully(componentString);
                        var components = DataComponentPatch.CODEC
                            .parse(new Dynamic<>(NbtOps.INSTANCE, componentNbt))
                            .getOrThrow();

                        stack.applyComponentsAndValidate(components);
                    } catch (CommandSyntaxException err) {
                        err.printStackTrace();
                    }
                }

                return new BookmarkedItem(productName, stack);
            }
        }

    }

    public static class BookMarkConfig {

        // TODO option for cropping at current displayed items instead of occupying the full space
        // always
        public List<BookmarkedItem> bookmarkedItems = new ArrayList<>();
        public Integer x, y;
        public boolean enabled = true;
        public boolean showEverywhere = true;
        public int maxVisibleChildren = 6;

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
                    6, () -> this.maxVisibleChildren, val -> {
                        this.maxVisibleChildren = val;
                        ModuleManager
                            .getInstance()
                            .getModule(BookmarkModule.class)
                            .updateChildrenCount();
                    }
                )
                .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(3, 14).step(1));
        }

        public OptionGroup createGroup() {
            var rootGroup = new OptionGrouping(this.createEnabledOption()).addOptions(
                this.createShowEverywhereOption(),
                this.createMaxVisibleOption()
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
