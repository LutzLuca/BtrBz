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
import com.github.lutzluca.btrbz.widgets.DraggableWidget;
import com.github.lutzluca.btrbz.widgets.ScrollableListWidget;
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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.component.ComponentChanges;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

// TODO have a little more configuration for size; this will involve changes to ScrollableListWidget
// TODO make it a little prettier
@Slf4j
public class BookmarkModule extends Module<BookMarkConfig> {

    private ScrollableListWidget<BookmarkedItemWidget> list;

    @Override
    public void onLoad() {
        ItemOverrideManager.register((info, slot, original) -> {
            if (slot.getIndex() != 13 || !info.inMenu(BazaarMenuType.Item)) {
                return Optional.empty();
            }

            if (GameUtils.isPlayerInventorySlot(slot) || original == ItemStack.EMPTY) {
                return Optional.empty();
            }

            if (original.get(BtrBz.BOOKMARKED) != null) {
                return Optional.of(original);
            }
            
            String productName = original.getName().getString();
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
                return slot != null && slot.getIndex() == 13 && info.inMenu(BazaarMenuType.Item);
            }

            @Override
            public boolean onClick(ScreenInfo info, Slot slot, int button) {
                var bookmarked = slot.getStack().get(BtrBz.BOOKMARKED);
                if (bookmarked == null) {
                    return false;
                }

                String productName = slot.getStack().getName().getString();

                var isBookmarked = toggleBookmark(productName, slot.getStack().copy());
                slot.getStack().set(BtrBz.BOOKMARKED, isBookmarked);
                return true;
            }
        });
    }

    public void updateChildrenCount() {
        if (this.list == null) {
            return;
        }

        this.list.setMaxVisibleChildren(ConfigManager.get().bookmark.maxVisibleChildren);
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
            this.list.addChild(this.createBookmarkedItemWidget(
                new BookmarkedItem(
                    productName,
                    itemStack
                ), this.list.getParentScreen()
            ));
            return bookmarked.bookmarked;
        }

        this.list
            .getChildren()
            .stream()
            .filter(widget -> widget.getProductName().equals(productName))
            .findFirst()
            .ifPresentOrElse(
                widget -> this.list.removeChild(widget),
                () -> log.warn(
                    "Tried to remove bookmark widget for {}, but it was not found",
                    productName
                )
            );

        return bookmarked.bookmarked;
    }

    @Override
    public boolean shouldDisplay(ScreenInfo info) {
        return this.configState.enabled && info.inMenu(
            BazaarMenuType.Main,
            BazaarMenuType.Orders,
            BazaarMenuType.Item,
            BazaarMenuType.ItemGroup
        );
    }

    @Override
    public List<ClickableWidget> createWidgets(ScreenInfo info) {
        if (this.list != null) {
            return List.of(this.list);
        }

        var position = this.getConfigPosition().orElse(new Position(10, 10));

        ScrollableListWidget<BookmarkedItemWidget> widget = this.list = new ScrollableListWidget<>(
            position.x(),
            position.y(),
            180,
            200,
            Text.literal("Bookmarked Items"),
            info.getScreen()
        );

        widget
            .setMaxVisibleChildren(this.configState.maxVisibleChildren)
            .setChildHeight(16)
            .setBottomPadding(2)
            .setTopMargin(2)
            .setChildSpacing(1)
            .onChildClick((child, index) -> {
                GameUtils.runCommand(String.format("bz %s", child.productName));
            })
            .onChildReordered(() -> syncBookmarksFromList(widget))
            .onChildRemoved((child) -> syncBookmarksFromList(widget))
            .onDragEnd((self, pos) -> savePosition(pos));

        for (BookmarkedItem item : this.configState.bookmarkedItems) {
            widget.addChild(this.createBookmarkedItemWidget(item, info.getScreen()));
        }

        return List.of(widget);
    }

    private BookmarkedItemWidget createBookmarkedItemWidget(BookmarkedItem item, Screen parent) {
        return new BookmarkedItemWidget(0, 0, 180, 16, item.productName, item.itemStack, parent);
    }

    public boolean isBookmarked(String productName) {
        return this.configState.bookmarkedItems
            .stream()
            .anyMatch(item -> item.productName.equals(productName));
    }

    private void syncBookmarksFromList(ScrollableListWidget<BookmarkedItemWidget> list) {
        log.debug("Syncing bookmarks from widget list to config");

        this.updateConfig(cfg -> {
            cfg.bookmarkedItems = list
                .getChildren()
                .stream()
                .map(widget -> new BookmarkedItem(widget.getProductName(), widget.getItemStack()))
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

    public static class BookmarkedItemWidget extends DraggableWidget {

        @Getter
        private final String productName;
        @Getter
        private final ItemStack itemStack;

        private final int color;

        public BookmarkedItemWidget(
            int x,
            int y,
            int width,
            int height,
            String productName,
            ItemStack itemStack,
            Screen parent
        ) {
            super(x, y, width, height, Text.literal(productName), parent);
            this.productName = productName;
            this.itemStack = itemStack;
            //noinspection DataFlowIssue
            this.color = (0xFF << 24) | Try
                .of(() -> itemStack
                    .getName()
                    .getSiblings()
                    .getFirst()
                    .getStyle()
                    .getColor()
                    .getRgb())
                .getOrElse(0xD3D3D3);
            this.setRenderBackground(false);
            this.setRenderBorder(false);
        }

        @Override
        protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
            var textRenderer = MinecraftClient.getInstance().textRenderer;

            int iconX = this.getX() + 4;
            int iconY = this.getY() + (this.height - 14) / 2;
            float scale = 14f / 16f;

            //? if >=1.21.6 {
            /*var matrices = ctx.getMatrices();
            matrices.pushMatrix();
            matrices.translate(iconX, iconY);
            matrices.scale(scale, scale);
            ctx.drawItem(this.itemStack, 0, 0);
            matrices.popMatrix();
            *///?} else {

            var matrices = ctx.getMatrices();
            matrices.push();
            matrices.translate(iconX, iconY, 0);
            matrices.scale(scale, scale, 1.0f);
            ctx.drawItem(this.itemStack, 0, 0);
            matrices.pop();
            //?}

            if (this.hovered) {
                ctx.fill(
                    this.getX(),
                    this.getY(),
                    this.getX() + this.getWidth(),
                    this.getY() + this.getHeight(),
                    0x30FFFFFF
                );
            }

            int textX = iconX + 18;
            int textY = this.getY() + (this.height - textRenderer.fontHeight) / 2;
            ctx.drawTextWithShadow(textRenderer, this.productName, textX, textY, this.color);
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

                var itemId = Registries.ITEM.getId(stack.getItem());
                itemData.addProperty("id", itemId.toString());

                var components = stack.getComponentChanges();
                if (!components.isEmpty()) {
                    var nbt = ComponentChanges.CODEC
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

                var itemId = Identifier.of(itemData.get("id").getAsString());
                var item = Registries.ITEM.get(itemId);
                var stack = new ItemStack(item);

                if (itemData.has("components")) {
                    String componentString = itemData.get("components").getAsString();
                    try {
                        var componentNbt = StringNbtReader.readCompound(componentString);
                        var components = ComponentChanges.CODEC
                            .parse(new Dynamic<>(NbtOps.INSTANCE, componentNbt))
                            .getOrThrow();

                        stack.applyChanges(components);
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
        public int maxVisibleChildren = 6;

        public Option.Builder<Boolean> createEnabledOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Text.literal("Bookmarked Items Module"))
                .description(OptionDescription.of(Text.literal(
                    "Display a list of bookmarked bazaar items for quick access")))
                .binding(true, () -> this.enabled, enabled -> this.enabled = enabled)
                .controller(ConfigScreen::createBooleanController);
        }

        public Option.Builder<Integer> createMaxVisibleOption() {
            return Option
                .<Integer>createBuilder()
                .name(Text.literal("Max Visible Items"))
                .description(OptionDescription.of(Text.literal(
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
            var rootGroup = new OptionGrouping(this.createEnabledOption()).addOptions(this.createMaxVisibleOption());

            return OptionGroup
                .createBuilder()
                .name(Text.literal("Bookmarked Items"))
                .description(OptionDescription.of(Text.literal(
                    "Settings for the bookmarked items quick-access list")))
                .options(rootGroup.build())
                .collapsed(false)
                .build();
        }
    }
}
