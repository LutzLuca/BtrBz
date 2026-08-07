package com.github.lutzluca.btrbz.core.widgets.cache;

import com.github.lutzluca.btrbz.utils.ClientTickDispatcher;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/** Initialized, periodically polled purse owner with explicit unavailable state. */
@Slf4j
public final class PurseTracker implements AutoCloseable {
    private static final int POLL_TICKS = 5;
    private final Supplier<Optional<Double>> valueSupplier;
    private final CacheToken changes = CacheToken.named("external.purse");
    private Optional<Double> value = Optional.empty();
    private boolean initialized;
    private boolean failureLogged;
    private int ticks;
    private ClientTickDispatcher.Registration registration;

    public PurseTracker(Supplier<Optional<Double>> valueSupplier) {
        this.valueSupplier = Objects.requireNonNull(valueSupplier, "valueSupplier");
    }

    public void initialize() {
        if (this.initialized) return;
        this.initialized = true;
        this.readInitial();
    }

    public void start() {
        this.requireInitialized();
        if (this.registration == null) {
            this.registration = ClientTickDispatcher.registerCancellable(_ -> {
                if (++this.ticks >= POLL_TICKS) {
                    this.ticks = 0;
                    this.poll();
                }
            });
        }
    }

    public boolean poll() {
        this.requireInitialized();
        try {
            Optional<Double> next = valid(this.valueSupplier.get());
            this.failureLogged = false;
            if (next.equals(this.value)) return false;
            this.value = next;
            this.changes.invalidate(InvalidationReason.of("purse availability or value changed"));
            return true;
        } catch (RuntimeException exception) {
            if (!this.failureLogged) log.warn("Failed to poll purse; keeping the last value", exception);
            this.failureLogged = true;
            return false;
        }
    }

    public Optional<Double> value() {
        this.requireInitialized();
        return this.value;
    }

    public CacheToken changes() {
        return this.changes;
    }

    @Override
    public void close() {
        if (this.registration != null) this.registration.close();
        this.registration = null;
    }

    private void readInitial() {
        try {
            this.value = valid(this.valueSupplier.get());
        } catch (RuntimeException exception) {
            this.failureLogged = true;
            log.warn("Failed to initialize purse tracker; purse is unavailable", exception);
        }
    }

    private static Optional<Double> valid(Optional<Double> value) {
        if (value == null || value.isEmpty()) return Optional.empty();
        double amount = value.orElseThrow();
        return Double.isFinite(amount) && amount >= 0 ? Optional.of(amount) : Optional.empty();
    }

    private void requireInitialized() {
        if (!this.initialized) throw new IllegalStateException("Purse tracker is not initialized");
    }
}
