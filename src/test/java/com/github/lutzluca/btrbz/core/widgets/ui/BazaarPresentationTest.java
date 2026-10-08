package com.github.lutzluca.btrbz.core.widgets.ui;

import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrderId;
import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import com.github.lutzluca.btrbz.core.widgets.hud.BazaarHudWidget;
import com.github.lutzluca.btrbz.core.widgets.trackedorders.TrackedOrdersWidget;
import com.github.lutzluca.btrbz.core.widgets.trackedorders.TrackedOrdersWidgetConfig;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

class BazaarPresentationTest {
    @Test
    void exactPriceKeepsItsFullWidthBeforeMetadata() {
        Assertions.assertEquals(
            new BazaarOrderRowComponent.PriorityWidths(62, 25),
            BazaarOrderRowComponent.priorityWidths(90, 62, 55));
        Assertions.assertEquals(
            new BazaarOrderRowComponent.PriorityWidths(62, 0),
            BazaarOrderRowComponent.priorityWidths(55, 62, 55));
    }

    @Test
    void compactHudShowsOnlyNonZeroStatusesInUrgencyOrder() {
        var data = new BazaarWidgetViewData.OrdersData(List.of(
            order("best", BazaarWidgetViewData.OrderStatus.Top),
            order("undercut", BazaarWidgetViewData.OrderStatus.Undercut)));

        Assertions.assertEquals(
            List.of("Undercut", "Best"),
            BazaarHudWidget.visibleStatusEntries(data).stream()
                .map(BazaarHudWidget.StatusEntry::label)
                .toList());
    }

    @Test
    void compactHudPlacesFilledBeforeUnknown() {
        var data = new BazaarWidgetViewData.OrdersData(
            List.of(order("unknown", BazaarWidgetViewData.OrderStatus.Unknown)), 2);

        Assertions.assertEquals(
            List.of("Filled", "Unknown"),
            BazaarHudWidget.visibleStatusEntries(data).stream()
                .map(BazaarHudWidget.StatusEntry::label)
                .toList());
    }

    @Test
    void detailedHudDistinguishesFullyEmptyFromFilledHistory() {
        Assertions.assertEquals("No active or filled orders", BazaarHudWidget.emptyText(
            new BazaarWidgetViewData.OrdersData(List.of(), 0)));
        Assertions.assertEquals("No active orders", BazaarHudWidget.emptyText(
            new BazaarWidgetViewData.OrdersData(List.of(), 2)));
    }

    @Test
    void newestIsDerivedWithoutMutatingManualOrder() {
        var old = order("old", BazaarWidgetViewData.OrderStatus.Top, 1);
        var fresh = order("fresh", BazaarWidgetViewData.OrderStatus.Top, 2);
        var manual = new ArrayList<>(List.of(old, fresh));

        Assertions.assertEquals(List.of(fresh, old), TrackedOrdersWidget.sortedOrders(
            manual, TrackedOrdersWidgetConfig.TrackedSort.Newest));
        Assertions.assertEquals(List.of(old, fresh), manual,
            "the newest view leaves the unsorted manual list unchanged");
    }

    private static BazaarWidgetViewData.Order order(String id, BazaarWidgetViewData.OrderStatus status) {
        return order(id, status, 0);
    }

    private static BazaarWidgetViewData.Order order(String id, BazaarWidgetViewData.OrderStatus status, long sequence) {
        return new BazaarWidgetViewData.Order(
            new TrackedOrderId(UUID.nameUUIDFromBytes(id.getBytes())),
            BazaarWidgetViewData.OrderSide.Buy, "Product", Component.literal("Product"),
            Optional.empty(), 1, 1,
            Optional.of(new BazaarWidgetViewData.FillProgress(0, 1)),
            status, Optional.empty(), List.of(), sequence);
    }

}
