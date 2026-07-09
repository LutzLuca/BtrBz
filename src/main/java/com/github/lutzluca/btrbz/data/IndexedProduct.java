package com.github.lutzluca.btrbz.data;

import com.github.lutzluca.btrbz.utils.GsonUtils;
import com.github.lutzluca.btrbz.utils.Utils;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;

/**
 * Canonical product metadata from the conversion index.
 * Store this in index-backed config features; convert to ProductIdentity only at runtime market lookup boundaries.
 */
public record IndexedProduct(String productId, String formattedName) {

    public IndexedProduct {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("productId must not be blank");
        }

        if (formattedName == null || formattedName.isBlank()) {
            formattedName = productId;
        }
        formattedName = formattedName.trim();
    }

    @Override
    public String toString() {
        return "IndexedProduct[productId=" + this.productId + ", strippedName=" + this.strippedName() + "]";
    }

    public String strippedName() {
        var stripped = Utils.cleanDisplayName(this.formattedName);
        return stripped.isBlank() ? this.productId : stripped;
    }

    public static final class GsonAdapter implements JsonSerializer<IndexedProduct>, JsonDeserializer<IndexedProduct> {

        @Override
        public JsonElement serialize(
            IndexedProduct src,
            Type typeOfSrc,
            JsonSerializationContext ctx
        ) {
            var obj = new JsonObject();
            obj.addProperty("productId", src.productId());
            obj.addProperty("formattedName", src.formattedName());
            return obj;
        }

        @Override
        public IndexedProduct deserialize(
            JsonElement json,
            Type typeOfT,
            JsonDeserializationContext ctx
        ) throws JsonParseException {
            if (json == null || !json.isJsonObject()) {
                throw new JsonParseException("IndexedProduct must be an object");
            }

            var obj = json.getAsJsonObject();
            var productId = GsonUtils.requiredString(obj, "productId", "IndexedProduct");
            var formattedName = GsonUtils.requiredString(obj, "formattedName", "IndexedProduct");
            return new IndexedProduct(productId, formattedName);
        }
    }
}
