package com.github.lutzluca.btrbz.core.config;

import com.github.lutzluca.btrbz.core.alert.AlertCondition;

import com.github.lutzluca.btrbz.core.alert.Alert;
import com.github.lutzluca.btrbz.core.alert.ReachedAlert;
import com.github.lutzluca.btrbz.core.alert.AlertManager;
import com.github.lutzluca.btrbz.core.alert.AlertDefinition;
import com.github.lutzluca.btrbz.core.alert.AlertType;
import com.github.lutzluca.btrbz.core.alert.AlertType.Direction;
import com.github.lutzluca.btrbz.core.alert.AlertType.PriceSource;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.github.lutzluca.btrbz.core.productinfo.ProductInfoConfig.Site;
import com.github.lutzluca.btrbz.core.widgets.hud.BazaarOrdersWidgetConfig.ToggleHintState;
import java.util.List;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigStoreTest {

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("persistence")
    class Persistence {

        @Test
        void bookmarksPersistOnlyProductMetadataAndKeepManualOrder() throws IOException {
            var path = ConfigStoreTest.this.tempDir.resolve("bookmarks.json");
            var store = new ConfigStore(path);
            var products = List.of(
                new IndexedProduct("ENCHANTMENT_ULTIMATE_WISE_5", "§9Ultimate Wise V"),
                new IndexedProduct("BOOSTER_COOKIE", "§6Booster Cookie"),
                new IndexedProduct("MISSING_PRODUCT", "§dSaved Product Name"));
            store.config().widgets.bookmarks.products.addAll(products);

            store.save();

            var serialized = JsonParser.parseString(Files.readString(path)).getAsJsonObject()
                .getAsJsonObject("widgets").getAsJsonObject("bookmarks").getAsJsonArray("products");
            Assertions.assertEquals(JsonParser.parseString("""
                [
                  {"productId": "ENCHANTMENT_ULTIMATE_WISE_5", "formattedName": "§9Ultimate Wise V"},
                  {"productId": "BOOSTER_COOKIE", "formattedName": "§6Booster Cookie"},
                  {"productId": "MISSING_PRODUCT", "formattedName": "§dSaved Product Name"}
                ]
                """), serialized);

            var reloaded = new ConfigStore(path);
            Assertions.assertTrue(reloaded.load());
            Assertions.assertEquals(products, reloaded.config().widgets.bookmarks.products);
        }

        @Test
        void retiredBookmarkStorageDoesNotPreventLoadingWidgetPreferences() throws IOException {
            var path = ConfigStoreTest.this.tempDir.resolve("retired-bookmarks.json");
            Files.writeString(path, """
                {
                  "widgets": {
                    "global_fine_tune_scale": 1.35,
                    "bookmarks": {
                      "content_width": 240,
                      "items": [
                        {
                          "product": {"productId": "BOOSTER_COOKIE", "formattedName": "§6Booster Cookie"},
                          "itemStack": {"id": "minecraft:cookie", "components": "obsolete stack data"}
                        }
                      ]
                    }
                  }
                }
                """);

            var store = new ConfigStore(path);
            Assertions.assertTrue(store.load());
            Assertions.assertEquals(1.35, store.config().widgets.globalFineTuneScale);
            Assertions.assertEquals(240, store.config().widgets.bookmarks.contentWidth);
            Assertions.assertTrue(store.config().widgets.bookmarks.products.isEmpty());
        }

        @Test
        void alertEditsAndDeletionPersistThroughTheManager() {
            var path = ConfigStoreTest.this.tempDir.resolve("alert-editor.json");
            var store = new ConfigStore(path);
            var manager = new AlertManager(new BazaarData(), () -> store.config().alert, store::save, _ -> {});
            var product = new IndexedProduct("ENCHANTED_DIAMOND", "Enchanted Diamond");
            var created = manager.saveAlert(null,
                new AlertDefinition(1_000L, product,
                    new AlertCondition.Price(new AlertType(PriceSource.Sell, Direction.Below), 100)))
                .get();
            manager.saveAlert(created.id,
                new AlertDefinition(2_000L, product,
                    new AlertCondition.Price(new AlertType(PriceSource.Sell, Direction.Above), 14.44)))
                .get();

            var reloaded = new ConfigStore(path);
            Assertions.assertTrue(reloaded.load());
            var saved = reloaded.config().alert.alerts.getFirst();
            Assertions.assertEquals(created.id, saved.id);
            Assertions.assertEquals(2_000L, saved.createdAt);
            Assertions.assertEquals(new AlertType(PriceSource.Sell, Direction.Above),
                ((AlertCondition.Price) saved.condition).type());
            Assertions.assertEquals(14.4, ((AlertCondition.Price) saved.condition).price());
            Assertions.assertEquals(product, saved.product);

            var restoredManager = new AlertManager(new BazaarData(), () -> reloaded.config().alert, reloaded::save,
                _ -> {});
            Assertions.assertTrue(restoredManager.removeAlert(saved.id));
            var afterDelete = new ConfigStore(path);
            Assertions.assertTrue(afterDelete.load());
            Assertions.assertTrue(afterDelete.config().alert.alerts.isEmpty());
        }

        @Test
        void invalidAlertTypeDoesNotPreventLoadingOtherSettings() throws IOException {
            var path = ConfigStoreTest.this.tempDir.resolve("invalid-alert-type.json");
            var json = JsonParser.parseString("""
                {"tax": 1.5, "alert": {"alerts": []}}
                """).getAsJsonObject();
            var gson = new GsonBuilder()
                .registerTypeAdapter(Alert.class, new Alert.GsonAdapter())
                .registerTypeAdapter(IndexedProduct.class, new IndexedProduct.GsonAdapter())
                .create();
            var valid = gson.toJsonTree(createAlert()).getAsJsonObject();
            var invalid = valid.deepCopy();
            invalid.getAsJsonObject("condition").addProperty("type", "unsupported");
            var alerts = json.getAsJsonObject("alert").getAsJsonArray("alerts");
            alerts.add(invalid);
            alerts.add(valid);
            Files.writeString(path, json.toString());

            var store = new ConfigStore(path);
            Assertions.assertTrue(store.load());
            var manager = new AlertManager(new BazaarData(), () -> store.config().alert, store::save, _ -> {});
            Assertions.assertEquals(1.5, store.config().tax);
            Assertions.assertEquals(1, manager.alerts().size());
            Assertions.assertEquals(123.4, ((AlertCondition.Price) manager.alerts().getFirst().condition).price());
        }

        @Test
        void changedUpdateSavesImmediately() {
            var path = ConfigStoreTest.this.tempDir.resolve("changed.json");
            var store = new ConfigStore(path);

            boolean changed = store.updateIfChanged(config -> {
                config.tax = 2.5;
                return true;
            });

            Assertions.assertTrue(changed);
            Assertions.assertTrue(Files.exists(path));

            var reloaded = new ConfigStore(path);
            Assertions.assertTrue(reloaded.load());
            Assertions.assertEquals(2.5, reloaded.config().tax);
        }

        @Test
        void unchangedUpdateDoesNotWrite() {
            var path = ConfigStoreTest.this.tempDir.resolve("unchanged.json");
            var store = new ConfigStore(path);

            Assertions.assertFalse(store.updateIfChanged(_ -> false));
            Assertions.assertFalse(Files.exists(path));
        }

        @Test
        void roundTripsCurrentSettingsAndRegisteredAdapters() throws IOException {
            record Preferences(Site site, String wireName, ToggleHintState hintState, boolean enabled) {}
            var cases = List.of(
                new Preferences(Site.Coflnet, "Coflnet", ToggleHintState.Unseen, false),
                new Preferences(Site.SkyblockBz, "SkyblockBz", ToggleHintState.Shown, true),
                new Preferences(Site.SkyblockFinance, "SkyblockFinance", ToggleHintState.Dismissed, false));
            for (var preferences : cases) {
                var path = ConfigStoreTest.this.tempDir.resolve(preferences.wireName() + ".json");
                var store = new ConfigStore(path);
                var config = store.config();
                config.enabled = preferences.enabled();
                config.alwaysActive = !preferences.enabled();
                config.tax = 3.25;
                config.widgets.globalFineTuneScale = 1.35;
                config.widgets.bazaarOrders.toggleHintState = preferences.hintState();
                config.productInfo.site = preferences.site();
                var alert = createAlert();
                config.alert.alerts.add(alert);
                config.alert.reachedAlerts.add(new ReachedAlert(alert, 2_000L,
                    new AlertCondition.Observation.Price(1_012)));
                config.alert.toastOnAlert = false;
                config.alert.alsoSendChatMessage = true;
                config.alert.detailedAlertToasts = true;

                store.save();

                var serialized = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                Assertions.assertEquals(preferences.wireName(),
                    serialized.getAsJsonObject("productInfo").get("site").getAsString(), preferences.wireName());
                var serializedAlert = serialized.getAsJsonObject("alert").getAsJsonArray("alerts")
                    .get(0).getAsJsonObject();
                Assertions.assertTrue(serializedAlert.has("product"));
                Assertions.assertFalse(serializedAlert.has("productId"));
                Assertions.assertFalse(serialized.getAsJsonObject("alert").has("sound_on_alert"));

                var reloaded = new ConfigStore(path);
                Assertions.assertTrue(reloaded.load(), preferences.wireName());
                var result = reloaded.config();
                Assertions.assertEquals(preferences.enabled(), result.enabled, preferences.wireName());
                Assertions.assertEquals(!preferences.enabled(), result.alwaysActive, preferences.wireName());
                Assertions.assertEquals(3.25, result.tax);
                Assertions.assertEquals(1.35, result.widgets.globalFineTuneScale);
                Assertions.assertEquals(preferences.site(), result.productInfo.site, preferences.wireName());
                Assertions.assertEquals(preferences.hintState(), result.widgets.bazaarOrders.supportedToggleHintState(),
                    preferences.wireName());
                Assertions.assertFalse(result.alert.toastOnAlert);
                Assertions.assertTrue(result.alert.alsoSendChatMessage);
                Assertions.assertTrue(result.alert.detailedAlertToasts);
                Assertions.assertEquals(1, result.alert.alerts.size());
                Assertions.assertEquals(alert.id, result.alert.alerts.getFirst().id);
                Assertions.assertEquals("ENCHANTED_DIAMOND", result.alert.alerts.getFirst().productId());
                Assertions.assertEquals("Enchanted Diamond", result.alert.alerts.getFirst().productName());
                Assertions.assertEquals(alert.id, result.alert.reachedAlerts.getFirst().alert().id);
                Assertions.assertEquals(2_000L, result.alert.reachedAlerts.getFirst().reachedAt());
                Assertions.assertEquals(new AlertCondition.Observation.Price(1_012),
                    result.alert.reachedAlerts.getFirst().observation());
            }
        }

        @Test
        void existingAlertDataLoadsWhenNotificationSettingsAreAbsent() throws IOException {
            var path = ConfigStoreTest.this.tempDir.resolve("existing-alert-data.json");
            var store = new ConfigStore(path);
            var alert = createAlert();
            store.config().alert.alerts.add(alert);
            store.config().alert.reachedAlerts
                .add(new ReachedAlert(alert, 2_000L, new AlertCondition.Observation.Price(1_012)));
            store.config().alert.toastOnAlert = false;
            store.config().alert.alsoSendChatMessage = true;
            store.config().alert.detailedAlertToasts = true;
            store.save();
            var serialized = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            var settings = serialized.getAsJsonObject("alert");
            for (var key : List.of("also_send_chat_message", "detailed_alert_toasts", "toast_on_alert")) {
                Assertions.assertNotNull(settings.remove(key), key + " must exist before removal");
                Assertions.assertFalse(settings.has(key), key + " must be absent in the load fixture");
            }
            Files.writeString(path, serialized.toString());

            var reloaded = new ConfigStore(path);
            Assertions.assertTrue(reloaded.load());
            var config = reloaded.config();
            Assertions.assertTrue(config.alert.toastOnAlert);
            Assertions.assertFalse(config.alert.alsoSendChatMessage);
            Assertions.assertFalse(config.alert.detailedAlertToasts);
            Assertions.assertEquals(alert.id, config.alert.alerts.getFirst().id);
            Assertions.assertEquals(alert.id, config.alert.reachedAlerts.getFirst().alert().id);
            Assertions.assertEquals(new AlertCondition.Observation.Price(1_012),
                config.alert.reachedAlerts.getFirst().observation());
        }

        @Test
        void writeIoFailureIsNotReportedToTheManager() throws IOException {
            var blocker = ConfigStoreTest.this.tempDir.resolve("blocked-parent");
            Files.writeString(blocker, "existing file blocks directory creation");
            var path = blocker.resolve("config.json");
            var store = new ConfigStore(path);
            var manager = new AlertManager(new BazaarData(), () -> store.config().alert, store::save, _ -> {});

            var result = manager.saveAlert(null, new AlertDefinition(1_000L,
                new IndexedProduct("ENCHANTED_DIAMOND", "Enchanted Diamond"),
                new AlertCondition.Price(new AlertType(PriceSource.Sell, Direction.Above), 100)));

            // YACL logs the real IOException and returns normally; the manager sees a successful callback.
            Assertions.assertTrue(result.isSuccess());
            Assertions.assertEquals(result.get().id, manager.alerts().getFirst().id);
            Assertions.assertFalse(Files.exists(path));
            Assertions.assertEquals("existing file blocks directory creation", Files.readString(blocker));
        }

        @Test
        void independentStoresDoNotShareInstancesOrPaths() {
            var firstPath = ConfigStoreTest.this.tempDir.resolve("first.json");
            var secondPath = ConfigStoreTest.this.tempDir.resolve("second.json");
            var first = new ConfigStore(firstPath);
            var second = new ConfigStore(secondPath);

            Assertions.assertNotSame(first.config(), second.config());
            first.config().tax = 1.5;
            second.config().tax = 4.5;
            first.save();
            second.save();

            var firstReloaded = new ConfigStore(firstPath);
            var secondReloaded = new ConfigStore(secondPath);
            Assertions.assertTrue(firstReloaded.load());
            Assertions.assertTrue(secondReloaded.load());
            Assertions.assertEquals(1.5, firstReloaded.config().tax);
            Assertions.assertEquals(4.5, secondReloaded.config().tax);
        }
    }

    private static Alert createAlert() {
        Gson gson = new GsonBuilder()
            .registerTypeAdapter(Alert.class, new Alert.GsonAdapter())
            .registerTypeAdapter(IndexedProduct.class, new IndexedProduct.GsonAdapter())
            .create();
        return gson.fromJson("""
            {
              "id": "29f2d47e-f09f-4c68-901f-f41a547d4145",
              "createdAt": 1700000000000,
              "product": {
                "productId": "ENCHANTED_DIAMOND",
                "formattedName": "§aEnchanted Diamond"
              },
              "kind": "Price",
              "condition": {"type": {"source": "Sell", "direction": "Above"}, "price": 123.4},
              "remindedAfter": 1000
            }
            """, Alert.class);
    }
}
