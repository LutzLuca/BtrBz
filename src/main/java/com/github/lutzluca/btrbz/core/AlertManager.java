package com.github.lutzluca.btrbz.core;

import com.github.lutzluca.btrbz.cache.CacheToken;
import com.github.lutzluca.btrbz.core.alert.AlertDefinition;
import com.github.lutzluca.btrbz.core.alert.AlertType;
import com.github.lutzluca.btrbz.core.config.ConfigImages;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.core.config.ConfigStore;
import com.github.lutzluca.btrbz.core.config.OptionGrouping;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.BazaarData.MarketSnapshot;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.utils.GsonUtils;
import com.github.lutzluca.btrbz.utils.Notifier;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionGroup;
import io.vavr.control.Try;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

@Slf4j
public class AlertManager {

    private static final long WEEK_DURATION_MS = 7L * 24 * 60 * 60 * 1000;
    private static final long MONTH_DURATION_MS = 30L * 24 * 60 * 60 * 1000;

    private final BazaarData bazaarData;
    private final Supplier<AlertConfig> config;
    private final Runnable save;
    private final CacheToken changes = CacheToken.named("alerts");

    public AlertManager(BazaarData bazaarData) {
        this(bazaarData, () -> ConfigStore.get().config().alert, ConfigStore.get()::save);
    }

    public AlertManager(BazaarData bazaarData, Supplier<AlertConfig> config, Runnable save) {
        this.bazaarData = Objects.requireNonNull(bazaarData, "bazaarData cannot be null");
        this.config = Objects.requireNonNull(config, "config supplier cannot be null");
        this.save = Objects.requireNonNull(save, "save callback cannot be null");

        if (this.config().alerts.removeIf(Objects::isNull)) {
            this.save.run();
            this.changes.invalidate("invalid alerts removed");
        }
    }

    public List<Alert> alerts() {
        return List.copyOf(this.config().alerts);
    }

    public CacheToken changes() {
        return this.changes;
    }

    public boolean enabled() {
        return this.config().enabled;
    }

    public void onBazaarUpdate(MarketSnapshot snapshot) {
        if (!snapshot.available()) {
            return;
        }
        var cfg = this.config();
        if (!cfg.enabled) {
            return;
        }

        boolean changed = false;
        var it = cfg.alerts.iterator();

        while (it.hasNext()) {
            var curr = it.next();
            var priceResult = curr.getAssociatedPrice(snapshot);
            if (priceResult.isFailure()) {
                it.remove();
                changed = true;
                Notifier.notifyInvalidProduct(curr, this.bazaarData);
                continue;
            }

            var price = priceResult.get();
            var reached = price.map(marketPrice -> curr.type.isReached(marketPrice, curr.price)).orElse(false);

            if (reached) {
                it.remove();
                changed = true;
                Notifier.notifyPriceReached(curr, price, this.bazaarData);
                continue;
            }

            var now = System.currentTimeMillis();
            var duration = now - curr.createdAt;

            if (duration > MONTH_DURATION_MS && curr.remindedAfter < MONTH_DURATION_MS) {
                Notifier.notifyOutdatedAlert(curr, "over a month", this.bazaarData);
                curr.remindedAfter = duration;
                changed = true;
            }

            if (duration > WEEK_DURATION_MS && curr.remindedAfter < WEEK_DURATION_MS) {
                Notifier.notifyOutdatedAlert(curr, "over a week", this.bazaarData);
                curr.remindedAfter = duration;
                changed = true;
                continue;
            }
        }

        if (changed) {
            this.save.run();
            this.changes.invalidate("alerts updated from market data");
        }
    }

    public Try<Alert> saveAlert(@Nullable UUID id, AlertDefinition definition) {
        if (definition == null) {
            return Try.failure(new IllegalArgumentException("Alert definition is required"));
        }

        return definition.validate().flatMap(valid -> Try.of(() -> {
            var config = this.config();
            var current = config.alerts;
            int editIndex = -1;
            if (id != null) {
                for (int index = 0; index < current.size(); index++) {
                    if (current.get(index).id.equals(id)) {
                        editIndex = index;
                        break;
                    }
                }
                if (editIndex < 0) {
                    throw new IllegalArgumentException("Alert " + id + " no longer exists");
                }
            }

            for (var alert : current) {
                if ((id == null || !alert.id.equals(id)) && alert.matches(valid)) {
                    throw new IllegalArgumentException("An identical alert is already active");
                }
            }

            var saved = new Alert(id == null ? UUID.randomUUID() : id, valid, -1L);
            var updated = new ArrayList<>(current);
            if (editIndex < 0) {
                updated.add(saved);
            } else {
                updated.set(editIndex, saved);
            }

            config.alerts = updated;
            try {
                this.save.run();
            } catch (RuntimeException err) {
                config.alerts = current;
                throw err;
            }
            this.changes.invalidate(id == null ? "alert created" : "alert edited");
            return saved;
        }));
    }

    public boolean removeAlert(UUID id) {
        if (id == null) {
            return false;
        }

        var config = this.config();
        var current = config.alerts;
        var updated = new ArrayList<>(current);
        if (!updated.removeIf(alert -> alert.id.equals(id))) {
            return false;
        }

        config.alerts = updated;
        try {
            this.save.run();
        } catch (RuntimeException err) {
            config.alerts = current;
            throw err;
        }
        this.changes.invalidate("alert removed");
        return true;
    }

    private AlertConfig config() {
        return Objects.requireNonNull(this.config.get(), "alert config cannot be null");
    }

    public static class Alert {

        public final UUID id;
        public final long createdAt;
        public final IndexedProduct product;
        public final AlertType type;
        public final double price;

        long remindedAfter = -1;

        private Alert(
            UUID id,
            long createdAt,
            IndexedProduct product,
            AlertType type,
            double price,
            long remindedAfter
        ) {
            this.id = id;
            this.createdAt = createdAt;
            this.product = product;
            this.type = type;
            this.price = price;
            this.remindedAfter = remindedAfter;
        }

        private Alert(UUID id, AlertDefinition definition, long remindedAfter) {
            this(
                id,
                definition.timestamp(),
                definition.product(),
                definition.type(),
                definition.price(),
                remindedAfter);
        }

        public String productName() {
            return this.product.strippedName();
        }

        public String productId() {
            return this.product.productId();
        }

        public Try<Optional<Double>> getAssociatedPrice(MarketSnapshot snapshot) {
            var identity = ProductIdentity.fromIndex(this.product);
            if (!snapshot.contains(identity)) {
                return Try.failure(
                    new Exception("The product \"" + this.productName() + "\" could not be found in the bazaar data"));
            }

            return Try.success(this.type.source().price(snapshot.getMarketPrices(identity)));
        }

        public boolean matches(AlertDefinition definition) {
            // @formatter:off
            return this.productId().equals(definition.product().productId())
                && this.type.equals(definition.type())
                && Double.compare(this.price, definition.price()) == 0;
            // @formatter:on
        }

        public static final class GsonAdapter implements JsonSerializer<Alert>, JsonDeserializer<Alert> {

            @Override
            public JsonElement serialize(
                Alert src,
                Type typeOfSrc,
                JsonSerializationContext ctx
            ) {
                var obj = new JsonObject();
                obj.addProperty("id", src.id.toString());
                obj.addProperty("createdAt", src.createdAt);
                obj.add("product", ctx.serialize(src.product, IndexedProduct.class));
                obj.add("type", ctx.serialize(src.type));
                obj.addProperty("price", src.price);
                obj.addProperty("remindedAfter", src.remindedAfter);
                return obj;
            }

            @Override
            public Alert deserialize(
                JsonElement json,
                Type typeOfT,
                JsonDeserializationContext ctx
            ) throws JsonParseException {
                if (json == null || !json.isJsonObject()) {
                    log.warn("Skipping malformed alert entry");
                    return null;
                }
                var obj = json.getAsJsonObject();
                var product = product(obj, ctx).orElse(null);
                if (product == null) {
                    return null;
                }

                try {
                    return new Alert(
                        UUID.fromString(GsonUtils.required(obj, "id", "Alert").getAsString()),
                        GsonUtils.required(obj, "createdAt", "Alert").getAsLong(),
                        product,
                        Objects
                            .requireNonNull(ctx.deserialize(GsonUtils.required(obj, "type", "Alert"), AlertType.class)),
                        GsonUtils.required(obj, "price", "Alert").getAsDouble(),
                        GsonUtils.optionalLong(obj, "remindedAfter").orElse(-1L));
                } catch (RuntimeException err) {
                    log.warn("Skipping invalid alert entry", err);
                    return null;
                }
            }

            private static Optional<IndexedProduct> product(JsonObject obj, JsonDeserializationContext ctx) {
                try {
                    return Optional.of(ctx.deserialize(
                        GsonUtils.required(obj, "product", "Alert"),
                        IndexedProduct.class));
                } catch (RuntimeException err) {
                    log.warn("Skipping alert with invalid product", err);
                    return Optional.empty();
                }
            }
        }
    }

    public static class AlertConfig {

        public boolean enabled = true;
        public boolean soundOnAlert = true;
        public List<Alert> alerts = new ArrayList<>();

        public Option.Builder<Boolean> createEnabledOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Enable Price Alerts"))
                .description(ConfigScreen.createDescription(ConfigScreen.paragraphs(
                    ConfigScreen.text(
                        "Check configured price targets and notify you when a target is reached."),
                    ConfigScreen.note(
                        "Alerts that become valid while this is off may fire immediately when it is enabled again."))))
                .binding(true, () -> this.enabled, val -> this.enabled = val)
                .controller(ConfigScreen::createBooleanController);
        }

        public Option.Builder<Boolean> createSoundOnAlertOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Play Alert Sound"))
                .description(ConfigScreen.createDescription(
                    "Play a sound together with the chat notification when a price target is reached."))
                .binding(true, () -> this.soundOnAlert, val -> this.soundOnAlert = val)
                .controller(ConfigScreen::createBooleanController);
        }

        public OptionGroup createGroup() {
            var rootGroup = new OptionGrouping(this.createEnabledOption()).addOptions(this.createSoundOnAlertOption());

            return OptionGroup
                .createBuilder()
                .name(Component.literal("Price Alerts"))
                .description(ConfigScreen.createDescription(ConfigScreen.paragraphs(
                    ConfigScreen.text("Notify you when a Bazaar price reaches a configured target."),
                    ConfigScreen.note("Open /btrbz alert to create, edit, or remove alerts.")),
                    ConfigImages.PriceAlert))
                .options(rootGroup.build())
                .collapsed(true)
                .build();
        }
    }
}
