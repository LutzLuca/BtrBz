package com.github.lutzluca.btrbz.core;

import com.github.lutzluca.btrbz.core.OrderProtectionManager.OrderProtectionConfig;
import com.github.lutzluca.btrbz.core.OrderProtectionManager.OrderValidator;
import com.github.lutzluca.btrbz.core.OrderProtectionManager.PercentageExceeded;
import com.github.lutzluca.btrbz.core.OrderProtectionManager.SpreadCrossing;
import com.github.lutzluca.btrbz.core.OrderProtectionManager.ValidationResult;
import com.github.lutzluca.btrbz.core.OrderProtectionManager.ValidationUnavailable;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.BazaarData.MarketPrices;
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
import org.junit.jupiter.api.Test;

class OrderProtectionManagerTest {

    @Test
    void exemptsOneTickButChecksLargerImprovementsOnBothSides() {
        var config = new OrderProtectionConfig();
        config.maxBuyOrderUndercut = 10;
        config.maxSellOfferUndercut = 10;

        Assertions.assertFalse(evaluate(OrderType.Buy, 0.8, 0.7, 2, config).protect());
        Assertions.assertFalse(evaluate(OrderType.Sell, 0.7, 0.1, 0.8, config).protect());
        Assertions.assertInstanceOf(PercentageExceeded.class, evaluate(OrderType.Buy, 0.9, 0.7, 2, config));
        Assertions.assertInstanceOf(PercentageExceeded.class, evaluate(OrderType.Sell, 0.6, 0.1, 0.8, config));

        config.maxBuyOrderUndercut = 0;
        config.maxSellOfferUndercut = 0;
        Assertions.assertFalse(evaluate(OrderType.Buy, 0.7, 0.7, 2, config).protect());
        Assertions.assertFalse(evaluate(OrderType.Sell, 0.9, 0.1, 0.8, config).protect());
    }

    @Test
    void blocksAtTheDecimalPercentageBoundaryOnBothSides() {
        var config = new OrderProtectionConfig();
        config.maxBuyOrderUndercut = 50;
        config.maxSellOfferUndercut = 50;

        Assertions.assertFalse(evaluate(OrderType.Buy, 1.4, 1, 2, config).protect());
        Assertions.assertInstanceOf(PercentageExceeded.class, evaluate(OrderType.Buy, 1.5, 1, 2, config));
        Assertions.assertFalse(evaluate(OrderType.Sell, 0.6, 0.1, 1, config).protect());
        Assertions.assertInstanceOf(PercentageExceeded.class, evaluate(OrderType.Sell, 0.5, 0.1, 1, config));
        Assertions.assertInstanceOf(PercentageExceeded.class, evaluate(OrderType.Buy, 0.6, 0.4, 2, config));
        Assertions.assertInstanceOf(PercentageExceeded.class, evaluate(OrderType.Sell, 0.2, 0.1, 0.4, config));
    }

    @Test
    void checksSpreadCrossingBeforeTheOneTickExemption() {
        var config = new OrderProtectionConfig();
        config.maxBuyOrderUndercut = 10;
        config.maxSellOfferUndercut = 10;

        Assertions.assertInstanceOf(SpreadCrossing.class, evaluate(OrderType.Buy, 0.8, 0.7, 0.8, config));
        Assertions.assertInstanceOf(SpreadCrossing.class, evaluate(OrderType.Sell, 0.7, 0.7, 0.8, config));

        config.blockUndercutOfOpposing = false;
        Assertions.assertFalse(evaluate(OrderType.Buy, 0.8, 0.7, 0.8, config).protect());
        Assertions.assertFalse(evaluate(OrderType.Sell, 0.7, 0.7, 0.8, config).protect());
    }

    @Test
    void distinguishesMissingProductsFromPresentEmptySides() {
        var market = new BazaarData(new ConversionIndexService(new ConversionIndex(
            ConversionIndex.SCHEMA_VERSION, "now", null,
            Map.of("TEST", new ConversionProductEntry("Test Product", new ProductNameSource.Derived())))));
        var config = new OrderProtectionConfig();
        var order = new OutstandingOrderInfo(
            ProductIdentity.fromRuntime("Test Product", "TEST", null), "Test Product", OrderType.Buy, 1, 0.8, 0.8);

        publish(market, """
            {"products":{"OTHER":{"sell_summary":[],"buy_summary":[]}}}
            """);
        Assertions.assertInstanceOf(ValidationUnavailable.class, OrderValidator.validate(order, market, config)
            .validationResult());
        var unindexedOrder = order.withProduct(ProductIdentity.fromRuntime("Other Product", "OTHER", null));
        Assertions.assertInstanceOf(ValidationUnavailable.class, OrderValidator.validate(unindexedOrder, market, config)
            .validationResult());

        publish(market, """
            {"products":{"TEST":{"sell_summary":[],"buy_summary":[]}}}
            """);
        Assertions.assertFalse(OrderValidator.validate(order, market, config).validationResult().protect());

        publish(market, """
            {"products":{"TEST":{"sell_summary":[],"buy_summary":[{"pricePerUnit":0.8,"amount":1,"orders":1}]}}}
            """);
        Assertions.assertInstanceOf(SpreadCrossing.class, OrderValidator.validate(order, market, config)
            .validationResult());

        publish(market, """
            {"products":{"TEST":{"sell_summary":[{"pricePerUnit":0.6,"amount":1,"orders":1}],"buy_summary":[]}}}
            """);
        Assertions.assertInstanceOf(PercentageExceeded.class, OrderValidator.validate(order, market, config)
            .validationResult());

        market.clearMarketData();
        config.blockUndercutPercentage = false;
        config.blockUndercutOfOpposing = false;
        Assertions.assertFalse(OrderValidator.validate(order, market, config).validationResult().protect());
    }

    private static ValidationResult evaluate(
        OrderType type,
        double proposedPrice,
        double bestBuy,
        double bestSell,
        OrderProtectionConfig config
    ) {
        var order = new OutstandingOrderInfo("Test Product", type, 1, proposedPrice, proposedPrice);
        return OrderValidator.validateOrder(order, new MarketPrices(Optional.of(bestBuy), Optional.of(bestSell)),
            config);
    }

    private static void publish(BazaarData market, String json) {
        market.onUpdate(new Gson().fromJson(json, SkyBlockBazaarReply.class).getProducts());
    }
}
