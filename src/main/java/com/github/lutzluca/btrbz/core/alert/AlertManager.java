package com.github.lutzluca.btrbz.core.alert;

import com.github.lutzluca.btrbz.cache.CacheToken;
import com.github.lutzluca.btrbz.core.alert.AlertCondition.Observation;
import com.github.lutzluca.btrbz.core.alert.AlertCondition.LiquiditySide;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.BazaarData.MarketSnapshot;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import io.vavr.control.Try;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

@Slf4j
public class AlertManager {

    private static final long WEEK_DURATION_MS = 7L * 24 * 60 * 60 * 1000;
    private static final long MONTH_DURATION_MS = 30L * 24 * 60 * 60 * 1000;
    private static final int HISTORY_LIMIT = 50;
    private static final long CROSSING_COOLDOWN_MS = 60_000;

    private final BazaarData bazaarData;
    private final Supplier<AlertConfig> config;
    private final Runnable onChanged;
    private final Consumer<ReachedAlert> notifyReached;
    private final LongSupplier clock;
    private final CacheToken changes = CacheToken.named("alerts");

    public AlertManager(
        BazaarData bazaarData,
        Supplier<AlertConfig> config,
        Runnable onChanged,
        Consumer<ReachedAlert> notifyReached
    ) {
        this(bazaarData, config, onChanged, notifyReached, System::currentTimeMillis);
    }

    AlertManager(
        BazaarData bazaarData,
        Supplier<AlertConfig> config,
        Runnable onChanged,
        Consumer<ReachedAlert> notifyReached,
        LongSupplier clock
    ) {
        this.bazaarData = Objects.requireNonNull(bazaarData, "bazaarData cannot be null");
        this.config = Objects.requireNonNull(config, "config supplier cannot be null");
        this.onChanged = Objects.requireNonNull(onChanged, "change callback cannot be null");
        this.notifyReached = Objects.requireNonNull(notifyReached, "reached notifier cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock");

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
        cleaned |= pruneHistory(cfg.reachedAlerts);
        if (cleaned) {
            this.changed("invalid alerts removed");
        }
    }

    public List<Alert> alerts() {
        return List.copyOf(this.config().alerts);
    }

    public List<ReachedAlert> inbox() {
        return this.config().reachedAlerts.stream().filter(ReachedAlert::pending).toList();
    }

    public List<ReachedAlert> history() {
        return this.config().reachedAlerts.stream().filter(entry -> !entry.pending()).toList();
    }

    public int pendingCount() {
        return (int) this.config().reachedAlerts.stream().filter(ReachedAlert::pending).count();
    }

    public boolean awaitingDismissal(Alert alert) {
        return this.config().reachedAlerts.stream()
            .anyMatch(entry -> entry.pending() && entry.alert().id.equals(alert.id));
    }

    public boolean dismiss(UUID id) {
        var reached = this.config().reachedAlerts;
        for (int index = 0; index < reached.size(); index++) {
            var entry = reached.get(index);
            if (entry.id().equals(id) && entry.pending()) {
                reached.set(index, this.dismissed(entry, this.clock.getAsLong()));
                pruneHistory(reached);
                this.changed("alert dismissed");
                return true;
            }
        }
        return false;
    }

    public void dismissAll() {
        long now = this.clock.getAsLong();
        var reached = this.config().reachedAlerts;
        reached.replaceAll(entry -> entry.pending() ? this.dismissed(entry, now) : entry);
        pruneHistory(reached);
        this.changed("inbox dismissed");
    }

    private ReachedAlert dismissed(ReachedAlert entry, long now) {
        for (var watch : this.config().alerts) {
            if (watch.id.equals(entry.alert().id)) {
                watch.armed = this.currentObservation(watch)
                    .map(observed -> !watch.condition.isReached(observed)).orElse(false);
            }
        }
        return entry.dismissed(now);
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
            if (this.awaitingDismissal(curr)) {
                continue;
            }
            var observed = this.observe(curr, snapshot);
            if (observed.isPresent() && !curr.condition.isReached(observed.get()) && !curr.armed) {
                curr.armed = true;
                changed = true;
            }
            if (observed.isPresent() && curr.condition.isReached(observed.get())
                && curr.armed
                && (curr.lastTriggeredAt < 0
                    || this.clock.getAsLong() - curr.lastTriggeredAt >= CROSSING_COOLDOWN_MS)) {
                if (curr.lifecycle != AlertLifecycle.Repeat) {
                    it.remove();
                }
                var entry = this.capture(curr, observed.get());
                cfg.reachedAlerts.addFirst(entry);
                changed = true;
                newlyReached.add(entry);
                continue;
            }

            if (!(curr.condition instanceof AlertCondition.Price price)) {
                continue;
            }

            var now = this.clock.getAsLong();
            var duration = now - (curr.lastTriggeredAt >= 0 ? curr.lastTriggeredAt : curr.createdAt);

            if (duration > MONTH_DURATION_MS && curr.remindedAfter < MONTH_DURATION_MS) {
                if (cfg.alsoSendChatMessage) {
                    AlertNotifications.notifyOutdated(curr, price, "over a month", this.bazaarData);
                }
                curr.remindedAfter = duration;
                changed = true;
            }

            if (duration > WEEK_DURATION_MS && curr.remindedAfter < WEEK_DURATION_MS) {
                if (cfg.alsoSendChatMessage) {
                    AlertNotifications.notifyOutdated(curr, price, "over a week", this.bazaarData);
                }
                curr.remindedAfter = duration;
                changed = true;
                continue;
            }
        }

        if (changed) {
            this.changed("alerts updated from market data");
            newlyReached.forEach(this::dispatchReached);
        }
    }

    public Try<Alert> saveAlert(@Nullable UUID id, AlertDefinition definition, AlertLifecycle lifecycle) {
        if (definition == null) {
            return Try.failure(new IllegalArgumentException("Alert definition is required"));
        }

        return definition.validate().flatMap(valid -> this.saveCondition(id, new Alert(
            id == null ? UUID.randomUUID() : id, valid, Objects.requireNonNull(lifecycle, "lifecycle"), -1L)));
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
        }

        for (var alert : current) {
            if ((id == null || !alert.id.equals(id)) && alert.matches(saved)) {
                return Try.failure(new IllegalArgumentException("An identical alert is already active"));
            }
        }

        if (editIndex >= 0 && current.get(editIndex).matches(saved)) {
            saved.armed = current.get(editIndex).armed;
            saved.lastTriggeredAt = current.get(editIndex).lastTriggeredAt;
        }

        if (this.awaitingDismissal(saved)) {
            saved.armed = false;
        }
        var reached = this.immediateObservation(saved);
        if (editIndex >= 0) {
            current.remove(editIndex);
        }
        if (reached.isPresent()) {
            config.reachedAlerts.addFirst(reached.get());
        }
        if (reached.isEmpty() || saved.lifecycle == AlertLifecycle.Repeat) {
            current.add(editIndex < 0 ? current.size() : editIndex, saved);
        }

        this.changed(id == null ? "alert created" : "alert edited");
        reached.ifPresent(this::dispatchReached);
        return Try.success(saved);
    }

    /** Reactivates a captured condition without resolving its original price expression again. */
    public Try<Alert> watchAgain(UUID id) {
        var original = this.history().stream()
            .filter(entry -> entry.id().equals(id))
            .findFirst();
        if (original.isEmpty()) {
            return Try.failure(new IllegalArgumentException("Reached alert " + id + " no longer exists"));
        }
        return this.saveCondition(null, original.get().alert().reactivated(this.clock.getAsLong()));
    }

    private void changed(String reason) {
        this.changes.invalidate(reason);
        this.onChanged.run();
    }

    private void dispatchReached(ReachedAlert reached) {
        Try.run(() -> this.notifyReached.accept(reached))
            .onFailure(err -> log.warn("Failed to deliver reached alert {}", reached.alert().id, err));
    }

    public Optional<Observation> currentObservation(Alert alert) {
        return this.observe(alert, this.bazaarData.currentSnapshot());
    }

    private Optional<ReachedAlert> immediateObservation(Alert alert) {
        if (!this.enabled() || !alert.armed
            || (alert.lastTriggeredAt >= 0 && this.clock.getAsLong() - alert.lastTriggeredAt < CROSSING_COOLDOWN_MS)) {
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
        alert.armed = false;
        alert.lastTriggeredAt = this.clock.getAsLong();
        alert.remindedAfter = -1;
        return new ReachedAlert(alert, alert.lastTriggeredAt, observation);
    }

    private static boolean pruneHistory(List<ReachedAlert> history) {
        int retained = 0;
        boolean changed = false;
        var iterator = history.iterator();
        while (iterator.hasNext()) {
            if (!iterator.next().pending() && ++retained > HISTORY_LIMIT) {
                iterator.remove();
                changed = true;
            }
        }
        return changed;
    }

    public boolean removeAlert(UUID id) {
        if (id == null) {
            return false;
        }

        if (!this.config().alerts.removeIf(alert -> alert.id.equals(id))) {
            return false;
        }
        this.changed("alert removed");
        return true;
    }

    public boolean removeHistoryEntry(UUID id) {
        if (!this.config().reachedAlerts.removeIf(entry -> !entry.pending() && entry.id().equals(id))) {
            return false;
        }
        this.changed("alert history entry removed");
        return true;
    }

    private AlertConfig config() {
        return Objects.requireNonNull(this.config.get(), "alert config cannot be null");
    }
}
