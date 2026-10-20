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
    public final AlertLifecycle lifecycle;
    boolean armed = true;
    long lastTriggeredAt = -1;

    Alert(UUID id, AlertDefinition definition, AlertLifecycle lifecycle) {
        this.id = id;
        this.createdAt = definition.timestamp();
        this.product = definition.product();
        this.condition = definition.condition();
        this.lifecycle = lifecycle;
    }

    public Kind kind() {
        return this.condition.kind();
    }

    Alert reactivated(long now) {
        return new Alert(UUID.randomUUID(), new AlertDefinition(now, this.product, this.condition), this.lifecycle);
    }

    Alert snapshot() {
        var copy = new Alert(this.id, new AlertDefinition(this.createdAt, this.product, this.condition),
            this.lifecycle);
        copy.armed = this.armed;
        copy.lastTriggeredAt = this.lastTriggeredAt;
        return copy;
    }

    public boolean waitingForReset() {
        return !this.armed;
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
            obj.addProperty("lifecycle", src.lifecycle.name());
            obj.addProperty("armed", src.armed);
            obj.addProperty("lastTriggeredAt", src.lastTriggeredAt);
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
                var alert = new Alert(
                    UUID.fromString(GsonUtils.required(obj, "id", "Alert").getAsString()),
                    definition,
                    AlertLifecycle.valueOf(GsonUtils.required(obj, "lifecycle", "Alert").getAsString()));
                alert.armed = GsonUtils.required(obj, "armed", "Alert").getAsBoolean();
                alert.lastTriggeredAt = GsonUtils.required(obj, "lastTriggeredAt", "Alert").getAsLong();
                if (alert.lastTriggeredAt < -1) {
                    throw new IllegalArgumentException("Invalid last trigger time");
                }
                return alert;
            } catch (RuntimeException err) {
                log.warn("Skipping invalid alert entry", err);
                return null;
            }
        }
    }
}
