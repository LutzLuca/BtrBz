package com.github.lutzluca.btrbz.core.widgets;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrderId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BazaarDataTest {
    @Test
    void bookmarkDragKeepsStableProductIdentityAndInsertionBoundary() {
        var drag = new BazaarData.BookmarkDragController();
        drag.start("BOOSTER_COOKIE", 1);
        drag.markMoved();
        drag.updateDropIndex(4);
        assertEquals(
            new BazaarData.BookmarkDragResult("BOOSTER_COOKIE", 1, 4, true),
            drag.finish()
        );
        assertFalse(drag.dragging());
    }

    @Test
    void compactCoinFormattingMatchesBtrbzStyle() {
        assertEquals("7.1B", BazaarNumberFormat.compact(7_100_000_000d));
        assertEquals("26.12B", BazaarNumberFormat.compact(26_120_000_000d));
        assertEquals("26B", BazaarNumberFormat.compact(26_000_000_000d));
        assertEquals("4.5M", BazaarNumberFormat.compact(4_500_000d));
        assertEquals("21.2M", BazaarNumberFormat.compact(21_200_000d));
        assertEquals("1.5k", BazaarNumberFormat.compact(1_500d));
        assertEquals("875", BazaarNumberFormat.compact(875d));
    }

    @Test
    void enchantedAbbreviationIsOptionalAndSpecific() {
        assertEquals("Ench. Diamond", BazaarHudOptions.productName("Enchanted Diamond", true));
        assertEquals("Enchanted Diamond", BazaarHudOptions.productName("Enchanted Diamond", false));
        assertEquals("Booster Cookie", BazaarHudOptions.productName("Booster Cookie", true));
    }

    @Test
    void statusCountsRepresentOrdersRatherThanVolume() {
        var orders = List.of(
            order("top-large", BazaarData.OrderStatus.TOP, 71_680),
            order("top-small", BazaarData.OrderStatus.TOP, 1),
            order("matched", BazaarData.OrderStatus.MATCHED, 500),
            order("undercut", BazaarData.OrderStatus.UNDERCUT, 12),
            order("unknown", BazaarData.OrderStatus.UNKNOWN, 99)
        );

        assertEquals(new BazaarData.StatusCounts(2, 1, 1, 1), BazaarData.StatusCounts.from(orders));
        assertEquals(5, new BazaarData.OrdersData(orders, 3).counts().total());
        assertEquals(3, new BazaarData.OrdersData(orders, 3).filledOrderCount());
    }

    @Test
    void hudDefaultsToDetailedMode() {
        assertEquals(BazaarWidgetOptions.HudMode.DETAILED, BazaarWidgetOptions.defaults().hud().mode());
        assertEquals(false, BazaarWidgetOptions.defaults().hud().hideWhenEmpty());
        assertEquals(true, BazaarWidgetOptions.defaults().hud().showItem());
        assertEquals(BazaarWidgetOptions.PriceDisplay.UNIT,
            BazaarWidgetOptions.defaults().hud().priceDisplay());
        assertEquals(true, BazaarWidgetOptions.defaults().trackedOrders().showStatusSummary());
        assertEquals(true, BazaarWidgetOptions.defaults().trackedOrders().showProgress());
    }

    @Test
    void liveProgressNeverChangesTheStableOrderVolume() {
        var order = new BazaarData.Order(
            id("partially-filled"), BazaarData.OrderSide.SELL, "Product", Component.literal("Product"),
            ItemStack.EMPTY, 10, 64, 21, BazaarData.OrderStatus.MATCHED, List.of()
        );

        assertEquals(64, order.amount());
        assertEquals(43, order.liveProgress().orElseThrow().remaining());
    }

    @Test
    void bookmarkAbbreviationPreservesProviderFormatting() {
        var formatted = Component.literal("Enchanted Diamond").withStyle(ChatFormatting.AQUA);
        var bookmark = new BazaarData.Bookmark(
            "ENCHANTED_DIAMOND",
            "Enchanted Diamond",
            formatted,
            ItemStack.EMPTY,
            true,
            false
        );

        assertEquals("Ench. Diamond", bookmark.formattedProductName(true).getString());
        assertEquals(formatted.getStyle(), bookmark.formattedProductName(true).getStyle());
    }

    @Test
    void unabridgedBookmarkPreservesTheSuppliedComponentTree() {
        var formatted = Component.literal("Enchanted ").withStyle(ChatFormatting.AQUA)
            .append(Component.literal("Diamond").withStyle(ChatFormatting.BOLD));
        var bookmark = new BazaarData.Bookmark(
            "ENCHANTED_DIAMOND",
            "Enchanted Diamond",
            formatted,
            ItemStack.EMPTY,
            true,
            false
        );

        var rendered = bookmark.formattedProductName(false);
        assertEquals(formatted, rendered);
        assertEquals(1, rendered.getSiblings().size());
        assertTrue(rendered.getSiblings().getFirst().getStyle().isBold());
    }

    private static BazaarData.Order order(String id, BazaarData.OrderStatus status, int volume) {
        return new BazaarData.Order(
            id(id),
            BazaarData.OrderSide.BUY,
            "Product",
            Component.literal("Product"),
            ItemStack.EMPTY,
            1,
            volume,
            0,
            status,
            List.of()
        );
    }

    private static TrackedOrderId id(String value) {
        return new TrackedOrderId(UUID.nameUUIDFromBytes(value.getBytes()));
    }

}
