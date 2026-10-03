package com.github.lutzluca.btrbz.core;

import com.github.lutzluca.btrbz.core.alert.AlertCondition;
import com.github.lutzluca.btrbz.core.alert.AlertConfig;
import com.github.lutzluca.btrbz.core.alert.AlertDefinition;
import com.github.lutzluca.btrbz.core.alert.AlertManager;
import com.github.lutzluca.btrbz.core.alert.AlertType;
import com.github.lutzluca.btrbz.core.fliphelper.FlipHelper;
import com.github.lutzluca.btrbz.core.fliphelper.FlipProductContext;
import com.github.lutzluca.btrbz.core.fliphelper.FlipSubmissionTracker;
import com.github.lutzluca.btrbz.core.trackedorders.TrackedOrderManager;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.BazaarMessageDispatcher.BazaarMessage;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.OrderModels.OutstandingOrderInfo;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.data.TimedStore;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply.Product;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class FeatureRuntimeTest {
    private static final ProductIdentity PRODUCT = ProductIdentity.fromRuntime("Test Product", "TEST", null);

    @Test
    void repeatedOperationsPreserveTheSessionAndCannotWakeHibernateOrInactive() {
        var data = new BazaarData();
        var orders = orders(data);
        var enabled = new AtomicBoolean(true);
        var owner = new AtomicReference<FeatureRuntime>();
        var activation = new Activation(enabled::get, () -> true, active -> {
            if (active) {
                owner.get().activate();
            } else {
                owner.get().deactivate();
            }
        });
        var operations = new ArrayList<String>();
        var runtime = new FeatureRuntime(activation, data, orders, () -> {
            Assertions.assertTrue(owner.get().isActive());
            operations.add("start");
        }, () -> {
            Assertions.assertTrue(owner.get().isRunning());
            Assertions.assertFalse(owner.get().isActive());
            operations.add("suspend");
        }, () -> {
            Assertions.assertFalse(owner.get().isRunning());
            operations.add("stop");
        });
        owner.set(runtime);
        activation.refresh();
        long generation = runtime.sessionGeneration();
        runtime.activate();
        runtime.hibernate();
        runtime.hibernate();
        runtime.activate();
        activation.refresh();
        Assertions.assertTrue(runtime.isHibernating());
        Assertions.assertEquals(generation, runtime.sessionGeneration());
        Assertions.assertEquals(List.of("start", "suspend"), operations);
        runtime.recover(BazaarData.prepareSnapshot(Map.of()));
        Assertions.assertTrue(runtime.isHibernating());
        runtime.recover(BazaarData.prepareSnapshot(products(100)));
        runtime.recover(BazaarData.prepareSnapshot(products(200)));
        Assertions.assertEquals(100, data.highestBuyOrderPrice(PRODUCT).orElseThrow());
        Assertions.assertEquals(generation, runtime.sessionGeneration());
        enabled.set(false);
        activation.refresh();
        runtime.deactivate();
        runtime.hibernate();
        runtime.recover(BazaarData.prepareSnapshot(products(300)));
        runtime.onMarketUpdate(products(400));
        Assertions.assertFalse(runtime.isRunning());
        Assertions.assertFalse(data.hasMarketData());
        Assertions.assertEquals(List.of("start", "suspend", "stop"), operations);
        enabled.set(true);
        activation.refresh();
        Assertions.assertTrue(runtime.isActive());
        Assertions.assertTrue(runtime.sessionGeneration() > generation);
        Assertions.assertEquals(List.of("start", "suspend", "stop", "start"), operations);
    }

    @Test
    void passiveFillsSyncAndLateConfirmationsSurviveButFullResetClearsTheSession() throws Exception {
        var data = new BazaarData();
        var orders = orders(data);
        try (var submissions = new FlipSubmissionTracker()) {
            var helper = new FlipHelper(data, new FlipProductContext(), submissions, orders);
            var runtime = new FeatureRuntime(new Activation(() -> true, () -> true, _ -> {}),
                data, orders, () -> {}, helper::cancelPendingFlip, submissions::clear);
            data.addListener(orders::onBazaarUpdate);
            runtime.activate();
            orders.syncOrders(List.of(order(10, 100, 2, 10), order(20, 100, 3, 11), order(30, 90, 4, 12)));
            var initial = orders.currentOrders();
            orders.reorder(initial.getFirst().id(), 3);
            orders.addOutstandingOrder(new OutstandingOrderInfo(PRODUCT, "Test Product", OrderType.Buy,
                40, 100, 4000));
            submissions.recordSubmittedFlip(PRODUCT, 109.9);
            runtime.onMarketUpdate(products(100));
            runtime.hibernate();
            Assertions.assertFalse(data.hasMarketData());
            Assertions.assertEquals(List.of(initial.get(1).id(), initial.get(2).id(), initial.get(0).id()),
                orders.currentOrders().stream().map(TrackedOrderManager.TrackedOrderSnapshot::id).toList());
            orders.handleOrderFilled(new BazaarMessage.OrderFilled(OrderType.Buy, 10, "Test Product"));
            orders.syncOrders(List.of(order(20, 100, 7, 13), order(30, 90, 6, 14),
                new OrderInfo.FilledOrderInfo(PRODUCT, "Test Product", OrderType.Buy, 10, 100, 10, 10, 15)));
            Assertions.assertEquals(1, orders.filledOrderCount());
            Assertions.assertEquals(List.of(initial.get(1).id(), initial.get(2).id()), orders.creationOrder());
            Assertions.assertEquals(7, orders.currentOrders().getFirst().fillAmountSnapshot());
            orders.confirmOutstanding(new BazaarMessage.OrderSetup(OrderType.Buy, 40, "Test Product", 4000));
            helper.handleFlipped(new BazaarMessage.OrderFlipped(5, "Test Product", 549.5));
            Assertions.assertEquals(4, orders.currentOrders().size());
            Assertions.assertEquals(109.9, orders.currentOrders().getLast().pricePerUnit());
            Assertions.assertTrue(submissions.consume(PRODUCT).isEmpty());
            runtime.recover(BazaarData.prepareSnapshot(products(105)));
            Assertions.assertEquals(1, orders.filledOrderCount());
            Assertions.assertEquals(initial.get(1).id(), orders.currentOrders().getFirst().id());
            Assertions.assertInstanceOf(OrderStatus.Undercut.class, orders.currentOrders().getFirst().status());
            var resets = new int[1];
            orders.addOnOrdersResetListener(() -> resets[0]++);
            runtime.hibernate();
            orders.addOutstandingOrder(new OutstandingOrderInfo(PRODUCT, "Test Product", OrderType.Buy,
                50, 100, 5000));
            submissions.recordSubmittedFlip(PRODUCT, 119.9);
            runtime.deactivate();
            runtime.deactivate();
            Assertions.assertEquals(1, resets[0]);
            Assertions.assertTrue(orders.creationOrder().isEmpty());
            Assertions.assertTrue(orders.currentOrders().isEmpty());
            Assertions.assertEquals(0, orders.filledOrderCount());
            Assertions.assertTrue(submissions.consume(PRODUCT).isEmpty());
            var field = TrackedOrderManager.class.getDeclaredField("outstandingOrderStore");
            field.setAccessible(true);
            Assertions.assertTrue(((TimedStore<?>) field.get(orders)).items().isEmpty());
        }
    }

    @Test
    void reachedAlertsPublishOnceAfterQuietBaselineAndTheGateOpen() {
        var data = new BazaarData();
        var orders = orders(data);
        var runtime = new FeatureRuntime(new Activation(() -> true, () -> true, _ -> {}), data,
            orders, () -> {}, () -> {}, () -> {});
        var config = new AlertConfig();
        var notices = new int[1];
        var alerts = new AlertManager(data, () -> config, () -> {}, reached -> {
            Assertions.assertTrue(runtime.isActive());
            Assertions.assertTrue(data.hasMarketData());
            Assertions.assertInstanceOf(OrderStatus.Undercut.class, orders.currentOrders().getFirst().status());
            notices[0]++;
        });
        data.addListener(alerts::onBazaarUpdate);
        data.addListener(orders::onBazaarUpdate);
        runtime.activate();
        runtime.onMarketUpdate(products(100));
        runtime.hibernate();
        orders.syncOrders(List.of(order(10, 100, 3, 10)));
        alerts.saveAlert(null, new AlertDefinition(1, new IndexedProduct("TEST", "Test Product"),
            new AlertCondition.Price(new AlertType(AlertType.PriceSource.Sell, AlertType.Direction.Above), 105)))
            .get();
        runtime.onMarketUpdate(products(110));
        Assertions.assertEquals(1, alerts.alerts().size());
        Assertions.assertEquals(0, notices[0]);
        runtime.recover(BazaarData.prepareSnapshot(products(110)));
        Assertions.assertEquals(0, alerts.alerts().size());
        Assertions.assertEquals(1, notices[0]);
        runtime.recover(BazaarData.prepareSnapshot(products(110)));
        runtime.onMarketUpdate(products(110));
        Assertions.assertEquals(1, notices[0]);
    }

    @Test
    void failedBaselineLeavesTheCandidateHiddenAndDoesNotConsumeAlerts() {
        var data = new BazaarData();
        var orders = orders(data);
        var runtime = new FeatureRuntime(new Activation(() -> true, () -> true, _ -> {}), data,
            orders, () -> {}, () -> {}, () -> {});
        runtime.activate();
        runtime.hibernate();
        orders.syncOrders(List.of(order(10, 100, 0, 10), order(20, 90, 0, 11)));
        var candidate = new Gson().fromJson("""
            {"products":{"TEST":{"sell_summary":[{"pricePerUnit":100,"orders":1},null]}}}
            """, SkyBlockBazaarReply.class);
        var publications = new int[1];
        data.addListener(_ -> publications[0]++);
        runtime.recover(BazaarData.prepareSnapshot(candidate.getProducts()));
        Assertions.assertTrue(runtime.isHibernating());
        Assertions.assertFalse(data.hasMarketData());
        Assertions.assertEquals(0, publications[0]);
        Assertions.assertTrue(
            orders.currentOrders().stream().allMatch(order -> order.status() instanceof OrderStatus.Unknown));
    }

    private static TrackedOrderManager orders(BazaarData data) {
        var config = new TrackedOrderManager.OrderManagerConfig();
        config.enabled = false;
        return new TrackedOrderManager(data, () -> config);
    }

    private static OrderInfo.UnfilledOrderInfo order(int volume, double price, int fill, int slot) {
        return new OrderInfo.UnfilledOrderInfo(PRODUCT, "Test Product", OrderType.Buy, volume, price, fill, 0, slot);
    }

    private static Map<String, Product> products(double price) {
        return new Gson().fromJson("""
            {"products":{"TEST":{
                "sell_summary":[{"pricePerUnit":%s,"amount":100,"orders":2}],
                "buy_summary":[{"pricePerUnit":120,"amount":100,"orders":2}]
            }}}
            """.formatted(price), SkyBlockBazaarReply.class).getProducts();
    }
}
