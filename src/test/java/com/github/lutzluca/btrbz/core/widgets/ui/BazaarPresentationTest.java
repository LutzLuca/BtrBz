package com.github.lutzluca.btrbz.core.widgets.ui;

import com.github.lutzluca.btrbz.core.widgets.bookmarks.BookmarksWidget;
import com.github.lutzluca.btrbz.core.widgets.config.BazaarWidgetOptions;
import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import com.github.lutzluca.btrbz.core.widgets.hud.BazaarHudWidget;
import com.github.lutzluca.btrbz.core.widgets.orderbook.OrderBookWidget;
import com.github.lutzluca.btrbz.core.widgets.trackedorders.TrackedOrdersWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BazaarPresentationTest {
    @Test
    void singleSideOrderBookRetainsConfiguredContentWidth() {
        var split = options(330, BazaarWidgetOptions.BookLayout.Split);
        var buyOnly = options(330, BazaarWidgetOptions.BookLayout.BuyOnly);

        assertEquals(330, OrderBookWidget.contentWidth(split));
        assertEquals(164, OrderBookWidget.sideWidth(split));
        assertEquals(330, OrderBookWidget.contentWidth(buyOnly));
        assertEquals(330, OrderBookWidget.sideWidth(buyOnly));
    }

    @Test
    void singleSideOrderBookUsesTheConfiguredMinimumWidth() {
        var sellOnly = options(220, BazaarWidgetOptions.BookLayout.SellOnly);

        assertEquals(220, OrderBookWidget.contentWidth(sellOnly));
        assertEquals(220, OrderBookWidget.sideWidth(sellOnly));
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
        var data = new BazaarWidgetViewData.OrdersData(List.of(
            order("best", BazaarWidgetViewData.OrderStatus.Top),
            order("undercut", BazaarWidgetViewData.OrderStatus.Undercut)
        ));

        assertEquals(
            List.of("Undercut", "Best"),
            BazaarHudWidget.visibleStatusEntries(data).stream()
                .map(BazaarHudWidget.StatusEntry::label)
                .toList()
        );
    }

    @Test
    void compactHudPlacesFilledBeforeUnknown() {
        var data = new BazaarWidgetViewData.OrdersData(
            List.of(order("unknown", BazaarWidgetViewData.OrderStatus.Unknown)), 2
        );

        assertEquals(
            List.of("Filled", "Unknown"),
            BazaarHudWidget.visibleStatusEntries(data).stream()
                .map(BazaarHudWidget.StatusEntry::label)
                .toList()
        );
    }

    @Test
    void detailedHudDistinguishesFullyEmptyFromFilledHistory() {
        assertEquals("No active or filled orders", BazaarHudWidget.emptyText(
            new BazaarWidgetViewData.OrdersData(List.of(), 0)
        ));
        assertEquals("No active orders", BazaarHudWidget.emptyText(
            new BazaarWidgetViewData.OrdersData(List.of(), 2)
        ));
    }

    @Test
    void newestAndOldestAreDerivedWithoutMutatingManualOrder() {
        var old = order("old", BazaarWidgetViewData.OrderStatus.Top, 1);
        var fresh = order("fresh", BazaarWidgetViewData.OrderStatus.Top, 2);
        var manual = List.of(fresh, old);

        assertEquals(List.of(old, fresh), TrackedOrdersWidget.sortedOrders(
            manual, BazaarWidgetOptions.TrackedSort.Oldest
        ));
        assertEquals(List.of(fresh, old), TrackedOrdersWidget.sortedOrders(
            manual, BazaarWidgetOptions.TrackedSort.Newest
        ));
        assertEquals(List.of(fresh, old), manual);
    }

    @Test
    void bookmarkAlphabeticalViewUsesDisplayNameWithoutChangingManualOrder() {
        var zed = bookmark("z", "Zed");
        var alpha = bookmark("a", "alpha");
        var manual = List.of(zed, alpha);
        assertEquals(List.of(alpha, zed), BookmarksWidget.sortedBookmarks(
            manual, BazaarWidgetOptions.BookmarkSort.Alphabetical
        ));
        assertEquals(List.of(zed, alpha), BookmarksWidget.sortedBookmarks(
            manual, BazaarWidgetOptions.BookmarkSort.Manual
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
            BazaarWidgetOptions.NumberStyle.Exact,
            true,
            true,
            true
        );
    }

    private static BazaarWidgetViewData.Order order(String id, BazaarWidgetViewData.OrderStatus status) {
        return order(id, status, 0);
    }

    private static BazaarWidgetViewData.Order order(String id, BazaarWidgetViewData.OrderStatus status, long sequence) {
        return new BazaarWidgetViewData.Order(
            new com.github.lutzluca.btrbz.data.OrderModels.TrackedOrderId(
                java.util.UUID.nameUUIDFromBytes(id.getBytes())
            ), BazaarWidgetViewData.OrderSide.Buy, "Product", Component.literal("Product"),
            ItemStack.EMPTY, 1, 1, java.util.Optional.of(new BazaarWidgetViewData.FillProgress(0, 1)),
            status, java.util.Optional.empty(), List.of(), sequence
        );
    }

    private static BazaarWidgetViewData.Bookmark bookmark(String id, String name) {
        return new BazaarWidgetViewData.Bookmark(id, name, Component.literal(name), ItemStack.EMPTY, false, false);
    }
}
