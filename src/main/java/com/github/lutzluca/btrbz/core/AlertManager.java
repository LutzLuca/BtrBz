package com.github.lutzluca.btrbz.core;

import com.github.lutzluca.btrbz.cache.CacheToken;
import com.github.lutzluca.btrbz.core.alert.AlertDefinition;
import com.github.lutzluca.btrbz.core.alert.AlertCondition.Kind;
import com.github.lutzluca.btrbz.core.alert.AlertCondition;
import com.github.lutzluca.btrbz.core.alert.AlertCondition.Observation;
import com.github.lutzluca.btrbz.core.alert.LiquidityEvaluation;
import com.github.lutzluca.btrbz.core.alert.AlertCondition.LiquiditySide;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.core.config.ConfigStore;
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
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import dev.isxander.yacl3.api.Option;
import io.vavr.control.Try;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

@Slf4j
public class AlertManager {

    private static final long WEEK_DURATION_MS = 7L * 24 * 60 * 60 * 1000;
    private static final long MONTH_DURATION_MS = 30L * 24 * 60 * 60 * 1000;
    private static final int REACHED_LIMIT = 10;

    private final BazaarData bazaarData;
    private final Supplier<AlertConfig> config;
    private final Runnable save;
    private final Consumer<ReachedAlert> notifyReached;
    private final CacheToken changes = CacheToken.named("alerts");

    public AlertManager(BazaarData bazaarData, Consumer<ReachedAlert> notifyReached) {
        this(bazaarData, () -> ConfigStore.get().config().alert, ConfigStore.get()::save, notifyReached);
    }

    public AlertManager(
        BazaarData bazaarData,
        Supplier<AlertConfig> config,
        Runnable save,
        Consumer<ReachedAlert> notifyReached
    ) {
        this.bazaarData = Objects.requireNonNull(bazaarData, "bazaarData cannot be null");
        this.config = Objects.requireNonNull(config, "config supplier cannot be null");
        this.save = Objects.requireNonNull(save, "save callback cannot be null");
        this.notifyReached = Objects.requireNonNull(notifyReached, "reached notifier cannot be null");

        var cfg = this.config();
        boolean cleaned = false;
        if (cfg.alerts == null) {
            cfg.alerts = new ArrayList<>();
            cleaned = true;
        }
        cleaned |= cfg.alerts.removeIf(Objects::isNull);
        if (cfg.reachedAlerts == null) {
            cfg.reachedAlerts = new ArrayList<>();
            cleaned = true;
        }
        cleaned |= cfg.reachedAlerts.removeIf(Objects::isNull);
        for (var kind : Kind.values()) {
            int retained = 0;
            var iterator = cfg.reachedAlerts.iterator();
            while (iterator.hasNext()) {
                if (iterator.next().alert().kind() == kind && ++retained > REACHED_LIMIT) {
                    iterator.remove();
                    cleaned = true;
                }
            }
        }
        if (cleaned) {
            this.changes.invalidate("invalid alerts removed");
            Try.run(this.save::run).onFailure(err -> log.warn("Failed to persist cleaned alert configuration", err));
        }
    }

    public List<Alert> alerts() {
        return List.copyOf(this.config().alerts);
    }

    public List<ReachedAlert> reachedAlerts() {
        return List.copyOf(this.config().reachedAlerts);
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
        var newlyReached = new ArrayList<ReachedAlert>();
        var it = cfg.alerts.iterator();

        while (it.hasNext()) {
            var curr = it.next();
            var observed = this.observe(curr, snapshot);
            if (observed.isPresent() && curr.condition.isReached(observed.get())) {
                it.remove();
                var entry = this.capture(curr, observed.get());
                addReached(cfg.reachedAlerts, entry);
                changed = true;
                newlyReached.add(entry);
                continue;
            }

            if (!(curr.condition instanceof AlertCondition.Price price)) {
                continue;
            }

            var now = System.currentTimeMillis();
            var duration = now - curr.createdAt;

            if (duration > MONTH_DURATION_MS && curr.remindedAfter < MONTH_DURATION_MS) {
                Notifier.notifyOutdatedAlert(curr, price, "over a month", this.bazaarData);
                curr.remindedAfter = duration;
                changed = true;
            }

            if (duration > WEEK_DURATION_MS && curr.remindedAfter < WEEK_DURATION_MS) {
                Notifier.notifyOutdatedAlert(curr, price, "over a week", this.bazaarData);
                curr.remindedAfter = duration;
                changed = true;
                continue;
            }
        }

        if (changed) {
            this.changes.invalidate("alerts updated from market data");
            Try.run(this.save::run).onFailure(err -> log.warn("Failed to persist updated alerts", err));
        }
        newlyReached.forEach(this::dispatchReached);
    }

    public Try<Alert> saveAlert(@Nullable UUID id, AlertDefinition definition) {
        if (definition == null) {
            return Try.failure(new IllegalArgumentException("Alert definition is required"));
        }

        return definition.validate().flatMap(valid -> this.saveCondition(id, new Alert(
            id == null ? UUID.randomUUID() : id, valid, -1L)));
    }

    private Try<Alert> saveCondition(@Nullable UUID id, Alert saved) {
        return Try.of(() -> {
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
                if (current.get(editIndex).kind() != saved.kind()) {
                    throw new IllegalArgumentException("An alert cannot change kind while editing");
                }
            }

            for (var alert : current) {
                if ((id == null || !alert.id.equals(id)) && alert.matches(saved)) {
                    throw new IllegalArgumentException("An identical alert is already active");
                }
            }

            var reached = this.immediateObservation(saved);
            var updated = new ArrayList<>(current);
            var oldReached = config.reachedAlerts;
            var newReached = new ArrayList<>(oldReached);
            if (editIndex >= 0) {
                updated.remove(editIndex);
            }
            if (reached.isPresent()) {
                addReached(newReached, reached.get());
            } else if (editIndex < 0) {
                updated.add(saved);
            } else {
                updated.add(editIndex, saved);
            }

            config.alerts = updated;
            config.reachedAlerts = newReached;
            try {
                this.save.run();
            } catch (RuntimeException err) {
                config.alerts = current;
                config.reachedAlerts = oldReached;
                throw err;
            }
            this.changes.invalidate(id == null ? "alert created" : "alert edited");
            reached.ifPresent(this::dispatchReached);
            return saved;
        });
    }

    /** Reactivates a captured condition without resolving its original price expression again. */
    public Try<Alert> watchAgain(UUID id) {
        return Try.of(() -> {
            var cfg = this.config();
            var original = cfg.reachedAlerts.stream()
                .filter(entry -> entry.alert().id.equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Reached alert " + id + " no longer exists"));
            var reactivated = original.alert().reactivated(System.currentTimeMillis());
            if (cfg.alerts.stream().anyMatch(active -> active.matches(reactivated))) {
                throw new IllegalArgumentException("An identical alert is already active");
            }
            var reached = this.immediateObservation(reactivated);
            var oldActive = cfg.alerts;
            var oldReached = cfg.reachedAlerts;
            var newActive = new ArrayList<>(oldActive);
            var newReached = new ArrayList<>(oldReached);
            newReached.remove(original);
            if (reached.isPresent()) {
                addReached(newReached, reached.get());
            } else {
                newActive.add(reactivated);
            }
            cfg.alerts = newActive;
            cfg.reachedAlerts = newReached;
            try {
                this.save.run();
            } catch (RuntimeException err) {
                cfg.alerts = oldActive;
                cfg.reachedAlerts = oldReached;
                throw err;
            }
            this.changes.invalidate("alert reactivated");
            reached.ifPresent(this::dispatchReached);
            return reactivated;
        });
    }

    private void dispatchReached(ReachedAlert reached) {
        Try.run(() -> this.notifyReached.accept(reached))
            .onFailure(err -> log.warn("Failed to deliver reached alert {}", reached.alert().id, err));
    }

    public OptionalLong liquidityProgress(Alert alert) {
        if (alert == null || alert.kind() != Kind.Liquidity) {
            return OptionalLong.empty();
        }
        return this.observe(alert, this.bazaarData.currentSnapshot())
            .map(value -> OptionalLong.of(((Observation.Liquidity) value).quantity()))
            .orElseGet(OptionalLong::empty);
    }

    private Optional<ReachedAlert> immediateObservation(Alert alert) {
        if (!this.enabled()) {
            return Optional.empty();
        }
        return this.observe(alert, this.bazaarData.currentSnapshot())
            .filter(alert.condition::isReached)
            .map(observation -> this.capture(alert, observation));
    }

    private Optional<Observation> observe(Alert alert, MarketSnapshot snapshot) {
        if (!snapshot.available() || !snapshot.contains(ProductIdentity.fromIndex(alert.product))) {
            return Optional.empty();
        }
        var identity = ProductIdentity.fromIndex(alert.product);
        return switch (alert.condition) {
            case AlertCondition.Price price -> price.type().source().price(snapshot.getMarketPrices(identity))
                .map(Observation.Price::new);
            case AlertCondition.Liquidity liquidity -> {
                var orders = snapshot.getOrderLists(identity);
                var summaries = liquidity.side() == LiquiditySide.BuyOrders ? orders.buyOrders() : orders.sellOffers();
                var levels = summaries.stream()
                    .map(
                        summary -> new LiquidityEvaluation.Level(summary.getPricePerUnit(), (long) summary.getAmount()))
                    .toList();
                yield Optional.of(new Observation.Liquidity(LiquidityEvaluation.qualifyingQuantity(
                    liquidity.side(), liquidity.priceBound(), levels)));
            }
        };
    }

    private ReachedAlert capture(Alert alert, Observation observation) {
        return new ReachedAlert(alert, System.currentTimeMillis(), observation);
    }

    private static void addReached(List<ReachedAlert> history, ReachedAlert reached) {
        history.addFirst(reached);
        int retained = 0;
        var iterator = history.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().alert().kind() == reached.alert().kind() && ++retained > REACHED_LIMIT) {
                iterator.remove();
            }
        }
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

    public boolean removeReachedAlert(UUID id) {
        var config = this.config();
        var current = config.reachedAlerts;
        var updated = new ArrayList<>(current);
        if (!updated.removeIf(entry -> entry.alert().id.equals(id))) {
            return false;
        }
        config.reachedAlerts = updated;
        try {
            this.save.run();
        } catch (RuntimeException err) {
            config.reachedAlerts = current;
            throw err;
        }
        this.changes.invalidate("reached alert removed");
        return true;
    }

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

    private AlertConfig config() {
        return Objects.requireNonNull(this.config.get(), "alert config cannot be null");
    }

    public static class Alert {
        public final UUID id;
        public final long createdAt;
        public final IndexedProduct product;
        public final AlertCondition condition;
        long remindedAfter;

        private Alert(UUID id, AlertDefinition definition, long remindedAfter) {
            this.id = id;
            this.createdAt = definition.timestamp();
            this.product = definition.product();
            this.condition = definition.condition();
            this.remindedAfter = remindedAfter;
        }

        public Kind kind() {
            return this.condition.kind();
        }

        private Alert reactivated(long now) {
            return new Alert(this.id, new AlertDefinition(now, this.product, this.condition), -1);
        }

        public String productName() {
            return this.product.strippedName();
        }

        public String productId() {
            return this.product.productId();
        }

        private boolean matches(Alert other) {
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

    public static class AlertConfig {

        public boolean enabled = true;
        public boolean toastOnAlert = true;
        public List<Alert> alerts = new ArrayList<>();
        public List<ReachedAlert> reachedAlerts = new ArrayList<>();

        public Option.Builder<Boolean> createEnabledOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Enable Alerts"))
                .description(ConfigScreen.createDescription(ConfigScreen.paragraphs(
                    ConfigScreen.text(
                        "Check configured price and liquidity conditions and notify you when one is reached."),
                    ConfigScreen.note(
                        "Alerts that become valid while this is off may fire immediately when it is enabled again."))))
                .binding(true, () -> this.enabled, val -> this.enabled = val)
                .controller(ConfigScreen::createBooleanController);
        }

        public Option.Builder<Boolean> createToastOnAlertOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Show Alert Toasts"))
                .description(ConfigScreen.createDescription(
                    "Show a passive toast when an alert is reached. Reached alerts remain in the Reached tab."))
                .binding(true, () -> this.toastOnAlert, val -> this.toastOnAlert = val)
                .controller(ConfigScreen::createBooleanController);
        }

    }
}
