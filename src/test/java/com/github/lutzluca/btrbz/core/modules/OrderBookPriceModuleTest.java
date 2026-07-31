package com.github.lutzluca.btrbz.core.modules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OrderBookPriceModuleTest {

    @Nested
    @DisplayName("price selection")
    class PriceSelection {

        @Test
        void adjustsPriceForOrderSideAndMinimumPrice() {
            assertEquals(100.1, OrderBookPriceModule.applyUndercut(100.0, OrderType.Buy));
            assertEquals(99.9, OrderBookPriceModule.applyUndercut(100.0, OrderType.Sell));
            assertEquals(0.1, OrderBookPriceModule.applyUndercut(0.05, OrderType.Sell));
        }
    }
}
