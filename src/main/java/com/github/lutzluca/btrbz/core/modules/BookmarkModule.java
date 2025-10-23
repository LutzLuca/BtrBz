package com.github.lutzluca.btrbz.core.modules;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.modules.BookmarkModule.BookMarkConfig;
import com.github.lutzluca.btrbz.utils.ItemOverrideManager;
import com.github.lutzluca.btrbz.utils.Position;
import com.github.lutzluca.btrbz.utils.ScreenActionManager;
import com.github.lutzluca.btrbz.utils.ScreenActionManager.ScreenClickRule;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import com.github.lutzluca.btrbz.utils.Util;
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
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
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
// TODO have nice tooltips; buy offer / buy order prices
@Slf4j
public class BookmarkModule extends Module<BookMarkConfig> {

    @Override
    public void onLoad() {
        ItemOverrideManager.register((info, slot, original) -> {
            if (slot.getIndex() != 13 || !info.inMenu(BazaarMenuType.Item)) {
                return Optional.empty();
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

                toggleBookmark(productName, slot.getStack().copy());
                return true;
            }
        });
    }

    private void toggleBookmark(String productName, ItemStack itemStack) {
        this.updateConfig(cfg -> {
            var it = cfg.bookmarkedItems.listIterator();
            while (it.hasNext()) {
                var item = it.next();
                if (item.productName.equals(productName)) {
                    it.remove();
                    return;
                }
            }

            it.add(new BookmarkedItem(productName, itemStack));
        });
    }

    @Override
    public boolean shouldDisplay(ScreenInfo info) {
        return this.configState.enabled && info.inMenu(
            BazaarMenuType.Main,
            BazaarMenuType.Item,
            BazaarMenuType.ItemGroup
        );
    }

    @Override
    public List<ClickableWidget> createWidgets(ScreenInfo info) {
        var position = this.getConfigPosition().orElse(new Position(10, 10));

        ScrollableListWidget<BookmarkedItemWidget> list = new ScrollableListWidget<>(
            position.x(),
            position.y(),
            220,
            200,
            Text.literal("Bookmarked Items"),
            info.getScreen()
        );

        list
            .setMaxVisibleChildren(this.configState.maxVisibleChildren)
            .setChildHeight(24)
            .setChildSpacing(2)
            .onChildClick((child, index) -> {
                Util.runCommand(String.format("bz %s", child.productName));
            })
            .onChildReordered(() -> syncBookmarksFromList(list))
            .onChildRemoved((widget) -> syncBookmarksFromList(list))
            .onDragEnd((self, pos) -> savePosition(pos));

        for (BookmarkedItem item : this.configState.bookmarkedItems) {
            list.addChild(new BookmarkedItemWidget(
                0,
                0,
                220,
                24,
                item.productName,
                item.itemStack,
                info.getScreen()
            ));
        }

        return List.of(list);
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
                .collect(Collectors.toCollection(ArrayList::new));
        });
    }

    private Optional<Position> getConfigPosition() {
        return Util
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
            this.color = Try
                .of(() -> itemStack
                    .getName()
                    .getSiblings()
                    .getFirst()
                    .getStyle()
                    .getColor()
                    .getRgb())
                .getOrElse(0xFFFFFFFF);
            this.setRenderBackground(true);
            this.setRenderBorder(true);
        }

        @Override
        protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
            var textRenderer = MinecraftClient.getInstance().textRenderer;

            int iconX = this.getX() + 4;
            int iconY = this.getY() + (this.height - 16) / 2;
            ctx.drawItem(this.itemStack, iconX, iconY);

            int textX = iconX + 20;
            int textY = this.getY() + (this.height - textRenderer.fontHeight) / 2;
            ctx.drawTextWithShadow(textRenderer, productName, textX, textY, this.color);
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
        public int maxVisibleChildren = 5;

        public Option<Boolean> createEnabledOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Text.literal("Bookmarked Items Module"))
                .description(OptionDescription.of(Text.literal(
                    "Display a list of bookmarked bazaar items for quick access")))
                .binding(true, () -> this.enabled, enabled -> this.enabled = enabled)
                .controller(TickBoxControllerBuilder::create)
                .build();
        }

        public Option<Integer> createMaxVisibleOption() {
            return Option
                .<Integer>createBuilder()
                .name(Text.literal("Max Visible Items"))
                .description(OptionDescription.of(Text.literal(
                    "Maximum number of bookmarks visible at once before scrolling")))
                .binding(5, () -> this.maxVisibleChildren, val -> this.maxVisibleChildren = val)
                .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(3, 10).step(1))
                .build();
        }
    }
}
