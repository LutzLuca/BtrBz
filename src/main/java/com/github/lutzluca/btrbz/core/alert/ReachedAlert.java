package com.github.lutzluca.btrbz.core.alert;

import com.github.lutzluca.btrbz.core.alert.AlertCondition.Observation;
import java.util.Objects;
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
public record ReachedAlert(Alert alert, long reachedAt, Observation observation) {
    public ReachedAlert {
        Objects.requireNonNull(alert, "alert");
        Objects.requireNonNull(observation, "observation");
        if (reachedAt < 0 || alert.kind() != observation.kind()) {
            throw new IllegalArgumentException("Invalid reached alert observation");
        }
    }

    public static final class GsonAdapter implements JsonSerializer<ReachedAlert>, JsonDeserializer<ReachedAlert> {
        @Override
        public JsonElement serialize(ReachedAlert src, Type type, JsonSerializationContext ctx) {
            var obj = new JsonObject();
            obj.add("alert", ctx.serialize(src.alert(), Alert.class));
            obj.addProperty("reachedAt", src.reachedAt());
            switch (src.observation()) {
                case Observation.Price price -> obj.addProperty("observed", price.value());
                case Observation.Liquidity liquidity -> obj.addProperty("observed", liquidity.quantity());
            }
            return obj;
        }

        @Override
        public ReachedAlert deserialize(JsonElement json, Type type, JsonDeserializationContext ctx) {
            try {
                var obj = json.getAsJsonObject();
                Alert alert = ctx.deserialize(GsonUtils.required(obj, "alert", "Reached alert"), Alert.class);
                var observed = GsonUtils.required(obj, "observed", "Reached alert");
                Observation observation = switch (alert.kind()) {
                    case Price -> new Observation.Price(observed.getAsDouble());
                    case Liquidity -> new Observation.Liquidity(observed.getAsLong());
                };
                return new ReachedAlert(alert,
                    GsonUtils.required(obj, "reachedAt", "Reached alert").getAsLong(), observation);
            } catch (RuntimeException err) {
                log.warn("Skipping invalid reached alert entry", err);
                return null;
            }
        }
    }
}
