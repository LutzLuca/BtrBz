package com.github.lutzluca.btrbz.core;

import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.OrderModels.OutstandingOrderInfo;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.data.conversions.ConversionIndex;
import com.github.lutzluca.btrbz.data.conversions.ConversionIndexService;
import com.github.lutzluca.btrbz.data.conversions.ConversionProductEntry;
import com.github.lutzluca.btrbz.data.conversions.ProductNameSource;
import com.google.gson.Gson;
import java.util.Map;
import java.util.Optional;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Market availability across activation sessions")
class MarketAvailabilityTest {
    private final IndexedProduct product = new IndexedProduct("TEST", "Test Product");
    private final ProductIdentity identity = ProductIdentity.fromIndex(this.product);
    private final BazaarData market = new BazaarData(new ConversionIndexService(new ConversionIndex(
        ConversionIndex.SCHEMA_VERSION, "2026-09-09T00:00:00Z", null,
        Map.of("TEST", new ConversionProductEntry("Test Product", new ProductNameSource.Derived())))));

    private void publish(double buyPrice) {
        var reply = new Gson().fromJson("""
            {"products":{"TEST":{
                "sell_summary":[{"pricePerUnit":%s,"amount":100,"orders":2}],
                "buy_summary":[{"pricePerUnit":110,"amount":100,"orders":2}]
            }}}
            """.formatted(buyPrice), SkyBlockBazaarReply.class);
        this.market.onUpdate(reply.getProducts());
    }

    @Test
    void clearingInvalidatesDirectAndSubscribedPricesUntilAnotherReply() {
        var tracked = new BazaarData.TrackedProduct(this.market, this.product);
        Assertions.assertFalse(this.market.hasMarketData());
        this.publish(100);
        Assertions.assertEquals(Optional.of(100.0), tracked.getBuyOrderPrice());
        long revision = this.market.marketChanges().revision();

        this.market.clearMarketData();

        Assertions.assertFalse(this.market.hasMarketData());
        Assertions.assertTrue(this.market.getMarketPrices(this.identity).highestBuyOrderPrice().isEmpty());
        Assertions.assertTrue(this.market.getMarketPrices(this.identity).lowestSellOfferPrice().isEmpty());
        Assertions.assertTrue(this.market.getOrderLists(this.identity).buyOrders().isEmpty());
        Assertions.assertTrue(this.market.productSpread(this.identity).isEmpty());
        Assertions.assertTrue(this.market.calculateQueuePosition(this.identity, OrderType.Buy, 90).isEmpty());
        Assertions.assertTrue(tracked.getBuyOrderPrice().isEmpty());
        Assertions.assertTrue(tracked.getSellOfferPrice().isEmpty());
        Assertions.assertTrue(this.market.marketChanges().revision() > revision);
        Assertions.assertEquals(Optional.of(this.product), this.market.resolveProductId("TEST"));

        this.publish(105);
        Assertions.assertTrue(this.market.hasMarketData());
        Assertions.assertEquals(Optional.of(105.0), tracked.getBuyOrderPrice());
        tracked.destroy();
    }

    @Test
    void protectionBlocksWhileWaitingAndUsesTheNewSessionsPrices() {
        var config = new OrderProtectionManager.OrderProtectionConfig();
        var order = new OutstandingOrderInfo(this.identity, "Test Product", OrderType.Buy, 1, 105, 105);
        this.publish(100);
        Assertions.assertFalse(OrderProtectionManager.OrderValidator.validate(order, this.market, config)
            .validationResult().protect());

        this.market.clearMarketData();
        var unavailable = OrderProtectionManager.OrderValidator.validate(order, this.market, config).validationResult();
        Assertions.assertTrue(unavailable.protect());
        Assertions.assertEquals("Bazaar prices are unavailable.", unavailable.reason());

        this.publish(50);
        Assertions.assertTrue(OrderProtectionManager.OrderValidator.validate(order, this.market, config)
            .validationResult().protect());

        this.market.clearMarketData();
        config.enabled = false;
        Assertions.assertFalse(OrderProtectionManager.OrderValidator.validate(order, this.market, config)
            .validationResult().protect());
    }
}
