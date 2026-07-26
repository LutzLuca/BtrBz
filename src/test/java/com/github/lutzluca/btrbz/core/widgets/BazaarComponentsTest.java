package com.github.lutzluca.btrbz.core.widgets;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BazaarComponentsTest {
    @Test
    void singleSideOrderBookRetainsConfiguredContentWidth() {
        var split = options(330, BazaarWidgetOptions.BookLayout.SPLIT);
        var buyOnly = options(330, BazaarWidgetOptions.BookLayout.BUY_ONLY);

        assertEquals(330, BazaarComponents.orderBookContentWidth(split));
        assertEquals(164, BazaarComponents.orderBookSideWidth(split));
        assertEquals(330, BazaarComponents.orderBookContentWidth(buyOnly));
        assertEquals(330, BazaarComponents.orderBookSideWidth(buyOnly));
    }

    @Test
    void singleSideOrderBookUsesTheConfiguredMinimumWidth() {
        var sellOnly = options(220, BazaarWidgetOptions.BookLayout.SELL_ONLY);

        assertEquals(220, BazaarComponents.orderBookContentWidth(sellOnly));
        assertEquals(220, BazaarComponents.orderBookSideWidth(sellOnly));
    }

    @Test
    void exactPriceKeepsItsFullWidthBeforeMetadata() {
        assertEquals(
            new BazaarOrderRowComponent.PriorityWidths(62, 25),
            BazaarOrderRowComponent.priorityWidths(90, 62, 55)
        );
        assertEquals(
            new BazaarOrderRowComponent.PriorityWidths(62, 0),
            BazaarOrderRowComponent.priorityWidths(55, 62, 55)
        );
    }

    @Test
    void compactHudShowsOnlyNonZeroStatusesInUrgencyOrder() {
        var data = new BazaarData.OrdersData(List.of(
            order("best", BazaarData.OrderStatus.TOP),
            order("undercut", BazaarData.OrderStatus.UNDERCUT)
        ));

        assertEquals(
            List.of("Undercut", "Best"),
            BazaarComponents.visibleStatusEntries(data).stream()
                .map(BazaarComponents.StatusEntry::label)
                .toList()
        );
    }

    @Test
    void compactHudPlacesFilledBeforeUnknown() {
        var data = new BazaarData.OrdersData(
            List.of(order("unknown", BazaarData.OrderStatus.UNKNOWN)), 2
        );

        assertEquals(
            List.of("Filled", "Unknown"),
            BazaarComponents.visibleStatusEntries(data).stream()
                .map(BazaarComponents.StatusEntry::label)
                .toList()
        );
    }

    @Test
    void detailedHudDistinguishesFullyEmptyFromFilledHistory() {
        assertEquals("No active or filled orders", BazaarComponents.emptyHudText(
            new BazaarData.OrdersData(List.of(), 0)
        ));
        assertEquals("No active orders", BazaarComponents.emptyHudText(
            new BazaarData.OrdersData(List.of(), 2)
        ));
    }

    @Test
    void newestAndOldestAreDerivedWithoutMutatingManualOrder() {
        var old = order("old", BazaarData.OrderStatus.TOP, 1);
        var fresh = order("fresh", BazaarData.OrderStatus.TOP, 2);
        var manual = List.of(fresh, old);

        assertEquals(List.of(old, fresh), BazaarComponents.sortedTrackedOrders(
            manual, BazaarWidgetOptions.TrackedSort.OLDEST
        ));
        assertEquals(List.of(fresh, old), BazaarComponents.sortedTrackedOrders(
            manual, BazaarWidgetOptions.TrackedSort.NEWEST
        ));
        assertEquals(List.of(fresh, old), manual);
    }

    @Test
    void bookmarkAlphabeticalViewUsesDisplayNameWithoutChangingManualOrder() {
        var zed = bookmark("z", "Zed");
        var alpha = bookmark("a", "alpha");
        var manual = List.of(zed, alpha);
        assertEquals(List.of(alpha, zed), BazaarExtraComponents.sortedBookmarks(
            manual, BazaarWidgetOptions.BookmarkSort.ALPHABETICAL
        ));
        assertEquals(List.of(zed, alpha), BazaarExtraComponents.sortedBookmarks(
            manual, BazaarWidgetOptions.BookmarkSort.MANUAL
        ));
    }

    private static BazaarWidgetOptions.OrderBook options(
        int width,
        BazaarWidgetOptions.BookLayout layout
    ) {
        return new BazaarWidgetOptions.OrderBook(
            width,
            5,
            layout,
            BazaarWidgetOptions.NumberStyle.EXACT,
            true,
            true
        );
    }

    private static BazaarData.Order order(String id, BazaarData.OrderStatus status) {
        return order(id, status, 0);
    }

    private static BazaarData.Order order(String id, BazaarData.OrderStatus status, long sequence) {
        return new BazaarData.Order(
            new com.github.lutzluca.btrbz.data.OrderModels.TrackedOrderId(
                java.util.UUID.nameUUIDFromBytes(id.getBytes())
            ), BazaarData.OrderSide.BUY, "Product", Component.literal("Product"),
            ItemStack.EMPTY, 1, 1, java.util.Optional.of(new BazaarData.FillProgress(0, 1)),
            status, java.util.Optional.empty(), List.of(), sequence
        );
    }

    private static BazaarData.Bookmark bookmark(String id, String name) {
        return new BazaarData.Bookmark(id, name, Component.literal(name), ItemStack.EMPTY, false, false);
    }
}
