package com.github.lutzluca.btrbz.core.widgets.bookmarks;

import com.github.lutzluca.btrbz.utils.GameUtils;

import com.github.lutzluca.btrbz.cache.CacheDependencies;
import com.github.lutzluca.btrbz.core.widgets.cache.WidgetDataSource;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import java.util.List;
import java.util.Objects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class BookmarksWidgetData implements WidgetDataSource<BookmarksWidgetData.Snapshot> {
    private final BookmarkComponent component;
    private final CacheDependencies dependencies;

    public BookmarksWidgetData(BookmarkComponent component) {
        this.component = component;
        this.dependencies = CacheDependencies.of(component.dataChanges());
    }

    @Override
    public CacheDependencies cacheDependencies() {
        return this.dependencies;
    }

    @Override
    public boolean sessionSensitive() {
        return false;
    }

    @Override
    public Snapshot snapshot(WidgetSession session) {
        return new Snapshot(this.component.currentBookmarks().stream().map(bookmark -> new Bookmark(
            bookmark.productId(), bookmark.productName(), GameUtils.legacyFormattedComponent(bookmark.formattedName()),
            bookmark.itemStack(), bookmark.hasBuyOrder(), bookmark.hasSellOffer())).toList());
    }

    public static Snapshot preview() {
        return new Snapshot(List.of(
            previewBookmark("BOOSTER_COOKIE", "Booster Cookie", Items.COOKIE, ChatFormatting.GOLD, true, false),
            previewBookmark("ENCHANTED_DIAMOND", "Enchanted Diamond", Items.DIAMOND, ChatFormatting.GREEN, true, true),
            previewBookmark("ENCHANTED_GOLD_BLOCK", "Enchanted Gold Block", Items.GOLD_BLOCK, ChatFormatting.BLUE,
                false, true),
            previewBookmark("ENCHANTED_ENDER_PEARL", "Enchanted Ender Pearl", Items.ENDER_PEARL, ChatFormatting.GREEN,
                false, false),
            previewBookmark("ENCHANTED_BLAZE_ROD", "Enchanted Blaze Rod", Items.BLAZE_ROD, ChatFormatting.BLUE, true,
                false),
            previewBookmark("ENCHANTED_EMERALD", "Enchanted Emerald", Items.EMERALD, ChatFormatting.GREEN, false,
                true)));
    }

    private static Bookmark previewBookmark(
        String id,
        String name,
        Item item,
        ChatFormatting rarity,
        boolean buy,
        boolean sell
    ) {
        return new Bookmark(id, name, Component.literal(name).withStyle(rarity), new ItemStack(item), buy, sell);
    }

    public record Bookmark(
        String productId,
        String productName,
        Component nativeName,
        ItemStack itemStack,
        boolean buyOrder,
        boolean sellOrder
    ) {
        public Bookmark {
            Objects.requireNonNull(productId, "productId");
            Objects.requireNonNull(productName, "productName");
            nativeName = Objects.requireNonNull(
                nativeName, "nativeName").copy();
            itemStack = itemStack.copy();
        }

        public Bookmark(
            String productId,
            String productName,
            ItemStack itemStack,
            boolean buyOrder,
            boolean sellOrder
        ) {
            this(productId, productName, Component.literal(productName), itemStack, buyOrder, sellOrder);
        }

        @Override
        public ItemStack itemStack() {
            return this.itemStack.copy();
        }

        @Override
        public Component nativeName() {
            return this.nativeName.copy();
        }

    }

    public record Snapshot(List<Bookmark> bookmarks) {
        public Snapshot {
            bookmarks = List.copyOf(bookmarks);
        }
    }
}
