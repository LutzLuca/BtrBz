package com.github.lutzluca.btrbz.core.fliphelper;

import com.github.lutzluca.btrbz.core.Activation;
import com.github.lutzluca.btrbz.core.FeatureRuntime;
import com.github.lutzluca.btrbz.core.trackedorders.TrackedOrderManager;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.google.gson.Gson;
import java.util.Map;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply.Product;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class FlipHelperTest {
    @Test
    void suspensionCancelsUnsentFlipButRetainsTheSelectionAndRestoresItsQuote() throws Exception {
        var data = new BazaarData();
        var context = new FlipProductContext();
        var selected = ProductIdentity.fromRuntime("Test Product", "TEST", null);
        try (var submissions = new FlipSubmissionTracker(); var orders = new TrackedOrderManager(data)) {
            var helper = new FlipHelper(data, context, submissions, orders);
            var runtime = new FeatureRuntime(new Activation(() -> true, () -> true, _ -> {}),
                data, orders, () -> {}, helper::cancelPendingFlip, helper::resetWorkflow);
            runtime.activate();
            helper.onOrderClick(filledBuy(selected));
            runtime.onMarketUpdate(products("TEST", 110));
            var pending = FlipHelper.class.getDeclaredField("pendingFlip");
            pending.setAccessible(true);
            pending.setBoolean(helper, true);

            runtime.hibernate();

            Assertions.assertFalse(pending.getBoolean(helper));
            Assertions.assertEquals(selected, context.getSelectedProduct().orElseThrow());
            Assertions.assertTrue(context.getFlipPrice(data).isEmpty());

            runtime.recover(BazaarData.prepareSnapshot(products("TEST", 120)));

            Assertions.assertFalse(pending.getBoolean(helper));
            Assertions.assertEquals(selected, context.getSelectedProduct().orElseThrow());
            Assertions.assertEquals(119.9, context.getFlipPrice(data).orElseThrow(), 0.000001);

            runtime.deactivate();

            Assertions.assertTrue(context.getSelectedProduct().isEmpty());
        }
    }

    @Test
    void acceptedOrderObservationDuringHibernateSelectsTheRecoveredFlipProduct() {
        var data = new BazaarData();
        var context = new FlipProductContext();
        var first = ProductIdentity.fromRuntime("First Product", "FIRST", null);
        var next = ProductIdentity.fromRuntime("Next Product", "NEXT", null);
        try (var submissions = new FlipSubmissionTracker(); var orders = new TrackedOrderManager(data)) {
            var helper = new FlipHelper(data, context, submissions, orders);
            var runtime = new FeatureRuntime(new Activation(() -> true, () -> true, _ -> {}),
                data, orders, () -> {}, helper::cancelPendingFlip, helper::resetWorkflow);
            runtime.activate();
            helper.onOrderClick(filledBuy(first));
            runtime.hibernate();

            helper.onOrderClick(filledBuy(next));

            Assertions.assertTrue(runtime.isHibernating());
            Assertions.assertEquals(next, context.getSelectedProduct().orElseThrow());
            Assertions.assertTrue(context.getFlipPrice(data).isEmpty());
            runtime.recover(BazaarData.prepareSnapshot(products("NEXT", 130)));
            Assertions.assertEquals(next, context.getSelectedProduct().orElseThrow());
            Assertions.assertEquals(129.9, context.getFlipPrice(data).orElseThrow(), 0.000001);
            helper.resetWorkflow();
            Assertions.assertTrue(context.getSelectedProduct().isEmpty());
        }
    }

    private static OrderInfo.FilledOrderInfo filledBuy(ProductIdentity product) {
        return new OrderInfo.FilledOrderInfo(product, product.strippedName(), OrderType.Buy,
            10, 100, 10, 10, 0);
    }

    private static Map<String, Product> products(String id, double price) {
        return new Gson().fromJson("""
            {"products":{"%s":{"buy_summary":[{"pricePerUnit":%s,"amount":100,"orders":2}]}}}
            """.formatted(id, price), SkyBlockBazaarReply.class).getProducts();
    }
}
