package com.github.lutzluca.btrbz.core.widgets.bookmarks;

import com.github.lutzluca.btrbz.core.widgets.hud.BazaarHudOptions;
import com.github.lutzluca.btrbz.utils.Utils;
import java.util.List;
import java.util.Objects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class BookmarksWidgetData {
    private final BookmarkComponent component;

    public BookmarksWidgetData(BookmarkComponent component) {
        this.component = component;
    }

    public Snapshot snapshot() {
        return new Snapshot(this.component.currentBookmarks().stream().map(bookmark ->
            new Bookmark(
                bookmark.productId(), bookmark.productName(), Utils.legacyFormattedComponent(bookmark.formattedName()),
                bookmark.itemStack(), bookmark.hasBuyOrder(), bookmark.hasSellOffer()
            )
        ).toList());
    }

    public static Snapshot preview() {
        return new Snapshot(List.of(
            previewBookmark("BOOSTER_COOKIE", "Booster Cookie", Items.COOKIE, true, false),
            previewBookmark("ENCHANTED_DIAMOND", "Enchanted Diamond", Items.DIAMOND, true, true),
            previewBookmark("ENCHANTED_GOLD", "Enchanted Gold Block", Items.GOLD_BLOCK, false, true),
            previewBookmark("ENCHANTED_ENDER_PEARL", "Enchanted Ender Pearl", Items.ENDER_PEARL, false, false),
            previewBookmark("ENCHANTED_BLAZE_ROD", "Enchanted Blaze Rod", Items.BLAZE_ROD, true, false),
            previewBookmark("ENCHANTED_EMERALD", "Enchanted Emerald", Items.EMERALD, false, true)
        ));
    }

    private static Bookmark previewBookmark(
        String id,
        String name,
        Item item,
        boolean buy,
        boolean sell
    ) {
        return new Bookmark(id, name, styled(name, item), new ItemStack(item), buy, sell);
    }

    private static Component styled(String name, Item item) {
        var component = Component.literal(name);
        if (item == Items.COOKIE) return component.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        if (item == Items.DIAMOND) return component.withStyle(ChatFormatting.AQUA);
        if (item == Items.EMERALD) return component.withStyle(ChatFormatting.GREEN);
        return component.withStyle(ChatFormatting.GRAY);
    }

    public record Bookmark(
        String productId,
        String productName,
        Component formattedProductName,
        ItemStack itemStack,
        boolean buyOrder,
        boolean sellOrder
    ) {
        public Bookmark {
            Objects.requireNonNull(productId, "productId");
            Objects.requireNonNull(productName, "productName");
            Objects.requireNonNull(formattedProductName, "formattedProductName");
            itemStack = itemStack.copy();
        }

        public Bookmark(String productId, String productName, ItemStack itemStack, boolean buyOrder, boolean sellOrder) {
            this(productId, productName, Component.literal(productName), itemStack, buyOrder, sellOrder);
        }

        @Override
        public ItemStack itemStack() {
            return this.itemStack.copy();
        }

        public Component formattedProductName(boolean abbreviateEnchanted) {
            if (!abbreviateEnchanted) return this.formattedProductName.copy();
            return Component.literal(BazaarHudOptions.productName(this.productName, abbreviateEnchanted))
                .setStyle(this.formattedProductName.getStyle());
        }
    }

    public record Snapshot(List<Bookmark> bookmarks) {
        public Snapshot {
            bookmarks = List.copyOf(bookmarks);
        }
    }
}
