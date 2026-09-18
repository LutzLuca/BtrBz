package com.github.lutzluca.btrbz.core.widgets.ordervalue;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo.FilledOrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo.ExpiredOrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo.UnfilledOrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderValueComponentTest {
    @Test
    void separatesLockedPendingAndClaimableValues() {
        var unfilled = List.of(
            new UnfilledOrderInfo("Buy Product", OrderType.Buy, 10, 5.0, 4, 2, 0),
            new UnfilledOrderInfo("Sell Product", OrderType.Sell, 8, 7.0, 3, 11, 1));
        var filled = List.of(
            new FilledOrderInfo("Filled Buy", OrderType.Buy, 3, 4.0, 3, 3, 2),
            new FilledOrderInfo("Filled Sell", OrderType.Sell, 1, 9.0, 1, 13, 3));
        var breakdown = OrderValueComponent.calculateBreakdown(List.of(
            unfilled.get(0), unfilled.get(1), filled.get(0), filled.get(1)));
        assertEquals(30.0, breakdown.buyLocked());
        assertEquals(22.0, breakdown.buyItems());
        assertEquals(24.0, breakdown.sellClaimable());
        assertEquals(35.0, breakdown.sellPending());
        assertEquals(111.0, breakdown.total());
    }

    @Test
    void expiredPartialFillsRetainOnlyUnclaimedItemsAndProceeds() {
        var breakdown = OrderValueComponent.calculateBreakdown(List.of(
            new ExpiredOrderInfo("Buy Product", OrderType.Buy, 10, 5.0, 4, 2, 0),
            new ExpiredOrderInfo("Sell Product", OrderType.Sell, 8, 7.0, 3, 11, 1)));

        assertEquals(0.0, breakdown.buyLocked());
        assertEquals(10.0, breakdown.buyItems());
        assertEquals(11.0, breakdown.sellClaimable());
        assertEquals(0.0, breakdown.sellPending());
        assertEquals(21.0, breakdown.total());
    }

    @Test
    void expiredFullyFilledOrdersUseUnclaimedValueWithoutDoubleCounting() {
        var breakdown = OrderValueComponent.calculateBreakdown(List.of(
            new ExpiredOrderInfo("Buy Product", OrderType.Buy, 10, 5.0, 10, 2, 0),
            new ExpiredOrderInfo("Sell Product", OrderType.Sell, 8, 7.0, 8, 11, 1)));

        assertEquals(21.0, breakdown.total());
        assertEquals(0.0, breakdown.buyLocked());
        assertEquals(0.0, breakdown.sellPending());
    }

    @Test
    void expiredUnfilledOrAlreadyClaimedOrdersHaveNoValue() {
        var breakdown = OrderValueComponent.calculateBreakdown(List.of(
            new ExpiredOrderInfo("Unfilled Buy", OrderType.Buy, 10, 5.0, 0, 0, 0),
            new ExpiredOrderInfo("Claimed Sell", OrderType.Sell, 8, 7.0, 3, 0, 1)));

        assertEquals(0.0, breakdown.total());
    }
}
