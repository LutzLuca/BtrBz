package com.github.lutzluca.btrbz.core.alert;

import com.github.lutzluca.btrbz.core.alert.AlertCondition.Kind;
import com.github.lutzluca.btrbz.core.alert.AlertType.Direction;
import com.github.lutzluca.btrbz.core.alert.AlertType.PriceSource;
import com.github.lutzluca.btrbz.core.alert.LiquidityEvaluation.Level;
import com.github.lutzluca.btrbz.core.alert.AlertCondition.LiquiditySide;
import com.github.lutzluca.btrbz.core.config.ConfigStore;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Path;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LiquidityAlertTest {

    private static final IndexedProduct PRODUCT = new IndexedProduct("ENCHANTED_DIAMOND", "Enchanted Diamond");

    @TempDir
    Path tempDir;

    @Test
    void countsOnlyQualifyingItemsWithEqualityAndSaturatesLargeTotals() {
        Assertions.assertEquals(100, LiquidityEvaluation.qualifyingQuantity(LiquiditySide.BuyOrders, 1_000,
            List.of(new Level(1_100, 40), new Level(1_000, 60), new Level(999, 9_999))));
        Assertions.assertEquals(100, LiquidityEvaluation.qualifyingQuantity(LiquiditySide.SellOffers, 1_000,
            List.of(new Level(900, 40), new Level(1_000, 60), new Level(1_001, 9_999))));
        Assertions.assertEquals(Long.MAX_VALUE, LiquidityEvaluation.qualifyingQuantity(LiquiditySide.BuyOrders, 1,
            List.of(new Level(1, Long.MAX_VALUE), new Level(1, 1))));
    }

    @Test
    void retainsMissingProductsAndRecordsAStableObservationWhenTheyReturn() {
        var config = new AlertConfig();
        var data = new BazaarData();
        var notified = new ArrayList<ReachedAlert>();
        var manager = new AlertManager(data, () -> config, () -> {}, notified::add);
        data.addListener(manager::onBazaarUpdate);
        manager.saveAlert(null, new AlertDefinition(1_000, PRODUCT,
            new AlertCondition.Price(new AlertType(PriceSource.Sell, Direction.Above), 1_000))).get();
        manager.saveAlert(null, definition(100, 1_000)).get();

        publish(data, "{\"OTHER\":{\"sell_summary\":[],\"buy_summary\":[]}}");
        Assertions.assertEquals(2, manager.alerts().size());
        Assertions.assertTrue(manager.reachedAlerts().isEmpty());
        Assertions.assertTrue(manager.liquidityProgress(manager.alerts().getLast()).isEmpty());

        publish(data, product("[{\"pricePerUnit\":1100,\"amount\":40,\"orders\":1},"
            + "{\"pricePerUnit\":1000,\"amount\":60,\"orders\":2},"
            + "{\"pricePerUnit\":999,\"amount\":500,\"orders\":1}]"));
        Assertions.assertTrue(manager.alerts().isEmpty());
        Assertions.assertEquals(2, notified.size());
        Assertions.assertEquals(new AlertCondition.Observation.Liquidity(100),
            manager.reachedAlerts().getFirst().observation());
        Assertions.assertEquals(new AlertCondition.Observation.Price(1_100),
            manager.reachedAlerts().getLast().observation());

        publish(data, product("[]"));
        Assertions.assertEquals(new AlertCondition.Observation.Liquidity(100),
            manager.reachedAlerts().getFirst().observation());
        Assertions.assertEquals(new AlertCondition.Observation.Price(1_100),
            manager.reachedAlerts().getLast().observation());
    }

    @Test
    void saveAndWatchAgainImmediatelyReachAgainstLatestSnapshotButRespectDuplicates() {
        var config = new AlertConfig();
        var data = new BazaarData();
        var notified = new ArrayList<ReachedAlert>();
        var manager = new AlertManager(data, () -> config, () -> {}, notified::add);
        publish(data, product("[{\"pricePerUnit\":1000,\"amount\":100,\"orders\":1}]"));

        var first = manager.saveAlert(null, definition(100, 1_000)).get();
        Assertions.assertTrue(manager.alerts().isEmpty());
        Assertions.assertEquals(1, notified.size());
        manager.watchAgain(first.id).get();
        Assertions.assertEquals(2, notified.size());
        Assertions.assertEquals(1, manager.reachedAlerts().size());

        publish(data, product("[]"));
        var duplicate = manager.saveAlert(null, definition(100, 1_000)).get();
        Assertions.assertEquals(0, manager.liquidityProgress(duplicate).orElseThrow());
        Assertions.assertTrue(manager.watchAgain(first.id).isFailure());
        Assertions.assertEquals(1, manager.reachedAlerts().size());
        Assertions.assertEquals(1, manager.alerts().size());
        Assertions.assertEquals(2, notified.size());
    }

    @Test
    void disabledMonitoringAllowsBothKindsToBeSavedWithoutImmediateDelivery() {
        var config = new AlertConfig();
        config.enabled = false;
        var data = new BazaarData();
        var notified = new ArrayList<ReachedAlert>();
        var manager = new AlertManager(data, () -> config, () -> {}, notified::add);
        data.addListener(manager::onBazaarUpdate);
        publish(data, product("[{\"pricePerUnit\":1000,\"amount\":100,\"orders\":1}]"));

        manager.saveAlert(null, new AlertDefinition(1_000, PRODUCT,
            new AlertCondition.Price(new AlertType(PriceSource.Sell, Direction.Above), 1_000))).get();
        manager.saveAlert(null, definition(100, 1_000)).get();

        Assertions.assertEquals(2, manager.alerts().size());
        Assertions.assertTrue(manager.reachedAlerts().isEmpty());
        Assertions.assertTrue(notified.isEmpty());

        config.enabled = true;
        publish(data, product("[{\"pricePerUnit\":1000,\"amount\":100,\"orders\":1}]"));
        Assertions.assertTrue(manager.alerts().isEmpty());
        Assertions.assertEquals(2, manager.reachedAlerts().size());
        Assertions.assertEquals(2, notified.size());
    }

    @Test
    void failedDeliveryDoesNotReportAPersistedImmediateReachAsAFailedSave() {
        var config = new AlertConfig();
        var data = new BazaarData();
        publish(data, product("[{\"pricePerUnit\":1000,\"amount\":100,\"orders\":1}]"));
        var manager = new AlertManager(data, () -> config, () -> {}, _ -> {
            throw new IllegalStateException("delivery unavailable");
        });

        Assertions.assertTrue(manager.saveAlert(null, definition(100, 1_000)).isSuccess());
        Assertions.assertTrue(manager.alerts().isEmpty());
        Assertions.assertEquals(1, manager.reachedAlerts().size());
    }

    @Test
    void persistsIndependentReachedLimitsForBothKinds() {
        var path = this.tempDir.resolve("liquidity-history.json");
        var store = new ConfigStore(path);
        var data = new BazaarData();
        publish(data, product("[{\"pricePerUnit\":1000,\"amount\":100,\"orders\":1}]"));
        var manager = new AlertManager(data, () -> store.config().alert, store::save, _ -> {});
        for (int index = 1; index <= 12; index++) {
            manager
                .saveAlert(null,
                    new AlertDefinition(index, PRODUCT,
                        new AlertCondition.Price(new AlertType(PriceSource.Sell, Direction.Above), 1_000 - index)))
                .get();
            manager.saveAlert(null, definition(index, 1_000)).get();
        }

        Assertions.assertEquals(20, manager.reachedAlerts().size());
        var restoredStore = new ConfigStore(path);
        Assertions.assertTrue(restoredStore.load());
        var restored = new AlertManager(data, () -> restoredStore.config().alert, restoredStore::save, _ -> {});
        Assertions.assertEquals(20, restored.reachedAlerts().size());
        Assertions.assertEquals(10, restored.reachedAlerts().stream()
            .filter(entry -> entry.alert().kind() == Kind.Price).count());
        Assertions.assertEquals(10, restored.reachedAlerts().stream()
            .filter(entry -> entry.alert().kind() == Kind.Liquidity).count());
        Assertions.assertEquals(12,
            ((AlertCondition.Liquidity) restored.reachedAlerts().getFirst().alert().condition).quantity());
    }

    private static AlertDefinition definition(long quantity, double bound) {
        return new AlertDefinition(1_000, PRODUCT,
            new AlertCondition.Liquidity(LiquiditySide.BuyOrders, quantity, bound));
    }

    private static String product(String sellSummary) {
        return "{\"ENCHANTED_DIAMOND\":{\"sell_summary\":" + sellSummary + ",\"buy_summary\":[]}}";
    }

    private static void publish(BazaarData data, String products) {
        var reply = new Gson().fromJson("{\"products\":" + products + "}", SkyBlockBazaarReply.class);
        data.onUpdate(reply.getProducts());
    }
}
