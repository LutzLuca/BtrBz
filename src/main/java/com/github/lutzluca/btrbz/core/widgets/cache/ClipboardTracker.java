package com.github.lutzluca.btrbz.core.widgets.cache;

import com.github.lutzluca.btrbz.cache.CacheToken;

import com.github.lutzluca.btrbz.utils.ClientTickDispatcher;
import java.util.Objects;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/** Initialized, periodically polled clipboard owner. */
@Slf4j
public final class ClipboardTracker implements AutoCloseable {
    private static final int POLL_TICKS = 5;

    private final Supplier<String> valueSupplier;
    private final CacheToken changes = CacheToken.named("external.clipboard");

    private String value = "";
    private boolean initialized;
    private boolean failureLogged;

    private ClientTickDispatcher.TaskHandle taskHandle;

    public ClipboardTracker(Supplier<String> valueSupplier) {
        this.valueSupplier = Objects.requireNonNull(valueSupplier, "valueSupplier");
    }

    public void initialize() {
        if (this.initialized) {
            return;
        }

        this.initialized = true;
        this.readInitial();
    }

    public void start() {
        this.requireInitialized();

        if (this.taskHandle == null) {
            this.poll();
            this.taskHandle = ClientTickDispatcher.scheduleEvery(POLL_TICKS, _ -> this.poll());
        }
    }

    public boolean poll() {
        this.requireInitialized();

        try {
            String next = Objects.requireNonNullElse(this.valueSupplier.get(), "");
            this.failureLogged = false;

            if (next.equals(this.value)) {
                return false;
            }

            this.value = next;
            this.changes.invalidate("clipboard text changed");

            return true;
        } catch (RuntimeException exception) {
            if (!this.failureLogged) {
                log.warn("Failed to poll clipboard; keeping the last value", exception);
            }

            this.failureLogged = true;

            return false;
        }
    }

    public String value() {
        this.requireInitialized();
        return this.value;
    }

    public CacheToken changes() {
        return this.changes;
    }

    @Override
    public void close() {
        if (this.taskHandle != null) {
            this.taskHandle.close();
        }

        this.taskHandle = null;
        if (!this.value.isEmpty()) {
            this.value = "";
            this.changes.invalidate("clipboard tracker stopped");
        }
    }

    private void readInitial() {
        try {
            this.value = Objects.requireNonNullElse(this.valueSupplier.get(), "");
        } catch (RuntimeException exception) {
            this.failureLogged = true;
            log.warn("Failed to initialize clipboard tracker; using an empty value", exception);
        }
    }

    private void requireInitialized() {
        if (!this.initialized) {
            throw new IllegalStateException("Clipboard tracker is not initialized");
        }
    }
}
