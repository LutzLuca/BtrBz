package com.github.lutzluca.btrbz.core.alert;

import io.vavr.control.Try;
import com.github.lutzluca.btrbz.cache.CacheToken;
import java.util.function.Supplier;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;

/** One reminder schedule covers every pending event. Delivery never handles an event. */
@Slf4j
public final class AlertDelivery {
    static final long REMINDER_INTERVAL_MS = 10 * 60_000L;
    private static final long RETURN_DELAY_MS = 3_000;
    private static final long RETRY_DELAY_MS = 5_000;

    private final AlertManager manager;
    private final Supplier<AlertConfig> config;
    private final Runnable onChanged;
    private final CacheToken changes = CacheToken.named("alert-reminders");
    private final Consumer<ReachedAlert> notifyReached;
    private final IntPredicate notifySummary;
    private final LongSupplier clock;
    private boolean ready;
    private boolean returnPending;
    private boolean remindersEnabled;
    private long nextSummaryAt = Long.MAX_VALUE;

    public AlertDelivery(
        AlertManager manager,
        Supplier<AlertConfig> config,
        Runnable onChanged,
        Consumer<ReachedAlert> notifyReached,
        IntPredicate notifySummary
    ) {
        this(manager, config, onChanged, notifyReached, notifySummary, System::currentTimeMillis);
    }

    AlertDelivery(
        AlertManager manager,
        Supplier<AlertConfig> config,
        Runnable onChanged,
        Consumer<ReachedAlert> notifyReached,
        IntPredicate notifySummary,
        LongSupplier clock
    ) {
        this.manager = manager;
        this.config = config;
        this.onChanged = onChanged;
        this.notifyReached = notifyReached;
        this.notifySummary = notifySummary;
        this.clock = clock;
    }

    public CacheToken changes() {
        return this.changes;
    }

    public boolean remindersEnabled() {
        return this.config.get().remindPending;
    }

    public long snoozedUntil() {
        return this.config.get().snoozedUntil;
    }

    public boolean snooze() {
        if (!this.remindersEnabled()) {
            return false;
        }
        this.nextSummaryAt = this.clock.getAsLong() + REMINDER_INTERVAL_MS;
        this.config.get().snoozedUntil = this.nextSummaryAt;
        this.changes.invalidate("alert reminders snoozed");
        this.onChanged.run();
        return true;
    }

    public void disconnect() {
        this.ready = false;
        this.returnPending = false;
        this.nextSummaryAt = Long.MAX_VALUE;
    }

    public void reached(ReachedAlert reached) {
        long now = this.clock.getAsLong();
        boolean reminders = this.remindersEnabled();
        if (!this.ready && !reminders) {
            this.notifyReached.accept(reached);
            return;
        }
        if (!this.ready || (this.returnPending && reminders && now >= this.snoozedUntil())) {
            return;
        }
        if (reminders) {
            this.nextSummaryAt = Math.min(this.nextSummaryAt, now + REMINDER_INTERVAL_MS);
        }
        this.notifyReached.accept(reached);
    }

    public void tick(boolean available, boolean viewingInbox) {
        if (!available || !this.manager.enabled()) {
            this.disconnect();
            return;
        }
        long now = this.clock.getAsLong();
        if (!this.ready) {
            this.ready = true;
            this.remindersEnabled = this.remindersEnabled();
            this.returnPending = this.remindersEnabled;
            this.nextSummaryAt = Math.max(now + RETURN_DELAY_MS, this.snoozedUntil());
        }
        if (this.remindersEnabled != this.remindersEnabled()) {
            this.remindersEnabled = this.remindersEnabled();
            this.returnPending = false;
            this.nextSummaryAt = Math.max(now + REMINDER_INTERVAL_MS, this.snoozedUntil());
        }
        if (!this.remindersEnabled) {
            this.returnPending = false;
            this.nextSummaryAt = Long.MAX_VALUE;
            return;
        }
        if (now < Math.max(this.nextSummaryAt, this.snoozedUntil())) {
            return;
        }
        int count = this.manager.pendingCount();
        if (viewingInbox || count == 0) {
            this.returnPending = false;
            this.nextSummaryAt = count == 0 ? Long.MAX_VALUE : now + REMINDER_INTERVAL_MS;
            return;
        }
        boolean delivered = Try.of(() -> this.notifySummary.test(count))
            .onFailure(error -> log.warn("Failed to deliver alert inbox reminder", error)).getOrElse(false);
        this.nextSummaryAt = now + (delivered ? REMINDER_INTERVAL_MS : RETRY_DELAY_MS);
        if (delivered) {
            this.returnPending = false;
        }
    }
}
