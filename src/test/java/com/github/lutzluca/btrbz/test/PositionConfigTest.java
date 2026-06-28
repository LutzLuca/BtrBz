package com.github.lutzluca.btrbz.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.github.lutzluca.btrbz.core.modules.OrderBookPriceModule.OrderBookPriceConfig;
import com.github.lutzluca.btrbz.core.modules.OrderLimitModule.OrderLimitConfig;
import com.github.lutzluca.btrbz.core.modules.orderpreset.OrderPresetsConfig;
import com.github.lutzluca.btrbz.utils.Position;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PositionConfigTest {

    private final Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .registerTypeAdapter(Position.class, new Position.GsonAdapter())
        .create();

    @Nested
    @DisplayName("Position Serialization")
    class PositionSerialization {

        @Test
        void serializesConfigPositionAsNestedObject() {
            OrderLimitConfig config = new OrderLimitConfig();
            config.position = new Position(12, 34);

            JsonObject json = gson.toJsonTree(config)
                .getAsJsonObject();

            assertEquals(
                12,
                json.getAsJsonObject("position")
                    .get("x")
                    .getAsInt()
            );
            assertEquals(
                34,
                json.getAsJsonObject("position")
                    .get("y")
                    .getAsInt()
            );
            assertFalse(json.has("x"));
            assertFalse(json.has("y"));
        }

        @Test
        void deserializesNestedPositionObject() {
            String json = """
                {
                  \"position\": { \"x\": 45, \"y\": 67 },
                  \"enabled\": true
                }
                """;

            OrderLimitConfig config = gson.fromJson(json, OrderLimitConfig.class);

            assertEquals(new Position(45, 67), config.position);
        }

        @Test
        void deserializesNamedPositionObjects() {
            String json = """
                {
                  \"container_position\": { \"x\": 11, \"y\": 22 },
                  \"sign_position\": { \"x\": 33, \"y\": 44 },
                  \"enabled\": true
                }
                """;

            OrderPresetsConfig config = gson.fromJson(json, OrderPresetsConfig.class);

            assertEquals(new Position(11, 22), config.containerPosition);
            assertEquals(new Position(33, 44), config.signPosition);
        }

        @Test
        void usesNamedPositionFieldForSignOverlay() {
            OrderBookPriceConfig config = new OrderBookPriceConfig();
            config.signPosition = new Position(90, 91);

            JsonObject json = gson.toJsonTree(config)
                .getAsJsonObject();

            assertEquals(
                90,
                json.getAsJsonObject("sign_position")
                    .get("x")
                    .getAsInt()
            );
            assertEquals(
                91,
                json.getAsJsonObject("sign_position")
                    .get("y")
                    .getAsInt()
            );
            assertFalse(json.has("sign_x"));
            assertFalse(json.has("sign_y"));
        }

        @Test
        void serializesNullPosition() {
            OrderLimitConfig orderLimitConfig = new OrderLimitConfig();
            orderLimitConfig.position = null;

            JsonObject orderLimitJson = gson.toJsonTree(orderLimitConfig)
                .getAsJsonObject();

            assertFalse(orderLimitJson.has("position"));
            assertNull(gson.fromJson(orderLimitJson, OrderLimitConfig.class).position);

            OrderBookPriceConfig orderBookPriceConfig = new OrderBookPriceConfig();
            orderBookPriceConfig.signPosition = null;

            JsonObject orderBookPriceJson = gson.toJsonTree(orderBookPriceConfig)
                .getAsJsonObject();

            assertFalse(orderBookPriceJson.has("sign_position"));
            assertNull(gson.fromJson(orderBookPriceJson, OrderBookPriceConfig.class).signPosition);
        }

        @Test
        void deserializesInvalidPositionThrows() {
            String missingX = """
                {
                  \"position\": { \"y\": 67 },
                  \"enabled\": true
                }
                """;

            String missingY = """
                {
                  \"position\": { \"x\": 45 },
                  \"enabled\": true
                }
                """;

            assertThrows(JsonParseException.class, () -> gson.fromJson(missingX, OrderLimitConfig.class));
            assertThrows(JsonParseException.class, () -> gson.fromJson(missingY, OrderLimitConfig.class));
        }

        @Test
        void roundTripPositionSerialization() {
            OrderPresetsConfig config = new OrderPresetsConfig();
            config.containerPosition = new Position(21, 43);

            OrderPresetsConfig roundTripped = gson.fromJson(gson.toJson(config), OrderPresetsConfig.class);

            assertEquals(new Position(21, 43), roundTripped.containerPosition);
        }
    }
}