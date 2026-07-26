package com.github.lutzluca.btrbz.core.widgets.orderbook;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import org.junit.jupiter.api.Test;

class OrderBookPriceComponentTest {
    @Test
    void adjustsPriceForOrderSideAndMinimumPrice() {
        assertEquals(100.1, OrderBookPriceComponent.adjustPrice(100.0, OrderType.Buy));
        assertEquals(99.9, OrderBookPriceComponent.adjustPrice(100.0, OrderType.Sell));
        assertEquals(0.1, OrderBookPriceComponent.adjustPrice(0.05, OrderType.Sell));
    }
}
