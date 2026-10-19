package com.github.lutzluca.btrbz.core.alert;

import io.vavr.control.Try;
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
    private final Consumer<ReachedAlert> notifyReached;
    private final IntPredicate notifySummary;
    private final LongSupplier clock;
    private boolean ready;
    private boolean returnPending;
    private long readyAt;
    private long nextSummaryAt = Long.MAX_VALUE;
    private long lastSnooze;

    public AlertDelivery(AlertManager manager, Consumer<ReachedAlert> notifyReached, IntPredicate notifySummary) {
        this(manager, notifyReached, notifySummary, System::currentTimeMillis);
    }

    AlertDelivery(
        AlertManager manager,
        Consumer<ReachedAlert> notifyReached,
        IntPredicate notifySummary,
        LongSupplier clock
    ) {
        this.manager = manager;
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
        if (!this.ready || (this.returnPending && now >= this.manager.snoozedUntil())) {
            return;
        }
        this.nextSummaryAt = Math.min(this.nextSummaryAt, now + REMINDER_INTERVAL_MS);
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
            this.returnPending = true;
            this.readyAt = now + RETURN_DELAY_MS;
            this.lastSnooze = this.manager.snoozedUntil();
            this.nextSummaryAt = Math.max(this.readyAt, this.lastSnooze);
        }
        if (this.lastSnooze != this.manager.snoozedUntil()) {
            this.lastSnooze = this.manager.snoozedUntil();
            this.nextSummaryAt = Math.max(this.readyAt, this.lastSnooze);
        }
        if (now < Math.max(this.nextSummaryAt, this.lastSnooze)) {
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
