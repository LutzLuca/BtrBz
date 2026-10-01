package com.github.lutzluca.btrbz.core.alert;

import com.github.lutzluca.btrbz.core.alert.AlertType.Direction;
import com.github.lutzluca.btrbz.core.alert.AlertType.PriceSource;
import com.github.lutzluca.btrbz.core.config.ConfigStore;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import com.google.gson.Gson;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;

class ReachedAlertsTest {
    private static final IndexedProduct PRODUCT = new IndexedProduct("ENCHANTED_DIAMOND", "§aEnchanted Diamond");

    @TempDir
    Path tempDir;

    @Test
    void keepsLatestTenReachedAlertsAndPersistsIndividualRemoval() {
        var path = this.tempDir.resolve("reached-alerts.json");
        var store = new ConfigStore(path);
        var data = new BazaarData();
        var notifications = new ArrayList<ReachedAlert>();
        var manager = new AlertManager(data, () -> store.config().alert, store::save, notifications::add);
        data.addListener(manager::onBazaarUpdate);
        for (int index = 0; index < 12; index++) {
            manager.saveAlert(null, definition(100 + index)).get();
        }

        long before = System.currentTimeMillis();
        publish(data, "100");

        Assertions.assertTrue(manager.alerts().isEmpty());
        Assertions.assertEquals(12, notifications.size());
        Assertions.assertEquals(10, manager.reachedAlerts().size());
        Assertions.assertEquals(111,
            ((AlertCondition.Price) manager.reachedAlerts().getFirst().alert().condition).price());
        Assertions.assertEquals(102,
            ((AlertCondition.Price) manager.reachedAlerts().getLast().alert().condition).price());
        var entry = manager.reachedAlerts().getFirst();
        Assertions.assertEquals(new AlertCondition.Observation.Price(100), entry.observation());
        Assertions.assertTrue(entry.reachedAt() >= before);
        Assertions.assertTrue(entry.reachedAt() <= System.currentTimeMillis());
        Assertions.assertThrows(UnsupportedOperationException.class, () -> manager.reachedAlerts().clear());

        publish(data, "90");
        Assertions.assertEquals(entry, manager.reachedAlerts().getFirst());
        Assertions.assertEquals(12, notifications.size());

        var reloaded = new ConfigStore(path);
        Assertions.assertTrue(reloaded.load());
        var restored = new AlertManager(data, () -> reloaded.config().alert, reloaded::save, _ -> {});
        var saved = restored.reachedAlerts().getFirst();
        Assertions.assertEquals(entry.alert().id, saved.alert().id);
        Assertions.assertEquals(PRODUCT, saved.alert().product);
        Assertions.assertEquals(entry.reachedAt(), saved.reachedAt());
        Assertions.assertEquals(entry.observation(), saved.observation());
        Assertions.assertEquals(entry.alert().condition, saved.alert().condition);
        Assertions.assertTrue(restored.removeReachedAlert(saved.alert().id));
        Assertions.assertFalse(restored.removeReachedAlert(saved.alert().id));
        Assertions.assertTrue(reloaded.load());
        Assertions.assertEquals(9, reloaded.config().alert.reachedAlerts.size());
        Assertions.assertTrue(reloaded.config().alert.alerts.isEmpty());
    }

    @Test
    void waitsForAvailablePricesAndEnabledAlertsBeforeRecording() {
        var config = new AlertConfig();
        var data = new BazaarData();
        var manager = new AlertManager(data, () -> config, () -> {}, _ -> {});
        data.addListener(manager::onBazaarUpdate);
        manager.saveAlert(null, definition(100)).get();

        data.onUpdate(Map.of());
        publish(data, null);
        publish(data, "101");
        config.enabled = false;
        publish(data, "99");
        Assertions.assertTrue(manager.reachedAlerts().isEmpty());
        Assertions.assertEquals(1, manager.alerts().size());

        config.enabled = true;
        publish(data, "99");
        Assertions.assertEquals(1, manager.reachedAlerts().size());
        Assertions.assertEquals(new AlertCondition.Observation.Price(99),
            manager.reachedAlerts().getFirst().observation());
        Assertions.assertTrue(manager.alerts().isEmpty());
    }

    @ParameterizedTest
    @EnumSource(PriceSource.class)
    void waitsForTheWatchedQuoteThenPersistsAndNotifiesOnce(PriceSource source) {
        var path = this.tempDir.resolve("price-alert.json");
        var store = new ConfigStore(path);
        var data = new BazaarData();
        var notifications = new ArrayList<ReachedAlert>();
        var saves = new int[1];
        var manager = new AlertManager(data, () -> store.config().alert, () -> {
            saves[0]++;
            store.save();
        }, notifications::add);
        data.addListener(manager::onBazaarUpdate);
        var condition = new AlertCondition.Price(
            new AlertType(source, source == PriceSource.Sell ? Direction.Below : Direction.Above), 100);

        publishBook(data, source == PriceSource.Sell ? "missing" : "90",
            source == PriceSource.Sell ? "150" : "missing");
        var alert = manager.saveAlert(null, new AlertDefinition(System.currentTimeMillis(), PRODUCT, condition)).get();
        Assertions.assertEquals(1, manager.alerts().size());
        Assertions.assertTrue(notifications.isEmpty());

        data.onUpdate(Map.of());
        publishBook(data, null, null);
        publishBook(data, source == PriceSource.Sell ? "101" : "90", source == PriceSource.Sell ? "150" : "99");
        Assertions.assertEquals(1, manager.alerts().size());
        Assertions.assertEquals(1, saves[0]);

        publishBook(data, source == PriceSource.Sell ? "95" : "101", source == PriceSource.Sell ? "99" : "105");
        Assertions.assertTrue(manager.alerts().isEmpty());
        Assertions.assertEquals(2, saves[0]);
        Assertions.assertEquals(1, notifications.size());
        var reached = manager.reachedAlerts().getFirst();
        Assertions.assertEquals(alert.id, reached.alert().id);
        Assertions.assertEquals(new AlertCondition.Observation.Price(source == PriceSource.Sell ? 95 : 105),
            reached.observation());

        publishBook(data, null, null);
        Assertions.assertEquals(2, saves[0]);
        Assertions.assertEquals(1, notifications.size());
        Assertions.assertEquals(reached, manager.reachedAlerts().getFirst());

        var restored = new ConfigStore(path);
        Assertions.assertTrue(restored.load());
        Assertions.assertTrue(restored.config().alert.alerts.isEmpty());
        var saved = restored.config().alert.reachedAlerts.getFirst();
        Assertions.assertEquals(alert.id, saved.alert().id);
        Assertions.assertEquals(condition, saved.alert().condition);
        Assertions.assertEquals(reached.observation(), saved.observation());
    }

    @ParameterizedTest
    @CsvSource({"Buy,Below,95", "Buy,Above,105", "Sell,Below,95", "Sell,Above,105"})
    void emptySidesKeepSavedAndReactivatedAlertsPending(PriceSource source, Direction direction, String quote) {
        var path = this.tempDir.resolve("empty-side-alert.json");
        var store = new ConfigStore(path);
        var data = new BazaarData();
        var notifications = new ArrayList<ReachedAlert>();
        var manager = new AlertManager(data, () -> store.config().alert, store::save, notifications::add);
        var condition = new AlertCondition.Price(new AlertType(source, direction), 100);
        publishBook(data, null, null);

        var alert = manager.saveAlert(null, new AlertDefinition(System.currentTimeMillis(), PRODUCT, condition)).get();
        Assertions.assertEquals(condition, manager.alerts().getFirst().condition);
        Assertions.assertTrue(manager.reachedAlerts().isEmpty());
        Assertions.assertTrue(notifications.isEmpty());

        var restored = new ConfigStore(path);
        Assertions.assertTrue(restored.load());
        Assertions.assertEquals(condition, restored.config().alert.alerts.getFirst().condition);
        Assertions.assertTrue(restored.config().alert.reachedAlerts.isEmpty());

        publishBook(data, source == PriceSource.Sell ? quote : null, source == PriceSource.Buy ? quote : null);
        manager.saveAlert(alert.id, new AlertDefinition(System.currentTimeMillis(), PRODUCT, condition)).get();
        Assertions.assertTrue(manager.alerts().isEmpty());
        Assertions.assertEquals(1, notifications.size());
        Assertions.assertEquals(new AlertCondition.Observation.Price(Double.parseDouble(quote)),
            notifications.getFirst().observation());

        publishBook(data, null, null);
        manager.watchAgain(alert.id).get();
        Assertions.assertTrue(manager.reachedAlerts().isEmpty());
        Assertions.assertEquals(condition, manager.alerts().getFirst().condition);
        Assertions.assertEquals(1, notifications.size());
    }

    @Test
    void failedSaveKeepsReachedHistoryAndFailedRemovalRollsBack() {
        var config = new AlertConfig();
        var data = new BazaarData();
        var failSave = new boolean[1];
        var notifications = new ArrayList<ReachedAlert>();
        var manager = new AlertManager(data, () -> config, () -> {
            if (failSave[0]) {
                throw new IllegalStateException("disk unavailable");
            }
        }, notifications::add);
        data.addListener(manager::onBazaarUpdate);
        manager.saveAlert(null, definition(100)).get();
        long revision = manager.changes().revision();
        failSave[0] = true;

        Assertions.assertDoesNotThrow(() -> publish(data, "99"));
        Assertions.assertTrue(manager.alerts().isEmpty());
        Assertions.assertEquals(1, notifications.size());
        Assertions.assertTrue(manager.changes().revision() > revision);
        var entry = manager.reachedAlerts().getFirst();
        Assertions.assertThrows(IllegalStateException.class, () -> manager.removeReachedAlert(entry.alert().id));
        Assertions.assertEquals(entry, manager.reachedAlerts().getFirst());
    }

    @Test
    void cleansMalformedHistoryAndEnforcesLimitOnLoad() {
        var config = new AlertConfig();
        var manager = new AlertManager(new BazaarData(), () -> config, () -> {}, _ -> {});
        for (int index = 0; index < 12; index++) {
            var alert = manager.saveAlert(null, definition(100 + index)).get();
            config.reachedAlerts.add(new ReachedAlert(alert, 1000 + index, new AlertCondition.Observation.Price(90)));
        }
        config.reachedAlerts.addFirst(null);
        var saves = new int[1];

        var restored = new AlertManager(new BazaarData(), () -> config, () -> saves[0]++, _ -> {});

        Assertions.assertEquals(1, saves[0]);
        Assertions.assertEquals(10, restored.reachedAlerts().size());
        Assertions.assertEquals(100,
            ((AlertCondition.Price) restored.reachedAlerts().getFirst().alert().condition).price());
        Assertions.assertEquals(109,
            ((AlertCondition.Price) restored.reachedAlerts().getLast().alert().condition).price());
    }

    private static AlertDefinition definition(double threshold) {
        return new AlertDefinition(System.currentTimeMillis(), PRODUCT,
            new AlertCondition.Price(new AlertType(PriceSource.Buy, Direction.Below), threshold));
    }

    private static void publish(BazaarData data, String price) {
        publishBook(data, null, price);
    }

    private static void publishBook(BazaarData data, String buyOrder, String sellOffer) {
        var reply = new Gson().fromJson("""
            {"products":{"ENCHANTED_DIAMOND":{"sell_summary":%s,"buy_summary":%s}}}
            """.formatted(summary(buyOrder), summary(sellOffer)), SkyBlockBazaarReply.class);
        data.onUpdate(reply.getProducts());
    }

    private static String summary(String price) {
        if (price == null) {
            return "[]";
        }
        return price.equals("missing")
            ? "null"
            : "[{\"pricePerUnit\":" + price + ",\"amount\":100,\"orders\":2}]";
    }
}
