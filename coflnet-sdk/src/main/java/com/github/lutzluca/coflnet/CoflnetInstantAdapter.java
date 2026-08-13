package com.github.lutzluca.coflnet;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

final class CoflnetInstantAdapter implements JsonDeserializer<Instant>, JsonSerializer<Instant> {
    @Override
    public Instant deserialize(JsonElement json, Type type, JsonDeserializationContext context)
            throws JsonParseException {
        if (!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isString()) {
            throw new JsonParseException("Expected an ISO-8601 timestamp string");
        }

        String value = json.getAsString();
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeException ignored) {
            try {
                // Coflnet currently emits offsetless values even though its schema says date-time.
                return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC);
            } catch (DateTimeException invalid) {
                throw new JsonParseException("Invalid Coflnet timestamp: " + value, invalid);
            }
        }
    }

    @Override
    public JsonElement serialize(Instant value, Type type, JsonSerializationContext context) {
        return new JsonPrimitive(value.toString());
    }
}
