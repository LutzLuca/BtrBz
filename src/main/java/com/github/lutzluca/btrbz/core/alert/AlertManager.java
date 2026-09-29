package com.github.lutzluca.btrbz.core.alert;

import com.github.lutzluca.btrbz.cache.CacheToken;
import com.github.lutzluca.btrbz.core.alert.AlertCondition.Kind;
import com.github.lutzluca.btrbz.core.alert.AlertCondition.Observation;
import com.github.lutzluca.btrbz.core.alert.AlertCondition.LiquiditySide;
import com.github.lutzluca.btrbz.core.config.ConfigStore;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.BazaarData.MarketSnapshot;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import io.vavr.control.Try;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
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
                AlertNotifications.notifyOutdated(curr, price, "over a month", this.bazaarData);
                curr.remindedAfter = duration;
                changed = true;
            }

            if (duration > WEEK_DURATION_MS && curr.remindedAfter < WEEK_DURATION_MS) {
                AlertNotifications.notifyOutdated(curr, price, "over a week", this.bazaarData);
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
                return Try.failure(new IllegalArgumentException("Alert " + id + " no longer exists"));
            }
            if (current.get(editIndex).kind() != saved.kind()) {
                return Try.failure(new IllegalArgumentException("An alert cannot change kind while editing"));
            }
        }

        for (var alert : current) {
            if ((id == null || !alert.id.equals(id)) && alert.matches(saved)) {
                return Try.failure(new IllegalArgumentException("An identical alert is already active"));
            }
        }

        var reached = this.immediateObservation(saved);
        var updated = new ArrayList<>(current);
        var newReached = new ArrayList<>(config.reachedAlerts);
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

        return this.replaceAndSave(updated, newReached).map(_ -> {
            this.changes.invalidate(id == null ? "alert created" : "alert edited");
            reached.ifPresent(this::dispatchReached);
            return saved;
        });
    }

    /** Reactivates a captured condition without resolving its original price expression again. */
    public Try<Alert> watchAgain(UUID id) {
        var cfg = this.config();
        var original = cfg.reachedAlerts.stream()
            .filter(entry -> entry.alert().id.equals(id))
            .findFirst();
        if (original.isEmpty()) {
            return Try.failure(new IllegalArgumentException("Reached alert " + id + " no longer exists"));
        }
        var reactivated = original.get().alert().reactivated(System.currentTimeMillis());
        if (cfg.alerts.stream().anyMatch(active -> active.matches(reactivated))) {
            return Try.failure(new IllegalArgumentException("An identical alert is already active"));
        }
        var reached = this.immediateObservation(reactivated);
        var newActive = new ArrayList<>(cfg.alerts);
        var newReached = new ArrayList<>(cfg.reachedAlerts);
        newReached.remove(original.get());
        if (reached.isPresent()) {
            addReached(newReached, reached.get());
        } else {
            newActive.add(reactivated);
        }

        return this.replaceAndSave(newActive, newReached).map(_ -> {
            this.changes.invalidate("alert reactivated");
            reached.ifPresent(this::dispatchReached);
            return reactivated;
        });
    }

    private Try<Void> replaceAndSave(List<Alert> active, List<ReachedAlert> reached) {
        var config = this.config();
        var previousActive = config.alerts;
        var previousReached = config.reachedAlerts;
        config.alerts = active;
        config.reachedAlerts = reached;
        return Try.run(this.save::run).onFailure(_ -> {
            config.alerts = previousActive;
            config.reachedAlerts = previousReached;
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

        this.replaceAndSave(updated, config.reachedAlerts).get();
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
        this.replaceAndSave(config.alerts, updated).get();
        this.changes.invalidate("reached alert removed");
        return true;
    }

    private AlertConfig config() {
        return Objects.requireNonNull(this.config.get(), "alert config cannot be null");
    }
}
