package com.github.lutzluca.btrbz.core.orderprotection;

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
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Order protection against unavailable market data")
class OrderProtectionValidatorTest {
    private final IndexedProduct product = new IndexedProduct("TEST", "Test Product");
    private final ProductIdentity identity = ProductIdentity.fromIndex(this.product);
    private final BazaarData market = new BazaarData(new ConversionIndexService(new ConversionIndex(
        ConversionIndex.SCHEMA_VERSION, "2026-09-09T00:00:00Z", null,
        Map.of("TEST", new ConversionProductEntry("Test Product", new ProductNameSource.Derived())))));

    private void publish(double buyPrice) {
        this.publish(
            "TEST",
            "[{\"pricePerUnit\":%s,\"amount\":100,\"orders\":2}]".formatted(buyPrice),
            "[{\"pricePerUnit\":110,\"amount\":100,\"orders\":2}]");
    }

    private void publish(String productId, String sellSummary, String buySummary) {
        var reply = new Gson().fromJson("""
            {"products":{"%s":{"sell_summary":%s,"buy_summary":%s}}}
            """.formatted(productId, sellSummary, buySummary), SkyBlockBazaarReply.class);
        this.market.onUpdate(reply.getProducts());
    }

    @Test
    void protectionBlocksWhileWaitingAndUsesTheNewSessionsPrices() {
        var config = new OrderProtectionConfig();
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

    @Test
    void protectionRequiresAnIndexEntryEvenWhenTheProductIsInTheMarket() {
        this.publish("UNINDEXED", "[]", "[]");
        var order = new OutstandingOrderInfo(
            ProductIdentity.fromRuntime("Unindexed Product", "UNINDEXED", null),
            "Unindexed Product", OrderType.Buy, 1, 105, 105);

        var result = OrderProtectionManager.OrderValidator.validate(order, this.market, new OrderProtectionConfig())
            .validationResult();

        Assertions.assertTrue(result.protect());
        Assertions.assertTrue(result.reason().contains("Unknown or unresolved product"));
    }

    @Test
    void protectionBlocksAnIndexedProductMissingFromTheMarket() {
        this.publish("OTHER", "[]", "[]");
        var order = new OutstandingOrderInfo(this.identity, "Test Product", OrderType.Buy, 1, 105, 105);

        var result = OrderProtectionManager.OrderValidator.validate(order, this.market, new OrderProtectionConfig())
            .validationResult();

        Assertions.assertTrue(result.protect());
        Assertions.assertTrue(result.reason().contains("missing from Bazaar market data"));
    }

    @Test
    void protectionAllowsAnIndexedProductWithEmptyBookSides() {
        var order = new OutstandingOrderInfo(this.identity, "Test Product", OrderType.Buy, 1, 105, 105);
        var config = new OrderProtectionConfig();

        this.publish("TEST", "[]", "[]");
        var bothSidesEmpty = OrderProtectionManager.OrderValidator.validate(order, this.market, config);
        Assertions.assertFalse(bothSidesEmpty.validationResult().protect());

        this.publish("TEST", "[]", "[{\"pricePerUnit\":110,\"amount\":100,\"orders\":2}]");
        var buySideEmpty = OrderProtectionManager.OrderValidator.validate(order, this.market, config);
        Assertions.assertFalse(buySideEmpty.validationResult().protect());
    }
}
