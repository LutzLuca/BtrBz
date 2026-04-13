package com.github.lutzluca.btrbz.utils;

import java.lang.reflect.Type;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import org.apache.commons.lang3.tuple.Pair;

public record Position(int x, int y) {

    public static Position from(Pair<Integer, Integer> pos) {
        return new Position(pos.getLeft(), pos.getRight());
    }

    public static class GsonAdapter implements JsonSerializer<Position>, JsonDeserializer<Position> {

        @Override
        public JsonElement serialize(Position src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject object = new JsonObject();
            object.addProperty("x", src.x());
            object.addProperty("y", src.y());
            return object;
        }

        @Override
        public Position deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (json == null || json.isJsonNull()) {
                return null;
            }

            JsonObject object = json.getAsJsonObject();
            JsonElement xElement = object.get("x");
            JsonElement yElement = object.get("y");
            if (xElement == null || yElement == null || xElement.isJsonNull() || yElement.isJsonNull()) {
                throw new JsonParseException("Position requires both x and y properties");
            }

            return new Position(xElement.getAsInt(), yElement.getAsInt());
        }
    }
}
