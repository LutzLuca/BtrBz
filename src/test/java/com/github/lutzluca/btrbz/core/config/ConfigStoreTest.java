package com.github.lutzluca.btrbz.core.config;

import com.github.lutzluca.btrbz.core.AlertManager.Alert;
import com.github.lutzluca.btrbz.core.AlertManager;
import com.github.lutzluca.btrbz.core.alert.AlertDefinition;
import com.github.lutzluca.btrbz.core.alert.AlertType;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
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
        void alertEditsAndDeletionPersistThroughTheManager() {
            var path = ConfigStoreTest.this.tempDir.resolve("alert-editor.json");
            var store = new ConfigStore(path);
            var manager = new AlertManager(new BazaarData(), () -> store.config().alert, store::save);
            var product = new IndexedProduct("ENCHANTED_DIAMOND", "Enchanted Diamond");
            var created = manager.saveAlert(null,
                new AlertDefinition(1_000L, product, AlertType.BuyOrder, 100)).get();
            manager.saveAlert(created.id,
                new AlertDefinition(2_000L, product, AlertType.InstaSell, 0.04)).get();

            var reloaded = new ConfigStore(path);
            Assertions.assertTrue(reloaded.load());
            var saved = reloaded.config().alert.alerts.getFirst();
            Assertions.assertEquals(created.id, saved.id);
            Assertions.assertEquals(2_000L, saved.createdAt);
            Assertions.assertEquals(AlertType.InstaSell, saved.type);
            Assertions.assertEquals(0.04, saved.price);
            Assertions.assertEquals(product, saved.product);

            var restoredManager = new AlertManager(new BazaarData(), () -> reloaded.config().alert, reloaded::save);
            Assertions.assertTrue(restoredManager.removeAlert(saved.id));
            var afterDelete = new ConfigStore(path);
            Assertions.assertTrue(afterDelete.load());
            Assertions.assertTrue(afterDelete.config().alert.alerts.isEmpty());
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
            var path = ConfigStoreTest.this.tempDir.resolve("round-trip.json");
            var store = new ConfigStore(path);
            var config = store.config();
            config.enabled = false;
            config.tax = 3.25;
            config.widgets.globalFineTuneScale = 1.35;
            config.alert.alerts.add(createAlert());

            store.save();

            var serialized = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            var serializedAlert = serialized
                .getAsJsonObject("alert")
                .getAsJsonArray("alerts")
                .get(0)
                .getAsJsonObject();
            Assertions.assertTrue(serializedAlert.has("product"));
            Assertions.assertFalse(serializedAlert.has("productId"));

            var reloaded = new ConfigStore(path);
            Assertions.assertTrue(reloaded.load());
            var result = reloaded.config();
            Assertions.assertFalse(result.enabled);
            Assertions.assertEquals(3.25, result.tax);
            Assertions.assertEquals(1.35, result.widgets.globalFineTuneScale);
            Assertions.assertEquals(1, result.alert.alerts.size());
            Assertions.assertEquals("ENCHANTED_DIAMOND", result.alert.alerts.getFirst().productId());
            Assertions.assertEquals("Enchanted Diamond", result.alert.alerts.getFirst().productName());
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
              "type": "SellOffer",
              "price": 123.4,
              "remindedAfter": 1000
            }
            """, Alert.class);
    }
}
