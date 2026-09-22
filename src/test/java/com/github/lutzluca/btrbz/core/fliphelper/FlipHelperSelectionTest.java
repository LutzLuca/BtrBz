package com.github.lutzluca.btrbz.core.fliphelper;

import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.data.conversions.ConversionIndex;
import com.github.lutzluca.btrbz.data.conversions.ConversionIndexService;
import com.google.gson.Gson;
import java.lang.reflect.Field;
import java.util.List;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class FlipHelperSelectionTest {

    private static final ProductIdentity PRODUCT = ProductIdentity.fromRuntime("UI Product", "TEST", null);

    @Test
    void filledBuyKeepsRuntimeProductUntilMarketQuoteArrives() {
        var fixture = fixture();
        Assertions.assertFalse(fixture.market().hasMarketData());
        fixture.helper().onOrderClick(filledBuy());

        Assertions.assertTrue(fixture.market().resolveIndexedProduct(PRODUCT).isEmpty());
        Assertions.assertEquals(PRODUCT, fixture.context().getSelectedProduct().orElseThrow());
        Assertions.assertTrue(fixture.helper().getFlipPrice().isEmpty());

        fixture.market().onUpdate(reply("OTHER", 40).getProducts());
        Assertions.assertTrue(fixture.market().hasMarketData());
        Assertions.assertFalse(fixture.market().contains(PRODUCT));
        Assertions.assertEquals(PRODUCT, fixture.context().getSelectedProduct().orElseThrow());
        Assertions.assertTrue(fixture.helper().getFlipPrice().isEmpty());

        fixture.market().onUpdate(reply("TEST", 110).getProducts());
        Assertions.assertEquals(PRODUCT, fixture.context().getSelectedProduct().orElseThrow());
        Assertions.assertEquals(109.9, fixture.helper().getFlipPrice().orElseThrow(), 0.000001);
    }

    @Test
    void disabledHelperStillSelectsFilledBuyForOrderBook() {
        var fixture = fixture(false);
        fixture.market().onUpdate(reply("TEST", 110).getProducts());
        fixture.helper().onOrderClick(filledBuy());

        Assertions.assertEquals(PRODUCT, fixture.context().getSelectedProduct().orElseThrow());
        Assertions.assertTrue(fixture.helper().getFlipPrice().isEmpty());
    }

    @Test
    void filledBuyWithoutBazaarIdDoesNotSelectOrSuggestPrice() {
        var fixture = fixture();
        fixture.market().onUpdate(reply("TEST", 110).getProducts());

        var unresolved = new OrderInfo.FilledOrderInfo(
            ProductIdentity.fromName("UI Product"), "UI Product", OrderType.Buy, 10, 100, 10, 10, 0);
        fixture.helper().onOrderClick(unresolved);

        Assertions.assertTrue(fixture.context().getSelectedProduct().isEmpty());
        Assertions.assertTrue(fixture.helper().getFlipPrice().isEmpty());
    }

    @Test
    void rejectedOrdersClearPreviousFlipAndProductSelection() throws ReflectiveOperationException {
        var fixture = fixture();
        var rejected = List.<OrderInfo>of(
            new OrderInfo.UnfilledOrderInfo(PRODUCT, "UI Product", OrderType.Buy, 10, 100, 0, 0, 1),
            new OrderInfo.UnfilledOrderInfo(PRODUCT, "UI Product", OrderType.Sell, 10, 100, 0, 0, 2),
            new OrderInfo.ExpiredOrderInfo(PRODUCT, "UI Product", OrderType.Buy, 10, 100, 0, 0, 3),
            new OrderInfo.ExpiredOrderInfo(PRODUCT, "UI Product", OrderType.Buy, 10, 100, 4, 4, 4));

        for (var order : rejected) {
            fixture.helper().onOrderClick(filledBuy());
            field(fixture.helper(), "pendingFlip").setBoolean(fixture.helper(), true);

            fixture.helper().onOrderClick(order);

            Assertions.assertTrue(fixture.context().getSelectedProduct().isEmpty(), order.toString());
            Assertions.assertNull(field(fixture.helper(), "potentialFlipProduct").get(fixture.helper()),
                order.toString());
            Assertions.assertFalse(field(fixture.helper(), "pendingFlip").getBoolean(fixture.helper()),
                order.toString());
        }
    }

    private static OrderInfo.FilledOrderInfo filledBuy() {
        return new OrderInfo.FilledOrderInfo(PRODUCT, "UI Product", OrderType.Buy, 10, 100, 10, 10, 0);
    }

    private static Fixture fixture() {
        return fixture(true);
    }

    private static Fixture fixture(boolean helperEnabled) {
        var market = new BazaarData(new ConversionIndexService(ConversionIndex.empty()));
        var context = new FlipProductContext(market);
        return new Fixture(market, context, new FlipHelper(market, context, null, null, () -> helperEnabled));
    }

    private static SkyBlockBazaarReply reply(String productId, double price) {
        return new Gson().fromJson(
            "{\"products\":{\"" + productId + "\":{\"buy_summary\":[{\"pricePerUnit\":" + price + "}]}}}",
            SkyBlockBazaarReply.class);
    }

    private static Field field(FlipHelper helper, String name) throws ReflectiveOperationException {
        var field = helper.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private record Fixture(BazaarData market, FlipProductContext context, FlipHelper helper) {}
}
