package com.github.lutzluca.btrbz.core.alert;

import com.github.lutzluca.btrbz.core.alert.AlertCondition.Kind;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import java.util.UUID;
import com.github.lutzluca.btrbz.utils.GsonUtils;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class Alert {
    public final UUID id;
    public final long createdAt;
    public final IndexedProduct product;
    public final AlertCondition condition;
    long remindedAfter;

    Alert(UUID id, AlertDefinition definition, long remindedAfter) {
        this.id = id;
        this.createdAt = definition.timestamp();
        this.product = definition.product();
        this.condition = definition.condition();
        this.remindedAfter = remindedAfter;
    }

    public Kind kind() {
        return this.condition.kind();
    }

    Alert reactivated(long now) {
        return new Alert(this.id, new AlertDefinition(now, this.product, this.condition), -1);
    }

    public String productName() {
        return this.product.strippedName();
    }

    public String productId() {
        return this.product.productId();
    }

    boolean matches(Alert other) {
        return this.productId().equals(other.productId()) && this.condition.equals(other.condition);
    }

    public static final class GsonAdapter implements JsonSerializer<Alert>, JsonDeserializer<Alert> {
        @Override
        public JsonElement serialize(Alert src, Type type, JsonSerializationContext ctx) {
            var obj = new JsonObject();
            obj.addProperty("id", src.id.toString());
            obj.addProperty("createdAt", src.createdAt);
            obj.add("product", ctx.serialize(src.product, IndexedProduct.class));
            obj.addProperty("kind", src.kind().name());
            obj.add("condition", ctx.serialize(src.condition));
            obj.addProperty("remindedAfter", src.remindedAfter);
            return obj;
        }

        @Override
        public Alert deserialize(JsonElement json, Type type, JsonDeserializationContext ctx) {
            try {
                var obj = json.getAsJsonObject();
                var kind = Kind.valueOf(GsonUtils.required(obj, "kind", "Alert").getAsString());
                var conditionJson = GsonUtils.required(obj, "condition", "Alert");
                AlertCondition condition = switch (kind) {
                    case Price -> ctx.deserialize(conditionJson, AlertCondition.Price.class);
                    case Liquidity -> ctx.deserialize(conditionJson, AlertCondition.Liquidity.class);
                };
                var definition = new AlertDefinition(
                    GsonUtils.required(obj, "createdAt", "Alert").getAsLong(),
                    ctx.deserialize(GsonUtils.required(obj, "product", "Alert"), IndexedProduct.class),
                    condition).validate().get();
                return new Alert(
                    UUID.fromString(GsonUtils.required(obj, "id", "Alert").getAsString()),
                    definition,
                    GsonUtils.optionalLong(obj, "remindedAfter").orElse(-1L));
            } catch (RuntimeException err) {
                log.warn("Skipping invalid alert entry", err);
                return null;
            }
        }
    }
}
