package com.github.lutzluca.btrbz.core.widgets.data;

import com.github.lutzluca.btrbz.core.widgets.hud.BazaarOrdersWidgetConfig;
import com.github.lutzluca.btrbz.core.widgets.bookmarks.BookmarksWidgetData;
import com.github.lutzluca.btrbz.core.widgets.trackedorders.TrackedOrdersWidgetConfig;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetDisplayOptions;
import com.github.lutzluca.btrbz.core.widgets.hud.BazaarHudOptions;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarNumberFormat;
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

class BazaarWidgetViewDataTest {
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
            order("top-large", BazaarWidgetViewData.OrderStatus.Top, 71_680),
            order("top-small", BazaarWidgetViewData.OrderStatus.Top, 1),
            order("matched", BazaarWidgetViewData.OrderStatus.Matched, 500),
            order("undercut", BazaarWidgetViewData.OrderStatus.Undercut, 12),
            order("unknown", BazaarWidgetViewData.OrderStatus.Unknown, 99)
        );

        assertEquals(new BazaarWidgetViewData.StatusCounts(2, 1, 1, 1), BazaarWidgetViewData.StatusCounts.from(orders));
        assertEquals(5, new BazaarWidgetViewData.OrdersData(orders, 3).counts().total());
        assertEquals(3, new BazaarWidgetViewData.OrdersData(orders, 3).filledOrderCount());
    }

    @Test
    void hudDefaultsToDetailedMode() {
        var hud = new BazaarOrdersWidgetConfig();
        var tracked = new TrackedOrdersWidgetConfig();
        assertEquals(BazaarOrdersWidgetConfig.HudMode.Detailed, hud.mode);
        assertTrue(hud.hideWhenEmpty);
        assertTrue(hud.showItem);
        assertEquals(WidgetDisplayOptions.PriceDisplay.Unit, hud.priceDisplay);
        assertTrue(tracked.showStatusSummary);
        assertTrue(tracked.showProgress);
    }

    @Test
    void liveProgressNeverChangesTheStableOrderVolume() {
        var order = new BazaarWidgetViewData.Order(
            id("partially-filled"), BazaarWidgetViewData.OrderSide.Sell, "Product", Component.literal("Product"),
            ItemStack.EMPTY, 10, 64, 21, BazaarWidgetViewData.OrderStatus.Matched, List.of()
        );

        assertEquals(64, order.amount());
        assertEquals(43, order.liveProgress().orElseThrow().remaining());
    }

    @Test
    void bookmarkAbbreviationPreservesProviderFormatting() {
        var formatted = Component.literal("Enchanted Diamond").withStyle(ChatFormatting.AQUA);
        var bookmark = new BookmarksWidgetData.Bookmark(
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
        var bookmark = new BookmarksWidgetData.Bookmark(
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

    private static BazaarWidgetViewData.Order order(String id, BazaarWidgetViewData.OrderStatus status, int volume) {
        return new BazaarWidgetViewData.Order(
            id(id),
            BazaarWidgetViewData.OrderSide.Buy,
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
