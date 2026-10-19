package com.github.lutzluca.btrbz.core.alert;

import com.github.lutzluca.btrbz.core.alert.AlertCondition.Kind;
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

class AlertLifecycleTest {
    private static final IndexedProduct PRODUCT = new IndexedProduct("DIAMOND", "Diamond");

    @TempDir
    Path tempDir;

    @ParameterizedTest
    @EnumSource(Kind.class)
    void repeatCrossingsWaitForDismissalAcrossReloadAndRequireARealResetAndCooldown(Kind kind) {
        var path = this.tempDir.resolve("crossings.json");
        var store = new ConfigStore(path);
        var data = new BazaarData();
        var notifications = new ArrayList<ReachedAlert>();
        long[] now = {1_000};
        var manager = new AlertManager(data, () -> store.config().alert, store::save, notifications::add, () -> now[0]);
        AlertCondition condition = kind == Kind.Price
            ? new AlertCondition.Price(new AlertType(AlertType.PriceSource.Buy, AlertType.Direction.Below), 100)
            : new AlertCondition.Liquidity(AlertCondition.LiquiditySide.SellOffers, 100, 100);
        var watch = manager.saveAlert(null, new AlertDefinition(now[0], PRODUCT, condition), AlertLifecycle.Repeat)
            .get();
        manager.onBazaarUpdate(snapshot(true));
        var first = manager.inbox().getFirst();
        Assertions.assertEquals(1, manager.alerts().size());
        Assertions.assertTrue(manager.alerts().getFirst().waitingForReset());

        var restoredStore = new ConfigStore(path);
        Assertions.assertTrue(restoredStore.load());
        var restored = new AlertManager(data, () -> restoredStore.config().alert, restoredStore::save,
            notifications::add, () -> now[0]);
        restored.onBazaarUpdate(BazaarData.MarketSnapshot.fromProducts(Map.of()));
        restored.onBazaarUpdate(snapshot(true));
        Assertions.assertEquals(1, notifications.size());
        restored.onBazaarUpdate(snapshot(false));
        Assertions.assertTrue(restored.alerts().getFirst().waitingForReset());
        restored.onBazaarUpdate(snapshot(true));
        now[0] = 61_000;
        restored.onBazaarUpdate(snapshot(false));
        restored.onBazaarUpdate(snapshot(true));
        Assertions.assertEquals(1, notifications.size(), "crossings cannot add events while the first is unfinished");
        Assertions.assertEquals(1, restored.pendingCount());
        restored.dismiss(first.id());
        restored.onBazaarUpdate(snapshot(true));
        Assertions.assertEquals(1, notifications.size(), "dismissing a met target still requires a fresh crossing");
        restored.onBazaarUpdate(snapshot(false));
        restored.onBazaarUpdate(snapshot(true));
        Assertions.assertEquals(2, notifications.size());
        Assertions.assertEquals(1, restored.pendingCount());
        Assertions.assertNotEquals(first.id(), restored.inbox().getFirst().id());
        Assertions.assertEquals(watch.id, restored.inbox().getFirst().alert().id);
        Assertions.assertEquals(1_000, first.alert().lastTriggeredAt, "old events retain their captured watch state");
        restored.dismissAll();
        restored.onBazaarUpdate(snapshot(false));
        restored.onBazaarUpdate(snapshot(true));
        Assertions.assertEquals(2, notifications.size(), "dismissing cannot bypass the cooldown");
        now[0] = 121_000;
        restored.onBazaarUpdate(snapshot(true));
        Assertions.assertEquals(3, notifications.size());
        Assertions.assertEquals(1, restored.pendingCount());
        Assertions.assertTrue(restoredStore.load());
        Assertions.assertEquals(3, restoredStore.config().alert.reachedAlerts.size());
        Assertions.assertEquals(121_000, restoredStore.config().alert.alerts.getFirst().lastTriggeredAt);
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void watchAgainCreatesANewAlertWithTheSavedTargetAndPreservesHistory(Kind kind) {
        var config = new AlertConfig();
        var data = new BazaarData();
        var notifications = new ArrayList<ReachedAlert>();
        var manager = new AlertManager(data, () -> config, () -> {}, notifications::add);
        data.addListener(manager::onBazaarUpdate);
        data.publishSnapshot(snapshot(true));
        AlertCondition condition = kind == Kind.Price
            ? new AlertCondition.Price(new AlertType(AlertType.PriceSource.Buy, AlertType.Direction.Below), 100)
            : new AlertCondition.Liquidity(AlertCondition.LiquiditySide.SellOffers, 100, 100);
        manager.saveAlert(null, new AlertDefinition(1_000, PRODUCT, condition), AlertLifecycle.Once).get();
        var original = manager.inbox().getFirst();
        manager.dismiss(original.id());
        var history = manager.history().getFirst();

        var immediate = manager.watchAgain(original.id()).get();

        Assertions.assertNotEquals(original.alert().id, immediate.id);
        Assertions.assertEquals(condition, immediate.condition);
        Assertions.assertEquals(immediate.id, manager.inbox().getFirst().alert().id);
        Assertions.assertTrue(manager.alerts().isEmpty());
        Assertions.assertEquals(2, notifications.size());
        Assertions.assertEquals(history, manager.history().getFirst());

        manager.dismissAll();
        data.publishSnapshot(snapshot(false));
        var watching = manager.watchAgain(original.id()).get();
        Assertions.assertFalse(watching.waitingForReset());
        Assertions.assertEquals(watching.id, manager.alerts().getFirst().id);
        Assertions.assertTrue(manager.watchAgain(original.id()).isFailure(), "an identical watch is already active");
        data.publishSnapshot(snapshot(true));
        Assertions.assertEquals(3, notifications.size());
        Assertions.assertEquals(watching.id, manager.inbox().getFirst().alert().id);
        Assertions.assertTrue(manager.history().contains(history));
    }

    @Test
    void dismissingWhileTheConditionIsUnmetResumesWatchingWithoutLosingTheFirstEvent() {
        var data = new BazaarData();
        var config = new AlertConfig();
        long[] now = {1_000};
        var manager = new AlertManager(data, () -> config, () -> {}, _ -> {}, () -> now[0]);
        data.addListener(manager::onBazaarUpdate);
        manager.saveAlert(null, new AlertDefinition(now[0], PRODUCT,
            new AlertCondition.Price(new AlertType(AlertType.PriceSource.Buy, AlertType.Direction.Below), 100)),
            AlertLifecycle.Repeat).get();
        manager.saveAlert(null, new AlertDefinition(now[0], PRODUCT,
            new AlertCondition.Price(new AlertType(AlertType.PriceSource.Buy, AlertType.Direction.Above), 110)),
            AlertLifecycle.Once).get();
        data.publishSnapshot(snapshot(true));
        var first = manager.inbox().getFirst();
        data.publishSnapshot(snapshot(false));
        manager.dismiss(first.id());
        Assertions.assertFalse(manager.alerts().getFirst().waitingForReset());
        Assertions.assertEquals(first.observation(), manager.history().getFirst().observation());
        now[0] = 61_000;
        data.publishSnapshot(snapshot(true));
        Assertions.assertEquals(1, manager.pendingCount());
        Assertions.assertEquals(1, manager.history().size());
        data.publishSnapshot(snapshotAtPrice(115));
        Assertions.assertEquals(2, manager.pendingCount(), "other watches still trigger after a dismissal");
        Assertions.assertEquals(1, manager.alerts().size());
    }

    @Test
    void changingTheLifecycleOfAnUnchangedConditionKeepsItsResetState() {
        var config = new AlertConfig();
        var data = new BazaarData();
        var manager = new AlertManager(data, () -> config, () -> {}, _ -> {}, () -> 1_000);
        var definition = new AlertDefinition(1_000, PRODUCT,
            new AlertCondition.Price(new AlertType(AlertType.PriceSource.Buy, AlertType.Direction.Below), 100));
        var watch = manager.saveAlert(null, definition, AlertLifecycle.Repeat).get();
        data.publishSnapshot(snapshot(true));
        manager.onBazaarUpdate(data.currentSnapshot());

        var edited = manager.saveAlert(watch.id, definition, AlertLifecycle.Once).get();

        Assertions.assertEquals(AlertLifecycle.Once, edited.lifecycle);
        Assertions.assertTrue(edited.waitingForReset());
        Assertions.assertEquals(1, manager.pendingCount());
        Assertions.assertEquals(1, manager.alerts().size());
    }

    @Test
    void reviewIsPersistedAndOnlyReviewedEventsArePruned() {
        var store = new ConfigStore(this.tempDir.resolve("inbox.json"));
        var manager = new AlertManager(new BazaarData(), () -> store.config().alert, store::save, _ -> {});
        var watch = manager.saveAlert(null, new AlertDefinition(1_000, PRODUCT,
            new AlertCondition.Price(new AlertType(AlertType.PriceSource.Buy, AlertType.Direction.Below), 100)),
            AlertLifecycle.Once).get();
        for (int index = 0; index < 65; index++) {
            store.config().alert.reachedAlerts.addFirst(new ReachedAlert(watch, 1_000 + index,
                new AlertCondition.Observation.Price(90)));
        }
        Assertions.assertEquals(65, manager.pendingCount());
        manager.dismiss(manager.inbox().getFirst().id());
        var reload = new ConfigStore(this.tempDir.resolve("inbox.json"));
        Assertions.assertTrue(reload.load());
        var restored = new AlertManager(new BazaarData(), () -> reload.config().alert, reload::save, _ -> {});
        Assertions.assertEquals(64, restored.pendingCount());
        Assertions.assertEquals(1, restored.history().size());
        restored.dismissAll();
        Assertions.assertTrue(restored.inbox().isEmpty());
        Assertions.assertEquals(50, restored.history().size());
        Assertions.assertEquals(1_064, restored.history().getFirst().reachedAt());
        Assertions.assertEquals(1_015, restored.history().getLast().reachedAt());
    }

    private static BazaarData.MarketSnapshot snapshot(boolean reached) {
        return snapshotAtPrice(reached ? 95 : 105);
    }

    private static BazaarData.MarketSnapshot snapshotAtPrice(double price) {
        String level = "[{\"pricePerUnit\":" + price + ",\"amount\":120,\"orders\":1}]";
        var reply = new Gson().fromJson("{\"products\":{\"DIAMOND\":{\"buy_summary\":" + level
            + ",\"sell_summary\":" + level + "}}}", SkyBlockBazaarReply.class);
        return BazaarData.MarketSnapshot.fromProducts(reply.getProducts());
    }
}
