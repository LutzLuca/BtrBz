package com.github.lutzluca.btrbz.core.runtime;

import com.github.lutzluca.btrbz.core.alert.AlertCondition;
import com.github.lutzluca.btrbz.core.alert.AlertConfig;
import com.github.lutzluca.btrbz.core.alert.AlertDefinition;
import com.github.lutzluca.btrbz.core.alert.AlertManager;
import com.github.lutzluca.btrbz.core.alert.AlertType;
import com.github.lutzluca.btrbz.core.alert.ReachedAlert;
import com.github.lutzluca.btrbz.core.trackedorders.TrackedOrderManager;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.BazaarPoller.MarketReply;
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
    void startupClosesTheMarketGateBeforeStartingSessionProducers() {
        var data = new BazaarData();
        var owner = new AtomicReference<FeatureRuntime>();
        var runtime = new FeatureRuntime(new Activation(() -> true, () -> false, _ -> {}), data,
            () -> {
                Assertions.assertTrue(owner.get().isRunning());
                Assertions.assertTrue(owner.get().isHibernating());
                Assertions.assertFalse(owner.get().isActive());
            }, () -> {}, () -> {}, () -> {});
        owner.set(runtime);

        runtime.activate();

        Assertions.assertFalse(data.hasMarketData());
        runtime.onMarketReply(new MarketReply(BazaarData.MarketSnapshot.fromProducts(products(110)), true));
        Assertions.assertTrue(runtime.isActive());
        Assertions.assertEquals(110, data.highestBuyOrderPrice(PRODUCT).orElseThrow());
    }

    @Test
    void hibernationRetainsTheSessionWhileDeactivationEndsIt() {
        var data = new BazaarData();
        try (var orders = orders(data)) {
            var owner = new AtomicReference<FeatureRuntime>();
            var operations = new ArrayList<String>();
            var runtime = new FeatureRuntime(new Activation(() -> true, () -> true, _ -> {}), data,
                () -> {
                    Assertions.assertTrue(owner.get().isActive());
                    operations.add("start");
                }, () -> {
                    Assertions.assertTrue(owner.get().isRunning());
                    Assertions.assertFalse(owner.get().isActive());
                    operations.add("suspend");
                }, () -> {
                    Assertions.assertFalse(owner.get().isRunning());
                    orders.cancelOutstandingOrders();
                    orders.resetTrackedOrders();
                    operations.add("end");
                }, () -> {});
            owner.set(runtime);
            data.addListener(orders::onBazaarUpdate);
            runtime.activate();
            orders.syncOrders(List.of(order(100)));
            var id = orders.currentOrders().getFirst().id();
            runtime.onMarketReply(new MarketReply(BazaarData.MarketSnapshot.fromProducts(products(100)), true));

            runtime.hibernate();
            runtime.activate();
            runtime.hibernate();

            Assertions.assertTrue(runtime.isHibernating());
            Assertions.assertFalse(data.hasMarketData());
            Assertions.assertEquals(id, orders.currentOrders().getFirst().id());
            Assertions.assertEquals(3, orders.currentOrders().getFirst().fillAmountSnapshot());
            Assertions.assertEquals(List.of("start", "suspend"), operations);

            runtime.deactivate();
            runtime.deactivate();
            runtime.onMarketReply(new MarketReply(BazaarData.MarketSnapshot.fromProducts(products(110)), true));

            Assertions.assertFalse(runtime.isRunning());
            Assertions.assertFalse(data.hasMarketData());
            Assertions.assertTrue(orders.currentOrders().isEmpty());
            Assertions.assertEquals(List.of("start", "suspend", "end"), operations);
        }
    }

    @Test
    void recoveryPublishesTheSnapshotAndActiveGateBeforeNotifyingAlerts() {
        var data = new BazaarData();
        try (var orders = orders(data)) {
            var runtime = runtime(data);
            var notices = new ArrayList<Publication>();
            var config = new AlertConfig();
            var alerts = new AlertManager(data, () -> config, () -> {}, reached -> notices.add(new Publication(
                runtime.isActive(), data.highestBuyOrderPrice(PRODUCT).orElseThrow())));
            // Alerts read BazaarData directly, before the orders listener processes this publication.
            data.addListener(alerts::onBazaarUpdate);
            data.addListener(orders::onBazaarUpdate);
            runtime.activate();
            runtime.hibernate();
            orders.syncOrders(List.of(order(100)));
            saveAlert(alerts);

            runtime.onMarketReply(new MarketReply(BazaarData.MarketSnapshot.fromProducts(products(110)), true));

            Assertions.assertEquals(List.of(new Publication(true, 110)), notices);
            Assertions.assertInstanceOf(OrderStatus.Undercut.class, orders.currentOrders().getFirst().status());
            Assertions.assertTrue(alerts.alerts().isEmpty());
        }
    }

    @Test
    void anOrderListenerFailureDoesNotBlockRecoveryOrSavedAlerts() {
        var data = new BazaarData();
        try (var orders = orders(data)) {
            var runtime = runtime(data);
            var config = new AlertConfig();
            var notices = new ArrayList<ReachedAlert>();
            var alerts = new AlertManager(data, () -> config, () -> {}, notices::add);
            data.addListener(orders::onBazaarUpdate);
            data.addListener(alerts::onBazaarUpdate);
            runtime.activate();
            runtime.hibernate();
            orders.syncOrders(List.of(order(110), order(90)));
            saveAlert(alerts);
            // A malformed second level fails the orders listener, while its first price can reach the alert.
            var candidate = new Gson().fromJson("""
                {"products":{"TEST":{"sell_summary":[{"pricePerUnit":110,"orders":1},null]}}}
                """, SkyBlockBazaarReply.class);

            runtime
                .onMarketReply(new MarketReply(BazaarData.MarketSnapshot.fromProducts(candidate.getProducts()), false));

            Assertions.assertTrue(runtime.isActive());
            Assertions.assertTrue(alerts.alerts().isEmpty());
            Assertions.assertEquals(1, notices.size());
        }
    }

    @Test
    void resettingSessionInvalidatesOldWorkWithoutRestartingServicesOrChangingMarketAvailability() {
        var data = new BazaarData();
        try (var orders = orders(data)) {
            var owner = new AtomicReference<FeatureRuntime>();
            var services = new ArrayList<String>();
            var cleanupGenerations = new ArrayList<Long>();
            var runtime = new FeatureRuntime(new Activation(() -> true, () -> true, _ -> {}), data,
                () -> services.add("start"), () -> services.add("suspend"), () -> services.add("end"), () -> {
                    cleanupGenerations.add(owner.get().sessionGeneration());
                    orders.cancelOutstandingOrders();
                    orders.resetTrackedOrders();
                });
            owner.set(runtime);
            runtime.activate();
            runtime.onMarketReply(new MarketReply(BazaarData.MarketSnapshot.fromProducts(products(110)), true));
            orders.syncOrders(List.of(order(100)));
            long previousGeneration = runtime.sessionGeneration();

            runtime.resetSession();

            Assertions.assertTrue(runtime.sessionGeneration() > previousGeneration);
            Assertions.assertEquals(List.of(runtime.sessionGeneration()), cleanupGenerations);
            Assertions.assertTrue(orders.currentOrders().isEmpty());
            Assertions.assertTrue(runtime.isActive());
            Assertions.assertEquals(110, data.highestBuyOrderPrice(PRODUCT).orElseThrow());
            Assertions.assertEquals(List.of("start"), services);

            runtime.hibernate();
            previousGeneration = runtime.sessionGeneration();
            runtime.resetSession();
            Assertions.assertTrue(runtime.sessionGeneration() > previousGeneration);
            Assertions.assertTrue(runtime.isHibernating());
            Assertions.assertFalse(data.hasMarketData());
            Assertions.assertEquals(List.of("start", "suspend"), services);
        }
    }

    private static FeatureRuntime runtime(BazaarData data) {
        return new FeatureRuntime(new Activation(() -> true, () -> true, _ -> {}), data,
            () -> {}, () -> {}, () -> {}, () -> {});
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

    private record Publication(boolean active, double buyPrice) {}
}
