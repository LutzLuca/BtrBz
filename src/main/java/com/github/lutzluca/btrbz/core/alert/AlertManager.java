package com.github.lutzluca.btrbz.core.alert;

import com.github.lutzluca.btrbz.cache.CacheToken;
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
    private final Runnable save;
    private final Consumer<ReachedAlert> notifyReached;
    private final LongSupplier clock;
    private final CacheToken changes = CacheToken.named("alerts");
    private final List<ReachedAlert> pendingNotices = new ArrayList<>();
    private boolean pendingSave;
    @lombok.Getter
    @lombok.experimental.Accessors(fluent = true)
    private long lastCheckedAt = -1;

    public AlertManager(BazaarData bazaarData, Consumer<ReachedAlert> notifyReached) {
        this(bazaarData, () -> ConfigStore.get().config().alert, ConfigStore.get()::save, notifyReached);
    }

    public AlertManager(
        BazaarData bazaarData,
        Supplier<AlertConfig> config,
        Runnable save,
        Consumer<ReachedAlert> notifyReached
    ) {
        this(bazaarData, config, save, notifyReached, System::currentTimeMillis);
    }

    AlertManager(
        BazaarData bazaarData,
        Supplier<AlertConfig> config,
        Runnable save,
        Consumer<ReachedAlert> notifyReached,
        LongSupplier clock
    ) {
        this.bazaarData = Objects.requireNonNull(bazaarData, "bazaarData cannot be null");
        this.config = Objects.requireNonNull(config, "config supplier cannot be null");
        this.save = Objects.requireNonNull(save, "save callback cannot be null");
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

    public long snoozedUntil() {
        return this.config().snoozedUntil;
    }

    public Try<Void> snooze() {
        var cfg = this.config();
        long previous = cfg.snoozedUntil;
        cfg.snoozedUntil = this.clock.getAsLong() + AlertDelivery.REMINDER_INTERVAL_MS;
        return Try.run(this.save::run).onFailure(_ -> cfg.snoozedUntil = previous)
            .onSuccess(_ -> this.changes.invalidate("inbox snoozed"));
    }

    public Try<Boolean> dismiss(UUID id) {
        var reached = new ArrayList<>(this.config().reachedAlerts);
        for (int index = 0; index < reached.size(); index++) {
            var entry = reached.get(index);
            if (entry.id().equals(id) && entry.pending()) {
                reached.set(index, entry.dismissed(this.clock.getAsLong()));
                pruneHistory(reached);
                return this.replaceAndSave(this.resumeDismissedWatches(reached), reached).map(_ -> {
                    this.changes.invalidate("alert reviewed");
                    return true;
                });
            }
        }
        return Try.success(false);
    }

    public Try<Void> dismissAll() {
        long now = this.clock.getAsLong();
        var reached = new ArrayList<>(this.config().reachedAlerts.stream()
            .map(entry -> entry.pending() ? entry.dismissed(now) : entry).toList());
        pruneHistory(reached);
        return this.replaceAndSave(this.resumeDismissedWatches(reached), reached)
            .onSuccess(_ -> this.changes.invalidate("inbox reviewed"));
    }

    private List<Alert> resumeDismissedWatches(List<ReachedAlert> reached) {
        return new ArrayList<>(this.config().alerts.stream().map(alert -> {
            if (!this.awaitingDismissal(alert) || reached.stream()
                .anyMatch(entry -> entry.pending() && entry.alert().id.equals(alert.id))) {
                return alert;
            }
            var resumed = alert.snapshot();
            resumed.armed = this.currentObservation(resumed)
                .map(observed -> !resumed.condition.isReached(observed)).orElse(false);
            return resumed;
        }).toList());
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
        this.lastCheckedAt = this.clock.getAsLong();

        boolean changed = false;
        var newlyReached = new ArrayList<ReachedAlert>();
        var active = new ArrayList<>(cfg.alerts.stream().map(Alert::snapshot).toList());
        var reached = new ArrayList<>(cfg.reachedAlerts);
        var it = active.iterator();

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
                reached.addFirst(entry);
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
            cfg.alerts = active;
            cfg.reachedAlerts = reached;
            this.pendingNotices.addAll(newlyReached);
            this.pendingSave = true;
            this.changes.invalidate("alerts updated from market data");
        }
        if (this.pendingSave) {
            this.replaceAndSave(cfg.alerts, cfg.reachedAlerts)
                .onFailure(err -> log.warn("Failed to persist updated alerts", err));
        }
    }

    public Try<Alert> saveAlert(@Nullable UUID id, AlertDefinition definition) {
        return this.saveAlert(id, definition, AlertLifecycle.Once);
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
        var updated = new ArrayList<>(current);
        var newReached = new ArrayList<>(config.reachedAlerts);
        if (editIndex >= 0) {
            updated.remove(editIndex);
        }
        if (reached.isPresent()) {
            newReached.addFirst(reached.get());
        }
        if (reached.isEmpty() || saved.lifecycle == AlertLifecycle.Repeat) {
            if (editIndex < 0) {
                updated.add(saved);
            } else {
                updated.add(editIndex, saved);
            }
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
            .filter(entry -> entry.id().equals(id))
            .findFirst();
        if (original.isEmpty()) {
            return Try.failure(new IllegalArgumentException("Reached alert " + id + " no longer exists"));
        }
        var reactivated = original.get().alert().reactivated(this.clock.getAsLong());
        if (cfg.alerts.stream().anyMatch(active -> active.matches(reactivated))) {
            return Try.failure(new IllegalArgumentException("An identical alert is already active"));
        }
        reactivated.armed = this.currentObservation(reactivated)
            .map(observation -> !reactivated.condition.isReached(observation)).orElse(true);
        var newActive = new ArrayList<>(cfg.alerts);
        var newReached = new ArrayList<>(cfg.reachedAlerts);
        newReached.set(newReached.indexOf(original.get()), original.get().dismissed(this.clock.getAsLong()));
        pruneHistory(newReached);
        newActive.add(reactivated);

        return this.replaceAndSave(newActive, newReached).map(_ -> {
            this.changes.invalidate("alert reactivated");
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
        }).onSuccess(_ -> {
            this.pendingSave = false;
            var notices = List.copyOf(this.pendingNotices);
            this.pendingNotices.clear();
            notices.stream().filter(notice -> config.reachedAlerts.stream()
                .anyMatch(event -> event.id().equals(notice.id()) && event.pending()))
                .forEach(this::dispatchReached);
        });
    }

    private void dispatchReached(ReachedAlert reached) {
        Try.run(() -> this.notifyReached.accept(reached))
            .onFailure(err -> log.warn("Failed to deliver reached alert {}", reached.alert().id, err));
    }

    public OptionalLong liquidityProgress(Alert alert) {
        if (alert == null || !(alert.condition instanceof AlertCondition.Liquidity)) {
            return OptionalLong.empty();
        }
        return this.observe(alert, this.bazaarData.currentSnapshot())
            .map(value -> OptionalLong.of(((Observation.Liquidity) value).quantity()))
            .orElseGet(OptionalLong::empty);
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
        if (!updated.removeIf(entry -> entry.id().equals(id))) {
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
