package com.github.lutzluca.btrbz.data;

import com.github.lutzluca.btrbz.utils.Utils;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TimedStore<T> implements AutoCloseable {

    private final long timeToLiveMs;
    private final LongSupplier clock;
    private final List<Entry<T>> entries = new ArrayList<>();

    // maybe use a shared `ScheduledExecutorService` for all TimedStore instances.
    // each TimedStore would register itself for periodic cleanup
    // avoiding unnecessary resource usage
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "btrbz-timedstore-cleanup");
        t.setDaemon(true);
        return t;
    });

    public TimedStore(long timeToLiveMs) {
        this(timeToLiveMs, System::currentTimeMillis);
    }

    TimedStore(long timeToLiveMs, LongSupplier clock) {
        if (timeToLiveMs <= 0) {
            throw new IllegalArgumentException("TimedStore timeToLiveMs must be > 0");
        }

        this.timeToLiveMs = timeToLiveMs;
        this.clock = Objects.requireNonNull(clock, "TimedStore clock must not be null");
        this.scheduler.scheduleAtFixedRate(this::cleanupExpired, timeToLiveMs, timeToLiveMs, TimeUnit.MILLISECONDS);
    }

    public void add(T item) {
        synchronized (this.entries) {
            this.entries.add(new Entry<>(item, this.clock.getAsLong() + this.timeToLiveMs));
        }
    }

    public Optional<T> removeFirstMatch(Predicate<T> predicate) {
        synchronized (this.entries) {
            for (var it = this.entries.iterator(); it.hasNext();) {
                var curr = it.next();
                if (curr.expiresAt < this.clock.getAsLong()) {
                    log.trace("removed expired timedstore entry: {}", curr);
                    it.remove();
                    continue;
                }

                if (predicate.test(curr.value)) {
                    it.remove();
                    return Optional.of(curr.value);
                }
            }
        }

        return Optional.empty();
    }

    public List<T> items() {
        var now = this.clock.getAsLong();
        synchronized (this.entries) {
            return this.entries.stream()
                .filter(entry -> entry.expiresAt >= now)
                .map(Entry::value)
                .toList();
        }
    }

    private void cleanupExpired() {
        long now = this.clock.getAsLong();
        synchronized (this.entries) {
            var expired = Utils.removeIfAndReturn(this.entries, entry -> entry.expiresAt < now);
            log.trace("removed {} expired timedstore entry: {}", expired.size(), expired);
        }
    }

    void triggerCleanup() {
        this.cleanupExpired();
    }

    int entryCount() {
        synchronized (this.entries) {
            return this.entries.size();
        }
    }

    @Override
    public void close() {
        this.scheduler.shutdownNow();
    }

    private record Entry<T>(T value, long expiresAt) {}
}
