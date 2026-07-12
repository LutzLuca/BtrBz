package com.github.lutzluca.btrbz.utils;

import java.lang.reflect.Type;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
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
        public JsonElement serialize(Position src, Type typeOfSrc, JsonSerializationContext ctx) {
            if (src == null) {
                return JsonNull.INSTANCE;
            }

            JsonObject obj = new JsonObject();
            obj.addProperty("x", src.x());
            obj.addProperty("y", src.y());
            return obj;
        }

        @Override
        public Position deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext ctx) throws JsonParseException {
            if (json == null || json.isJsonNull()) {
                return null;
            }

            JsonObject object = json.getAsJsonObject();
            return new Position(
                GsonUtils.required(object, "x", "Position").getAsInt(),
                GsonUtils.required(object, "y", "Position").getAsInt()
            );
        }
    }
}
