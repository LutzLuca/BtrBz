package com.github.lutzluca.btrbz.data.conversions;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;

final class ProductNameSourceAdapter
    implements JsonSerializer<ProductNameSource>, JsonDeserializer<ProductNameSource> {

    @Override
    public JsonElement serialize(ProductNameSource src, Type typeOfSrc, JsonSerializationContext context) {
        var json = new JsonObject();
        switch (src) {
            case ProductNameSource.HypixelItem _ -> json.addProperty("type", "hypixel-item");
            case ProductNameSource.Neu neu -> {
                json.addProperty("type", "neu");
                json.addProperty("neuId", neu.neuId());
            }
            case ProductNameSource.Derived _ -> json.addProperty("type", "derived");
        }
        return json;
    }

    @Override
    public ProductNameSource deserialize(
        JsonElement json,
        Type typeOfT,
        JsonDeserializationContext context
    ) throws JsonParseException {
        if (json == null || !json.isJsonObject()) {
            throw new JsonParseException("Product name source must be an object");
        }

        var obj = json.getAsJsonObject();
        if (!obj.has("type")) {
            throw new JsonParseException("Product name source is missing type");
        }

        return switch (obj.get("type").getAsString()) {
            case "hypixel-item" -> new ProductNameSource.HypixelItem();
            case "neu" -> {
                if (!obj.has("neuId") || obj.get("neuId").getAsString().isBlank()) {
                    throw new JsonParseException("NEU product name source is missing neuId");
                }
                yield new ProductNameSource.Neu(obj.get("neuId").getAsString());
            }
            case "derived" -> new ProductNameSource.Derived();
            default -> throw new JsonParseException(
                "Unknown product name source type: " + obj.get("type").getAsString()
            );
        };
    }
}
