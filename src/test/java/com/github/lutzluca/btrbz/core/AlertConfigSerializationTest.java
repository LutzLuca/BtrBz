package com.github.lutzluca.btrbz.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.lutzluca.btrbz.core.AlertManager.Alert;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AlertConfigSerializationTest {

    private final Gson gson = new GsonBuilder()
        .registerTypeAdapter(Alert.class, new Alert.GsonAdapter())
        .create();

    @Nested
    @DisplayName("current alert config")
    class CurrentAlertConfig {

        @Test
        void roundTripsFlatProductRefShape() {
            var json = """
                {
                  "id": "29f2d47e-f09f-4c68-901f-f41a547d4145",
                  "createdAt": 1700000000000,
                  "productId": "ENCHANTED_DIAMOND",
                  "displayName": "Enchanted Diamond",
                  "type": "SellOffer",
                  "price": 123.4,
                  "remindedAfter": 1000
                }
                """;

            var alert = AlertConfigSerializationTest.this.gson.fromJson(json, Alert.class);
            var reparsed = AlertConfigSerializationTest.this.gson.fromJson(
                AlertConfigSerializationTest.this.gson.toJson(alert),
                Alert.class
            );

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
