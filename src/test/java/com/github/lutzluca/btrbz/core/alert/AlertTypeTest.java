package com.github.lutzluca.btrbz.core.alert;

import com.github.lutzluca.btrbz.core.alert.AlertType.PriceSource;
import com.github.lutzluca.btrbz.core.alert.AlertType.Side;
import com.github.lutzluca.btrbz.data.BazaarData.MarketPrices;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AlertTypeTest {

    @Test
    void mapsEveryPersistedTypeToItsSideAndPriceSource() {
        Assertions.assertEquals(AlertType.BuyOrder, AlertType.of(Side.Buy, PriceSource.BuyOrder));
        Assertions.assertEquals(AlertType.InstaBuy, AlertType.of(Side.Buy, PriceSource.SellOffer));
        Assertions.assertEquals(AlertType.InstaSell, AlertType.of(Side.Sell, PriceSource.BuyOrder));
        Assertions.assertEquals(AlertType.SellOffer, AlertType.of(Side.Sell, PriceSource.SellOffer));
    }

    @Test
    void buyAndSellUseOppositeThresholdDirections() {
        Assertions.assertTrue(AlertType.InstaBuy.isReached(90.0, 100.0));
        Assertions.assertFalse(AlertType.InstaBuy.isReached(110.0, 100.0));
        Assertions.assertTrue(AlertType.InstaSell.isReached(110.0, 100.0));
        Assertions.assertFalse(AlertType.InstaSell.isReached(90.0, 100.0));
    }

    @Test
    void priceSourcesExposeExplicitReferencesAndOnlyValidPrices() {
        var prices = new MarketPrices(Optional.of(100.0), Optional.of(110.0));
        Assertions.assertEquals("buy_order", PriceSource.BuyOrder.reference());
        Assertions.assertEquals("sell_offer", PriceSource.SellOffer.reference());
        Assertions.assertEquals(Optional.of(100.0), PriceSource.BuyOrder.price(prices));
        Assertions.assertEquals(Optional.of(110.0), PriceSource.SellOffer.price(prices));
        Assertions.assertTrue(
            PriceSource.BuyOrder.price(new MarketPrices(Optional.of(Double.NaN), Optional.empty())).isEmpty());
        Assertions.assertTrue(
            PriceSource.SellOffer.price(new MarketPrices(Optional.empty(), Optional.of(0.0))).isEmpty());
    }
}
