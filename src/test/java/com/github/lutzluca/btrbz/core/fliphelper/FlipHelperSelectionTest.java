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
    void filledBuySelectsRuntimeProductWithoutConversionIndexEntry() throws ReflectiveOperationException {
        var fixture = fixture();
        fixture.helper().onOrderClick(filledBuy());

        Assertions.assertTrue(fixture.market().resolveIndexedProduct(PRODUCT).isEmpty());
        Assertions.assertEquals(PRODUCT, fixture.context().getSelectedProduct().orElseThrow());
        Assertions.assertEquals(PRODUCT, field(fixture.helper(), "potentialFlipProduct").get(fixture.helper()));
    }

    @Test
    void disabledHelperStillSelectsFilledBuyForOrderBook() throws ReflectiveOperationException {
        var fixture = fixture(false);
        fixture.helper().onOrderClick(filledBuy());

        Assertions.assertEquals(PRODUCT, fixture.context().getSelectedProduct().orElseThrow());
        Assertions.assertNull(field(fixture.helper(), "potentialFlipProduct").get(fixture.helper()));
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
        var reply = new Gson().fromJson("""
            {"products":{"TEST":{"buy_summary":[{"pricePerUnit":110}]}}}
            """, SkyBlockBazaarReply.class);
        market.onUpdate(reply.getProducts());
        var context = new FlipProductContext(market);
        return new Fixture(market, context, new FlipHelper(market, context, null, null, () -> helperEnabled));
    }

    private static Field field(FlipHelper helper, String name) throws ReflectiveOperationException {
        var field = helper.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private record Fixture(BazaarData market, FlipProductContext context, FlipHelper helper) {}
}
