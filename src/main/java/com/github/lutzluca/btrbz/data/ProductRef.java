package com.github.lutzluca.btrbz.data;

import com.github.lutzluca.btrbz.utils.GsonUtils;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;

public record ProductRef(String productId, String displayName) implements ProductIdentity {

    public ProductRef {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("productId must not be blank");
        }

        if (displayName == null || displayName.isBlank()) {
            displayName = productId;
        }
    }

    public static final class GsonAdapter implements JsonSerializer<ProductRef>, JsonDeserializer<ProductRef> {

        @Override
        public JsonElement serialize(
            ProductRef src,
            Type typeOfSrc,
            JsonSerializationContext ctx
        ) {
            var obj = new JsonObject();
            obj.addProperty("productId", src.productId());
            obj.addProperty("displayName", src.displayName());
            return obj;
        }

        @Override
        public ProductRef deserialize(
            JsonElement json,
            Type typeOfT,
            JsonDeserializationContext ctx
        ) throws JsonParseException {
            if (json == null || !json.isJsonObject()) {
                throw new JsonParseException("ProductRef must be an object");
            }

            var obj = json.getAsJsonObject();
            var productId = GsonUtils.requiredString(obj, "productId", "ProductRef");
            var displayName = GsonUtils.optionalString(obj, "displayName").orElse(productId);
            return new ProductRef(productId, displayName);
        }
    }
}
