package com.github.lutzluca.btrbz.core;

import com.github.lutzluca.btrbz.core.trackedorders.TrackedOrderManager;
import com.github.lutzluca.btrbz.core.widgets.ordervalue.OrderValueComponent;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo.ExpiredOrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo.FilledOrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo.UnfilledOrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class OrderHighlightManagerTest {
    @Test
    void snapshotsHighlightExpiredCardsWithoutTrackingThemAndReplaceOldSlots() {
        var manager = new TrackedOrderManager(new BazaarData());
        var highlights = new OrderHighlightManager(() -> true);
        var value = new OrderValueComponent();
        manager.afterOrderSync(snapshot -> {
            highlights.sync(manager.getTrackedOrders(), snapshot);
            value.sync(snapshot);
        });
        var active = new UnfilledOrderInfo("Product", OrderType.Buy, 10, 5.0, 4, 2, 10);
        manager.syncOrders(List.of(active));
        manager.getTrackedOrders().getFirst().status = new OrderStatus.Undercut(1.0);
        Assertions.assertEquals(OrderHighlightManager.UNDERCUT_COLOR, highlights.getHighlight(10).orElseThrow());
        Assertions.assertEquals(40.0, value.currentBreakdown().total());

        manager.syncOrders(List.of(
            new ExpiredOrderInfo("Product", OrderType.Buy, 10, 5.0, 4, 2, 11),
            new ExpiredOrderInfo("Sell Product", OrderType.Sell, 8, 7.0, 8, 11, 12),
            new FilledOrderInfo("Filled Product", OrderType.Buy, 1, 3.0, 1, 1, 13)));

        Assertions.assertTrue(manager.currentOrders().isEmpty());
        Assertions.assertTrue(manager.creationOrder().isEmpty());
        Assertions.assertEquals(1, manager.filledOrderCount());
        Assertions.assertTrue(highlights.getHighlight(10).isEmpty());
        Assertions.assertEquals(OrderHighlightManager.EXPIRED_COLOR, highlights.getHighlight(11).orElseThrow());
        Assertions.assertEquals(OrderHighlightManager.EXPIRED_COLOR, highlights.getHighlight(12).orElseThrow());
        Assertions.assertEquals(OrderHighlightManager.FILLED_COLOR, highlights.getHighlight(13).orElseThrow());
        Assertions.assertNull(highlights.getTrackedOrder(11));
        Assertions.assertNull(highlights.getTrackedOrder(12));
        Assertions.assertEquals(24.0, value.currentBreakdown().total());

        manager.syncOrders(List.of());

        Assertions.assertTrue(highlights.getHighlight(11).isEmpty());
        Assertions.assertTrue(highlights.getHighlight(12).isEmpty());
        Assertions.assertEquals(0.0, value.currentBreakdown().total());
        Assertions.assertEquals(0, manager.filledOrderCount());
    }

    @Test
    void expiredHighlightRespectsSettingAndClear() {
        var enabled = new AtomicBoolean(true);
        var highlights = new OrderHighlightManager(enabled::get);
        List<OrderInfo> snapshot = List.of(
            new ExpiredOrderInfo("Product", OrderType.Buy, 10, 5.0, 0, 0, 11));
        highlights.sync(List.of(), snapshot);
        Assertions.assertEquals(OrderHighlightManager.EXPIRED_COLOR, highlights.getHighlight(11).orElseThrow());

        enabled.set(false);
        Assertions.assertTrue(highlights.getHighlight(11).isEmpty());
        enabled.set(true);
        highlights.clear();
        Assertions.assertTrue(highlights.getHighlight(11).isEmpty());
    }
}
