package com.github.lutzluca.btrbz.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.lutzluca.btrbz.core.AlertManager.Alert;
import com.github.lutzluca.btrbz.data.BazaarData.MarketPrices;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AlertConfigSerializationTest {

    private final Gson gson = new GsonBuilder()
        .registerTypeAdapter(Alert.class, new Alert.GsonAdapter())
        .registerTypeAdapter(IndexedProduct.class, new IndexedProduct.GsonAdapter())
        .create();

    @ParameterizedTest
    @CsvSource({"BuyOrder,100,true", "SellOffer,120,false", "InstaBuy,120,true", "InstaSell,100,false"})
    void legacyTypesKeepTheirWatchedPriceAndTriggerDirection(String legacyType, double price, boolean below) {
        var json = validAlertJson();
        json.addProperty("type", legacyType);
        var alert = this.gson.fromJson(json, Alert.class);
        var prices = new MarketPrices(Optional.of(100.0), Optional.of(120.0));

        Assertions.assertEquals(price, alert.type.source().price(prices).orElseThrow());
        Assertions.assertTrue(alert.type.isReached(123.4, alert.price));
        Assertions.assertEquals(below, alert.type.isReached(123.3, alert.price));
        Assertions.assertEquals(!below, alert.type.isReached(123.5, alert.price));
        Assertions.assertEquals(1_700_000_000_000L, alert.createdAt);
        Assertions.assertEquals(1000L, alert.remindedAfter);
        var serialized = this.gson.toJsonTree(alert).getAsJsonObject();
        Assertions.assertTrue(serialized.get("type").isJsonObject());
        Assertions.assertEquals(alert.type, this.gson.fromJson(serialized, Alert.class).type);
    }

    @ParameterizedTest
    @CsvSource({"price,0", "price,-1", "price,0.04", "price,NaN", "price,Infinity", "price,-Infinity", "createdAt,-1"})
    void skipsDefinitionsThatFailValidation(String field, String value) {
        var json = validAlertJson();
        json.addProperty(field, value);
        Assertions.assertNull(this.gson.fromJson(json, Alert.class));
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
              "type": {"source": "Sell", "direction": "Above"},
              "price": 123.4,
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

            assertTrue(serialized.has("product"));
            assertFalse(serialized.has("productId"));
            assertFalse(serialized.has("formattedName"));
            assertFalse(serialized.has("strippedName"));
            assertEquals(
                "ENCHANTED_DIAMOND",
                serialized.getAsJsonObject("product").get("productId").getAsString());
            assertEquals(
                "§aEnchanted Diamond",
                serialized.getAsJsonObject("product").get("formattedName").getAsString());
            assertFalse(serialized.getAsJsonObject("product").has("strippedName"));
            assertEquals(alert.id, reparsed.id);
            assertEquals(alert.createdAt, reparsed.createdAt);
            assertEquals(alert.productId(), reparsed.productId());
            assertEquals(alert.productName(), reparsed.productName());
            assertEquals(alert.type, reparsed.type);
            assertEquals(alert.price, reparsed.price);
            assertEquals(alert.remindedAfter, reparsed.remindedAfter);
        }
    }
}
