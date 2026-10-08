package com.github.lutzluca.btrbz.data;

import com.github.lutzluca.btrbz.utils.Utils;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TimedStore<T> {

    private final long timeToLiveMs;
    private final LongSupplier clock;
    private final List<Entry<T>> entries = new ArrayList<>();

    public TimedStore(long timeToLiveMs) {
        this(timeToLiveMs, System::currentTimeMillis);
    }

    TimedStore(long timeToLiveMs, LongSupplier clock) {
        if (timeToLiveMs <= 0) {
            throw new IllegalArgumentException("TimedStore timeToLiveMs must be > 0");
        }

        this.timeToLiveMs = timeToLiveMs;
        this.clock = Objects.requireNonNull(clock, "TimedStore clock must not be null");
    }

    public void add(T item) {
        synchronized (this.entries) {
            long now = this.clock.getAsLong();
            this.cleanupExpired(now);
            this.entries.add(new Entry<>(item, now + this.timeToLiveMs));
        }
    }

    public Optional<T> removeFirstMatch(Predicate<T> predicate) {
        synchronized (this.entries) {
            this.cleanupExpired(this.clock.getAsLong());
            for (var it = this.entries.iterator(); it.hasNext();) {
                var curr = it.next();
                if (predicate.test(curr.value)) {
                    it.remove();
                    return Optional.of(curr.value);
                }
            }
        }

        return Optional.empty();
    }

    public List<T> items() {
        synchronized (this.entries) {
            this.cleanupExpired(this.clock.getAsLong());
            return this.entries
                .stream()
                .map(Entry::value)
                .toList();
        }
    }

    public void clear() {
        synchronized (this.entries) {
            this.entries.clear();
        }
    }

    private void cleanupExpired(long now) {
        var expired = Utils.removeIfAndReturn(this.entries, entry -> entry.expiresAt < now);
        if (!expired.isEmpty()) {
            log.trace("Removed {} expired TimedStore entries: {}", expired.size(), expired);
        }
    }

    private record Entry<T>(T value, long expiresAt) {}
}
