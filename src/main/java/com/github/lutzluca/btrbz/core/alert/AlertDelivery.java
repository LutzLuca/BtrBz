package com.github.lutzluca.btrbz.core.alert;

import io.vavr.control.Try;
import java.util.function.BooleanSupplier;
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
    private final BooleanSupplier reminders;
    private final Consumer<ReachedAlert> notifyReached;
    private final IntPredicate notifySummary;
    private final LongSupplier clock;
    private boolean ready;
    private boolean returnPending;
    private boolean remindersEnabled;
    private long nextSummaryAt = Long.MAX_VALUE;

    public AlertDelivery(
        AlertManager manager,
        BooleanSupplier reminders,
        Consumer<ReachedAlert> notifyReached,
        IntPredicate notifySummary
    ) {
        this(manager, reminders, notifyReached, notifySummary, System::currentTimeMillis);
    }

    AlertDelivery(
        AlertManager manager,
        BooleanSupplier reminders,
        Consumer<ReachedAlert> notifyReached,
        IntPredicate notifySummary,
        LongSupplier clock
    ) {
        this.manager = manager;
        this.reminders = reminders;
        this.notifyReached = notifyReached;
        this.notifySummary = notifySummary;
        this.clock = clock;
    }

    public void disconnect() {
        this.ready = false;
        this.returnPending = false;
        this.nextSummaryAt = Long.MAX_VALUE;
    }

    public void reached(ReachedAlert reached) {
        long now = this.clock.getAsLong();
        boolean reminders = this.reminders.getAsBoolean();
        if (reminders && (!this.ready || this.returnPending)) {
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
            this.remindersEnabled = this.reminders.getAsBoolean();
            this.returnPending = this.remindersEnabled;
            this.nextSummaryAt = now + RETURN_DELAY_MS;
        }
        if (this.remindersEnabled != this.reminders.getAsBoolean()) {
            this.remindersEnabled = this.reminders.getAsBoolean();
            this.returnPending = false;
            this.nextSummaryAt = now + REMINDER_INTERVAL_MS;
        }
        if (!this.remindersEnabled) {
            this.returnPending = false;
            this.nextSummaryAt = Long.MAX_VALUE;
            return;
        }
        if (now < this.nextSummaryAt) {
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
