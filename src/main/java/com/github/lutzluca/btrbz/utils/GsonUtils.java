package com.github.lutzluca.btrbz.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.util.Optional;

public final class GsonUtils {

    private GsonUtils() { }

    public static JsonElement required(JsonObject obj, String name, String subject) {
        if (obj == null || !obj.has(name) || obj.get(name).isJsonNull()) {
            throw new JsonParseException(subject + " is missing required field '" + name + "'");
        }
        return obj.get(name);
    }

    public static String requiredString(JsonObject obj, String name, String subject) {
        return GsonUtils.optionalString(obj, name)
            .orElseThrow(() -> new JsonParseException(subject + " is missing required field '" + name + "'"));
    }

    public static Optional<String> optionalString(JsonObject obj, String name) {
        if (obj == null || !obj.has(name) || obj.get(name).isJsonNull()) {
            return Optional.empty();
        }

        var value = obj.get(name).getAsString();
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    public static Optional<Long> optionalLong(JsonObject obj, String name) {
        if (obj == null || !obj.has(name) || obj.get(name).isJsonNull()) {
            return Optional.empty();
        }

        return Optional.of(obj.get(name).getAsLong());
    }
}
