package com.github.lutzluca.btrbz.core;

import com.github.lutzluca.btrbz.core.AlertManager.AlertConfig;
import com.github.lutzluca.btrbz.core.AlertManager.ReachedAlert;
import com.github.lutzluca.btrbz.core.alert.AlertDefinition;
import com.github.lutzluca.btrbz.core.alert.AlertType;
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
        Assertions.assertEquals(111, manager.reachedAlerts().getFirst().alert().price);
        Assertions.assertEquals(102, manager.reachedAlerts().getLast().alert().price);
        var entry = manager.reachedAlerts().getFirst();
        Assertions.assertEquals(100, entry.price());
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
        Assertions.assertEquals(entry.price(), saved.price());
        Assertions.assertEquals(entry.alert().type, saved.alert().type);
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
        Assertions.assertEquals(99, manager.reachedAlerts().getFirst().price());
        Assertions.assertTrue(manager.alerts().isEmpty());
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
            config.reachedAlerts.add(new ReachedAlert(alert, 1000 + index, 90));
        }
        config.reachedAlerts.addFirst(new ReachedAlert(null, 1000, 90));
        config.reachedAlerts.addFirst(null);
        var alert = config.alerts.getFirst();
        config.reachedAlerts.addFirst(new ReachedAlert(alert, -1, 90));
        config.reachedAlerts.addFirst(new ReachedAlert(alert, 1000, Double.NaN));
        var saves = new int[1];

        var restored = new AlertManager(new BazaarData(), () -> config, () -> saves[0]++, _ -> {});

        Assertions.assertEquals(1, saves[0]);
        Assertions.assertEquals(10, restored.reachedAlerts().size());
        Assertions.assertEquals(100, restored.reachedAlerts().getFirst().alert().price);
        Assertions.assertEquals(109, restored.reachedAlerts().getLast().alert().price);
    }

    private static AlertDefinition definition(double threshold) {
        return new AlertDefinition(System.currentTimeMillis(), PRODUCT,
            new AlertType(PriceSource.Buy, Direction.Below), threshold);
    }

    private static void publish(BazaarData data, String price) {
        var summary = price == null ? "[]" : "[{\"pricePerUnit\":" + price + ",\"amount\":100,\"orders\":2}]";
        var reply = new Gson().fromJson("""
            {"products":{"ENCHANTED_DIAMOND":{"sell_summary":[],"buy_summary":%s}}}
            """.formatted(summary), SkyBlockBazaarReply.class);
        data.onUpdate(reply.getProducts());
    }
}
