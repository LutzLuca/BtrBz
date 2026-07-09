package com.github.lutzluca.btrbz.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.lutzluca.btrbz.core.AlertManager.Alert;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AlertConfigSerializationTest {

    private final Gson gson = new GsonBuilder()
        .registerTypeAdapter(Alert.class, new Alert.GsonAdapter())
        .registerTypeAdapter(IndexedProduct.class, new IndexedProduct.GsonAdapter())
        .create();

    @Nested
    @DisplayName("current alert config")
    class CurrentAlertConfig {

        @Test
        void roundTripsNestedIndexedProductShape() {
            var json = """
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
                """;

            var alert = AlertConfigSerializationTest.this.gson.fromJson(json, Alert.class);
            var serialized = AlertConfigSerializationTest.this.gson.toJsonTree(alert).getAsJsonObject();
            var reparsed = AlertConfigSerializationTest.this.gson.fromJson(
                serialized,
                Alert.class
            );

            assertTrue(serialized.has("product"));
            assertFalse(serialized.has("productId"));
            assertFalse(serialized.has("formattedName"));
            assertFalse(serialized.has("strippedName"));
            assertEquals(
                "ENCHANTED_DIAMOND",
                serialized.getAsJsonObject("product").get("productId").getAsString()
            );
            assertEquals(
                "§aEnchanted Diamond",
                serialized.getAsJsonObject("product").get("formattedName").getAsString()
            );
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

    @Nested
    @DisplayName("legacy alert config")
    class LegacyAlertConfig {

        @Test
        void skipsFlatProductFields() {
            var json = """
                {
                  "id": "29f2d47e-f09f-4c68-901f-f41a547d4145",
                  "createdAt": 1700000000000,
                  "productName": "Enchanted Diamond",
                  "productId": "ENCHANTED_DIAMOND",
                  "type": "SellOffer",
                  "price": 123.4
                }
                """;

            assertNull(AlertConfigSerializationTest.this.gson.fromJson(json, Alert.class));
        }
    }
}
