package com.github.lutzluca.btrbz.core;

import com.github.lutzluca.btrbz.core.AlertManager.Alert;
import com.github.lutzluca.btrbz.core.AlertManager.ReachedAlert;
import com.github.lutzluca.btrbz.core.alert.AlertCondition;
import com.github.lutzluca.btrbz.core.alert.AlertDefinition;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AlertConfigSerializationTest {

    private final Gson gson = new GsonBuilder()
        .registerTypeAdapter(Alert.class, new Alert.GsonAdapter())
        .registerTypeAdapter(ReachedAlert.class, new ReachedAlert.GsonAdapter())
        .registerTypeAdapter(IndexedProduct.class, new IndexedProduct.GsonAdapter())
        .create();

    @Test
    void requiresCurrentKindAndConditionInsteadOfMigratingLegacyAlerts() {
        var json = validAlertJson();
        json.remove("kind");
        Assertions.assertNull(this.gson.fromJson(json, Alert.class));
        json = validAlertJson();
        json.getAsJsonObject("condition").addProperty("type", "BuyOrder");
        Assertions.assertNull(this.gson.fromJson(json, Alert.class));
    }

    @ParameterizedTest
    @CsvSource({"price,0", "price,-1", "price,0.04", "price,NaN", "price,Infinity", "price,-Infinity", "createdAt,-1"})
    void skipsDefinitionsThatFailValidation(String field, String value) {
        var json = validAlertJson();
        (field.equals("price") ? json.getAsJsonObject("condition") : json).addProperty(field, value);
        Assertions.assertNull(this.gson.fromJson(json, Alert.class));
    }

    @ParameterizedTest
    @CsvSource({"observed,0", "observed,-1", "observed,NaN", "observed,Infinity", "reachedAt,-1"})
    void skipsInvalidReachedObservations(String field, String value) {
        var json = new JsonObject();
        json.add("alert", validAlertJson());
        json.addProperty("reachedAt", 2_000L);
        json.addProperty("observed", 130);
        json.addProperty(field, value);
        Assertions.assertNull(this.gson.fromJson(json, ReachedAlert.class));
    }

    @Test
    void liquidityHistoryPreservesWholeQuantitiesBeyondDoublePrecision() {
        var config = new AlertManager.AlertConfig();
        var manager = new AlertManager(new BazaarData(), () -> config, () -> {}, _ -> {});
        var condition = new AlertCondition.Liquidity(AlertCondition.LiquiditySide.BuyOrders, Long.MAX_VALUE, 1_000);
        var alert = manager.saveAlert(null, new AlertDefinition(1_000,
            new IndexedProduct("ENCHANTED_DIAMOND", "Enchanted Diamond"), condition)).get();
        var reached = new ReachedAlert(alert, 2_000, new AlertCondition.Observation.Liquidity(Long.MAX_VALUE));

        var restored = this.gson.fromJson(this.gson.toJson(reached), ReachedAlert.class);

        Assertions.assertEquals(condition, restored.alert().condition);
        Assertions.assertEquals(reached.observation(), restored.observation());
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new ReachedAlert(alert, 2_000, new AlertCondition.Observation.Price(1_000)));
    }

    private static JsonObject validAlertJson() {
        return JsonParser.parseString("""
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
            """).getAsJsonObject();
    }

    @Nested
    @DisplayName("current alert config")
    class CurrentAlertConfig {

        @Test
        void roundTripsNestedIndexedProductShape() {
            var json = validAlertJson();

            var alert = AlertConfigSerializationTest.this.gson.fromJson(json, Alert.class);
            var serialized = AlertConfigSerializationTest.this.gson.toJsonTree(alert).getAsJsonObject();
            var reparsed = AlertConfigSerializationTest.this.gson.fromJson(
                serialized,
                Alert.class);

            Assertions.assertTrue(serialized.has("product"));
            Assertions.assertFalse(serialized.has("productId"));
            Assertions.assertFalse(serialized.has("formattedName"));
            Assertions.assertFalse(serialized.has("strippedName"));
            Assertions.assertEquals(
                "ENCHANTED_DIAMOND",
                serialized.getAsJsonObject("product").get("productId").getAsString());
            Assertions.assertEquals(
                "§aEnchanted Diamond",
                serialized.getAsJsonObject("product").get("formattedName").getAsString());
            Assertions.assertFalse(serialized.getAsJsonObject("product").has("strippedName"));
            Assertions.assertEquals(alert.id, reparsed.id);
            Assertions.assertEquals(alert.createdAt, reparsed.createdAt);
            Assertions.assertEquals(alert.productId(), reparsed.productId());
            Assertions.assertEquals(alert.productName(), reparsed.productName());
            Assertions.assertEquals(alert.condition, reparsed.condition);
            Assertions.assertEquals(alert.remindedAfter, reparsed.remindedAfter);
        }
    }
}
