package com.github.lutzluca.btrbz.data;

import com.google.gson.Gson;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import java.util.Map;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class BazaarProductActivityTest {
    @Test
    void mapsWholeSideTotalsAndWeeklyMovementToPlayerOrderSides() {
        var reply = new Gson().fromJson("""
            {"products":{"TEST":{
                "sell_summary":[{"pricePerUnit":10,"amount":2,"orders":1}],
                "buy_summary":[{"pricePerUnit":12,"amount":3,"orders":1}],
                "quick_status":{
                    "sellOrders":1234,"sellVolume":1842560,"sellMovingWeek":82656000,
                    "buyOrders":876,"buyVolume":964320,"buyMovingWeek":62193600
                }
            }}}
            """, SkyBlockBazaarReply.class);
        var snapshot = BazaarData.MarketSnapshot.fromProducts(reply.getProducts());
        var product = ProductIdentity.fromRuntime("Test", "TEST", null);
        var activity = snapshot.productActivity(product).orElseThrow();

        Assertions.assertEquals(10.0, snapshot.highestBuyOrderPrice(product).orElseThrow());
        Assertions.assertEquals(12.0, snapshot.lowestSellOfferPrice(product).orElseThrow());
        Assertions.assertEquals(1234, activity.buyOrders().orders());
        Assertions.assertEquals(1842560, activity.buyOrders().listedItems());
        Assertions.assertEquals(492000.0, activity.buyOrders().weeklyAverageHourlyMovement());
        Assertions.assertEquals(876, activity.sellOffers().orders());
        Assertions.assertEquals(964320, activity.sellOffers().listedItems());
        Assertions.assertEquals(370200.0, activity.sellOffers().weeklyAverageHourlyMovement());

        var market = new BazaarData();
        market.publishSnapshot(snapshot);
        Assertions.assertEquals(2.0, market.getEstimatedFillTimeMinutes(product, OrderType.Buy, 16_400).orElseThrow());
        Assertions.assertEquals(2.0, market.getEstimatedFillTimeMinutes(product, OrderType.Sell, 12_340).orElseThrow());
    }

    @Test
    void missingStatusIsUnavailableAndReportedZeroActivityIsZero() {
        var reply = new Gson().fromJson("""
            {"products":{"MISSING":{},"ZERO":{"quick_status":{
                "sellOrders":0,"sellVolume":0,"sellMovingWeek":0,
                "buyOrders":0,"buyVolume":0,"buyMovingWeek":0
            }}}}
            """, SkyBlockBazaarReply.class);
        var snapshot = BazaarData.MarketSnapshot.fromProducts(reply.getProducts());
        Assertions
            .assertTrue(snapshot.productActivity(ProductIdentity.fromRuntime("Missing", "MISSING", null)).isEmpty());
        Assertions.assertTrue(BazaarData.MarketSnapshot.fromProducts(Map.of())
            .productActivity(ProductIdentity.fromRuntime("Zero", "ZERO", null)).isEmpty());
        var zero = snapshot.productActivity(ProductIdentity.fromRuntime("Zero", "ZERO", null)).orElseThrow();
        Assertions.assertEquals(0.0, zero.buyOrders().weeklyAverageHourlyMovement());
        Assertions.assertEquals(0, zero.sellOffers().orders());
    }
}
