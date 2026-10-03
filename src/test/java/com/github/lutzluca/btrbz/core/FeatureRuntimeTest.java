package com.github.lutzluca.btrbz.core;

import com.github.lutzluca.btrbz.core.alert.AlertCondition;
import com.github.lutzluca.btrbz.core.alert.AlertConfig;
import com.github.lutzluca.btrbz.core.alert.AlertDefinition;
import com.github.lutzluca.btrbz.core.alert.AlertManager;
import com.github.lutzluca.btrbz.core.alert.AlertType;
import com.github.lutzluca.btrbz.core.alert.ReachedAlert;
import com.github.lutzluca.btrbz.core.trackedorders.TrackedOrderManager;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply.Product;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class FeatureRuntimeTest {
    private static final ProductIdentity PRODUCT = ProductIdentity.fromRuntime("Test Product", "TEST", null);

    @Test
    void hibernationRetainsTheSessionWhileDeactivationEndsIt() {
        var data = new BazaarData();
        try (var orders = orders(data)) {
            var owner = new AtomicReference<FeatureRuntime>();
            var operations = new ArrayList<String>();
            var runtime = new FeatureRuntime(new Activation(() -> true, () -> true, _ -> {}), data, orders,
                () -> operations.add("start"), () -> {
                    Assertions.assertTrue(owner.get().isRunning());
                    Assertions.assertFalse(owner.get().isActive());
                    operations.add("suspend");
                }, () -> {
                    Assertions.assertFalse(owner.get().isRunning());
                    operations.add("end");
                });
            owner.set(runtime);
            data.addListener(orders::onBazaarUpdate);
            runtime.activate();
            orders.syncOrders(List.of(order(100)));
            var id = orders.currentOrders().getFirst().id();
            runtime.onMarketUpdate(products(100));

            runtime.hibernate();
            runtime.activate();
            runtime.hibernate();
            runtime.onMarketUpdate(products(110));

            Assertions.assertTrue(runtime.isHibernating());
            Assertions.assertFalse(data.hasMarketData());
            Assertions.assertEquals(id, orders.currentOrders().getFirst().id());
            Assertions.assertEquals(3, orders.currentOrders().getFirst().fillAmountSnapshot());
            Assertions.assertEquals(List.of("start", "suspend"), operations);

            runtime.deactivate();
            runtime.deactivate();
            runtime.recover(BazaarData.prepareSnapshot(products(110)));
            runtime.onMarketUpdate(products(110));

            Assertions.assertFalse(runtime.isRunning());
            Assertions.assertFalse(data.hasMarketData());
            Assertions.assertTrue(orders.currentOrders().isEmpty());
            Assertions.assertEquals(List.of("start", "suspend", "end"), operations);
        }
    }

    @Test
    void recoveryPublishesTheBaselineAndActiveGateBeforeNotifyingAlerts() {
        var data = new BazaarData();
        try (var orders = orders(data)) {
            var runtime = runtime(data, orders);
            var notices = new ArrayList<Publication>();
            var config = new AlertConfig();
            var alerts = new AlertManager(data, () -> config, () -> {}, reached -> notices.add(new Publication(
                runtime.isActive(), data.highestBuyOrderPrice(PRODUCT).orElseThrow(),
                orders.currentOrders().getFirst().status() instanceof OrderStatus.Undercut)));
            // Production registers alerts first; recovery cannot rely on the orders listener running first.
            data.addListener(alerts::onBazaarUpdate);
            data.addListener(orders::onBazaarUpdate);
            runtime.activate();
            runtime.hibernate();
            orders.syncOrders(List.of(order(100)));
            saveAlert(alerts);

            runtime.recover(BazaarData.prepareSnapshot(products(110)));

            Assertions.assertEquals(List.of(new Publication(true, 110, true)), notices);
            Assertions.assertTrue(alerts.alerts().isEmpty());
        }
    }

    @Test
    void failedBaselineKeepsAlertsAndMarketGatedUntilRetrySucceeds() {
        var data = new BazaarData();
        try (var orders = orders(data)) {
            var runtime = runtime(data, orders);
            var config = new AlertConfig();
            var notices = new ArrayList<ReachedAlert>();
            var alerts = new AlertManager(data, () -> config, () -> {}, notices::add);
            data.addListener(alerts::onBazaarUpdate);
            runtime.activate();
            runtime.hibernate();
            orders.syncOrders(List.of(order(110), order(90)));
            saveAlert(alerts);
            // This candidate reaches the alert, but its second level fails the real self-undercut baseline.
            var candidate = new Gson().fromJson("""
                {"products":{"TEST":{"sell_summary":[{"pricePerUnit":110,"orders":1},null]}}}
                """, SkyBlockBazaarReply.class);

            runtime.recover(BazaarData.prepareSnapshot(candidate.getProducts()));

            Assertions.assertTrue(runtime.isHibernating());
            Assertions.assertFalse(data.hasMarketData());
            Assertions.assertEquals(1, alerts.alerts().size());
            Assertions.assertTrue(notices.isEmpty());
            Assertions.assertTrue(
                orders.currentOrders().stream().allMatch(order -> order.status() instanceof OrderStatus.Unknown));

            runtime.recover(BazaarData.prepareSnapshot(products(110)));

            Assertions.assertTrue(runtime.isActive());
            Assertions.assertTrue(alerts.alerts().isEmpty());
            Assertions.assertEquals(1, notices.size());
        }
    }

    private static FeatureRuntime runtime(BazaarData data, TrackedOrderManager orders) {
        return new FeatureRuntime(new Activation(() -> true, () -> true, _ -> {}), data, orders,
            () -> {}, () -> {}, () -> {});
    }

    private static TrackedOrderManager orders(BazaarData data) {
        var config = new TrackedOrderManager.OrderManagerConfig();
        config.enabled = false;
        return new TrackedOrderManager(data, () -> config);
    }

    private static void saveAlert(AlertManager alerts) {
        alerts.saveAlert(null, new AlertDefinition(1, new IndexedProduct("TEST", "Test Product"),
            new AlertCondition.Price(new AlertType(AlertType.PriceSource.Sell, AlertType.Direction.Above), 105)))
            .get();
    }

    private static OrderInfo.UnfilledOrderInfo order(double price) {
        return new OrderInfo.UnfilledOrderInfo(PRODUCT, "Test Product", OrderType.Buy, 10, price, 3, 0, 10);
    }

    private static Map<String, Product> products(double price) {
        return new Gson().fromJson("""
            {"products":{"TEST":{
                "sell_summary":[{"pricePerUnit":%s,"amount":100,"orders":2}],
                "buy_summary":[{"pricePerUnit":120,"amount":100,"orders":2}]
            }}}
            """.formatted(price), SkyBlockBazaarReply.class).getProducts();
    }

    private record Publication(boolean active, double buyPrice, boolean undercut) {}
}
