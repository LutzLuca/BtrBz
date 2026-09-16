package com.github.lutzluca.btrbz.core.alert;

import com.github.lutzluca.btrbz.core.alert.AlertType.PriceSource;
import com.github.lutzluca.btrbz.core.alert.AlertType.Direction;
import com.github.lutzluca.btrbz.data.BazaarData.MarketPrices;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AlertTypeTest {

    @Test
    void comparisonsAreInclusiveAndIndependentOfPriceSource() {
        for (var source : PriceSource.values()) {
            var below = new AlertType(source, Direction.Below);
            var above = new AlertType(source, Direction.Above);
            Assertions.assertTrue(below.isReached(90.0, 100.0));
            Assertions.assertFalse(below.isReached(110.0, 100.0));
            Assertions.assertTrue(above.isReached(110.0, 100.0));
            Assertions.assertFalse(above.isReached(90.0, 100.0));
            Assertions.assertTrue(below.isReached(100.0, 100.0));
            Assertions.assertTrue(above.isReached(100.0, 100.0));
            Assertions.assertFalse(below.isReached(Double.NaN, 100.0));
            Assertions.assertFalse(above.isReached(Double.POSITIVE_INFINITY, 100.0));
        }
    }

    @Test
    void priceSourcesResolveInstantTradesAndRejectInvalidQuotes() {
        var prices = new MarketPrices(Optional.of(100.0), Optional.of(110.0));
        Assertions.assertEquals(Optional.of(110.0), PriceSource.Buy.price(prices));
        Assertions.assertEquals(Optional.of(100.0), PriceSource.Sell.price(prices));
        Assertions.assertTrue(
            PriceSource.Buy.price(new MarketPrices(Optional.empty(), Optional.of(Double.NaN))).isEmpty());
        Assertions.assertTrue(
            PriceSource.Sell.price(new MarketPrices(Optional.of(0.0), Optional.empty())).isEmpty());
    }
}
