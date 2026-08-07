package com.github.lutzluca.btrbz.core.widgets.cache;

import com.github.lutzluca.btrbz.utils.ClientTickDispatcher;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;

/** Initialized, client-thread UTC day owner. */
@Slf4j
public final class UtcDayTracker implements AutoCloseable {
    private final LongSupplier daySupplier;
    private final CacheToken changes = CacheToken.named("external.utc-day");
    private long currentDay;
    private boolean initialized;
    private boolean failureLogged;
    private ClientTickDispatcher.Registration registration;

    public UtcDayTracker() {
        this(() -> LocalDate.now(ZoneOffset.UTC).toEpochDay());
    }

    public UtcDayTracker(LongSupplier daySupplier) {
        this.daySupplier = Objects.requireNonNull(daySupplier, "daySupplier");
    }

    public void initialize() {
        if (this.initialized) return;
        this.currentDay = this.daySupplier.getAsLong();
        this.initialized = true;
    }

    public void start() {
        this.requireInitialized();
        if (this.registration == null) {
            this.registration = ClientTickDispatcher.registerCancellable(_ -> this.poll());
        }
    }

    public boolean poll() {
        this.requireInitialized();
        try {
            long next = this.daySupplier.getAsLong();
            this.failureLogged = false;
            if (next == this.currentDay) return false;
            this.currentDay = next;
            this.changes.invalidate(InvalidationReason.of("UTC day changed"));
            return true;
        } catch (RuntimeException exception) {
            if (!this.failureLogged) log.warn("Failed to poll UTC day; keeping the last value", exception);
            this.failureLogged = true;
            return false;
        }
    }

    public long currentDay() {
        this.requireInitialized();
        return this.currentDay;
    }

    public CacheToken changes() {
        return this.changes;
    }

    @Override
    public void close() {
        if (this.registration != null) this.registration.close();
        this.registration = null;
    }

    private void requireInitialized() {
        if (!this.initialized) throw new IllegalStateException("UTC day tracker is not initialized");
    }
}
