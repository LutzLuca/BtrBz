package com.github.lutzluca.btrbz.core.fliphelper;

import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.data.conversions.ConversionIndex;
import com.github.lutzluca.btrbz.data.conversions.ConversionIndexService;
import com.google.gson.Gson;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class FlipProductContextTest {

    private static final ProductIdentity PRODUCT = ProductIdentity.fromRuntime("Test Product", "TEST", null);

    private final BazaarData market = new BazaarData(new ConversionIndexService(ConversionIndex.empty()));
    private final FlipProductContext context = new FlipProductContext();

    @Test
    void retainsRuntimeIdentityBeforeQuotesArriveAndWhenTheProductDisappears() {
        this.context.selectOrder(filledBuy(PRODUCT));

        Assertions.assertTrue(this.market.resolveIndexedProduct(PRODUCT).isEmpty());
        Assertions.assertEquals(PRODUCT, this.context.getSelectedProduct().orElseThrow());
        Assertions.assertTrue(this.context.getFlipPrice(this.market).isEmpty());

        this.publish("OTHER", 500);
        Assertions.assertEquals(PRODUCT, this.context.getSelectedProduct().orElseThrow());
        Assertions.assertTrue(this.context.getFlipPrice(this.market).isEmpty());

        this.publish("TEST", 110);
        Assertions.assertEquals(109.9, this.context.getFlipPrice(this.market).orElseThrow(), 0.000001);

        this.publish("TEST", 120);
        Assertions.assertEquals(119.9, this.context.getFlipPrice(this.market).orElseThrow(), 0.000001);

        this.market.publishSnapshot(BazaarData.MarketSnapshot.fromProducts(Map.of()));
        Assertions.assertEquals(PRODUCT, this.context.getSelectedProduct().orElseThrow());
        Assertions.assertTrue(this.context.getFlipPrice(this.market).isEmpty());

        this.publish("TEST", 130);
        Assertions.assertEquals(129.9, this.context.getFlipPrice(this.market).orElseThrow(), 0.000001);
    }

    @Test
    void keepsSelectionWhileRejectingUnusableQuotesAndRespectingTheMinimumPrice() {
        var cases = List.of(
            new QuoteCase("empty sell-offer book", null, Optional.empty()),
            new QuoteCase("zero quote", "0", Optional.empty()),
            new QuoteCase("negative quote", "-1", Optional.empty()),
            new QuoteCase("NaN quote", "NaN", Optional.empty()),
            new QuoteCase("infinite quote", "Infinity", Optional.empty()),
            new QuoteCase("minimum quote", "0.1", Optional.of(0.1)));

        for (var example : cases) {
            this.context.selectOrder(filledBuy(PRODUCT));
            this.publish("TEST", example.quote());

            Assertions.assertEquals(PRODUCT, this.context.getSelectedProduct().orElseThrow(), example.description());
            Assertions.assertEquals(example.expectedPrice(), this.context.getFlipPrice(this.market),
                example.description());
        }
    }

    @Test
    void rejectedOrdersClearThePreviousSelectionAndPrice() {
        this.publish("TEST", 110);
        var rejected = List.<OrderInfo>of(
            new OrderInfo.UnfilledOrderInfo(PRODUCT, "Test Product", OrderType.Buy, 10, 100, 4, 4, 1),
            new OrderInfo.FilledOrderInfo(PRODUCT, "Test Product", OrderType.Sell, 10, 100, 10, 1000, 2),
            new OrderInfo.ExpiredOrderInfo(PRODUCT, "Test Product", OrderType.Buy, 10, 100, 10, 10, 3),
            filledBuy(ProductIdentity.fromName("Test Product")),
            filledBuy(ProductIdentity.fromRuntime("Enchanted Book", "ENCHANTED_BOOK", null)));

        for (var order : rejected) {
            this.context.selectOrder(filledBuy(PRODUCT));
            this.context.selectOrder(order);

            Assertions.assertTrue(this.context.getSelectedProduct().isEmpty(), order.toString());
            Assertions.assertTrue(this.context.getFlipPrice(this.market).isEmpty(), order.toString());
        }
    }

    @Test
    void clearingSelectionPreventsLaterQuotesFromRestoringIt() {
        this.context.selectOrder(filledBuy(PRODUCT));
        this.publish("TEST", 110);
        this.context.clearProduct();
        this.publish("TEST", 120);

        Assertions.assertTrue(this.context.getSelectedProduct().isEmpty());
        Assertions.assertTrue(this.context.getFlipPrice(this.market).isEmpty());
    }

    private static OrderInfo.FilledOrderInfo filledBuy(ProductIdentity product) {
        return new OrderInfo.FilledOrderInfo(product, product.strippedName(), OrderType.Buy, 10, 100, 10, 10, 0);
    }

    private void publish(String productId, double sellPrice) {
        this.publish(productId, Double.toString(sellPrice));
    }

    private void publish(String productId, String sellPrice) {
        var sellSummary = sellPrice == null
            ? "[]"
            : "[{\"pricePerUnit\":" + sellPrice + ",\"amount\":100,\"orders\":2}]";
        var reply = new Gson().fromJson("""
            {"products":{"%s":{
                "sell_summary":[{"pricePerUnit":80,"amount":100,"orders":2}],
                "buy_summary":%s
            }}}
            """.formatted(productId, sellSummary), SkyBlockBazaarReply.class);
        this.market.publishSnapshot(BazaarData.MarketSnapshot.fromProducts(reply.getProducts()));
    }

    private record QuoteCase(String description, String quote, Optional<Double> expectedPrice) {}
}
