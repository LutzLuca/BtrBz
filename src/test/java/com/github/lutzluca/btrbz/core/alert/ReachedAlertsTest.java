package com.github.lutzluca.btrbz.core.alert;

import com.github.lutzluca.btrbz.core.alert.AlertType.Direction;
import com.github.lutzluca.btrbz.core.alert.AlertType.PriceSource;
import com.github.lutzluca.btrbz.core.alert.AlertCondition.Kind;
import com.github.lutzluca.btrbz.core.alert.AlertCondition.LiquiditySide;
import com.github.lutzluca.btrbz.core.config.ConfigStore;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import com.google.gson.Gson;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
    void keepsIndependentHistoryLimitsAndPersistsIndividualRemoval() {
        var path = this.tempDir.resolve("reached-alerts.json");
        var store = new ConfigStore(path);
        var data = new BazaarData();
        var notifications = new ArrayList<ReachedAlert>();
        var manager = new AlertManager(data, () -> store.config().alert, store::save, notifications::add);
        data.addListener(manager::onBazaarUpdate);
        for (int index = 1; index <= 12; index++) {
            manager.saveAlert(null, definition(100 + index)).get();
            manager.saveAlert(null, new AlertDefinition(System.currentTimeMillis(), PRODUCT,
                new AlertCondition.Liquidity(LiquiditySide.SellOffers, index, 100))).get();
        }

        long before = System.currentTimeMillis();
        publish(data, "100");

        Assertions.assertTrue(manager.alerts().isEmpty());
        Assertions.assertEquals(24, notifications.size());
        var history = manager.reachedAlerts();
        Assertions.assertEquals(20, history.size());
        var prices = history.stream().filter(entry -> entry.alert().kind() == Kind.Price).toList();
        var liquidity = history.stream().filter(entry -> entry.alert().kind() == Kind.Liquidity).toList();
        Assertions.assertEquals(List.of(112.0, 111.0, 110.0, 109.0, 108.0, 107.0, 106.0, 105.0, 104.0, 103.0),
            prices.stream().map(entry -> ((AlertCondition.Price) entry.alert().condition).price()).toList());
        Assertions.assertEquals(List.of(12L, 11L, 10L, 9L, 8L, 7L, 6L, 5L, 4L, 3L),
            liquidity.stream().map(entry -> ((AlertCondition.Liquidity) entry.alert().condition).quantity()).toList());
        long after = System.currentTimeMillis();
        for (var entry : history) {
            var expected = entry.alert().kind() == Kind.Price
                ? new AlertCondition.Observation.Price(100) : new AlertCondition.Observation.Liquidity(100);
            Assertions.assertEquals(expected, entry.observation(), entry.alert().kind().name());
            Assertions.assertTrue(entry.reachedAt() >= before && entry.reachedAt() <= after,
                "capture time for " + entry.alert().id);
        }
        Assertions.assertThrows(UnsupportedOperationException.class, () -> manager.reachedAlerts().clear());

        publish(data, "90");
        Assertions.assertEquals(history, manager.reachedAlerts());
        Assertions.assertEquals(24, notifications.size());

        var reloaded = new ConfigStore(path);
        Assertions.assertTrue(reloaded.load());
        var restored = new AlertManager(data, () -> reloaded.config().alert, reloaded::save, notifications::add);
        var savedHistory = restored.reachedAlerts();
        Assertions.assertEquals(20, savedHistory.size());
        for (int index = 0; index < history.size(); index++) {
            var entry = history.get(index);
            var saved = savedHistory.get(index);
            Assertions.assertEquals(entry.alert().id, saved.alert().id);
            Assertions.assertEquals(PRODUCT, saved.alert().product);
            Assertions.assertEquals(entry.reachedAt(), saved.reachedAt());
            Assertions.assertEquals(entry.observation(), saved.observation());
            Assertions.assertEquals(entry.alert().condition, saved.alert().condition);
        }
        restored.onBazaarUpdate(data.currentSnapshot());
        Assertions.assertEquals(24, notifications.size(), "reloading history must not redeliver it");
        var removedId = savedHistory.getFirst().alert().id;
        Assertions.assertTrue(restored.removeReachedAlert(removedId));
        Assertions.assertFalse(restored.removeReachedAlert(removedId));
        Assertions.assertTrue(reloaded.load());
        Assertions.assertEquals(19, reloaded.config().alert.reachedAlerts.size());
        Assertions.assertEquals(10, reloaded.config().alert.reachedAlerts.stream()
            .filter(entry -> entry.alert().kind() == Kind.Price).count());
        Assertions.assertEquals(9, reloaded.config().alert.reachedAlerts.stream()
            .filter(entry -> entry.alert().kind() == Kind.Liquidity).count());
        Assertions.assertTrue(reloaded.config().alert.reachedAlerts.stream()
            .noneMatch(entry -> entry.alert().id.equals(removedId)));
        Assertions.assertTrue(reloaded.config().alert.alerts.isEmpty());
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

        data.publishSnapshot(BazaarData.MarketSnapshot.fromProducts(Map.of()));
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
    void throwingSaveCallbackKeepsReachedHistoryAndRollsBackRemoval() {
        var config = new AlertConfig();
        var data = new BazaarData();
        var failSave = new boolean[1];
        var notifications = new ArrayList<ReachedAlert>();
        var manager = new AlertManager(data, () -> config, () -> {
            if (failSave[0]) {
                throw new IllegalStateException("save callback failed");
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
        data.publishSnapshot(BazaarData.MarketSnapshot.fromProducts(reply.getProducts()));
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
