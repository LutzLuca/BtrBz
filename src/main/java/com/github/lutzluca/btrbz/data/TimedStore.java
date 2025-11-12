package com.github.lutzluca.btrbz.data;

import com.github.lutzluca.btrbz.utils.Utils;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TimedStore<T> {

    private final long timeToLiveMs;
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
        this.timeToLiveMs = timeToLiveMs;
        this.scheduler.scheduleAtFixedRate(
            this::cleanupExpired,
            timeToLiveMs,
            timeToLiveMs,
            TimeUnit.MILLISECONDS
        );
    }

    public void add(T item) {
        synchronized (this.entries) {
            this.entries.add(new Entry<>(item, System.currentTimeMillis() + this.timeToLiveMs));
        }
    }

    public Optional<T> removeFirstMatch(Predicate<T> predicate) {
        synchronized (this.entries) {
            for (var it = this.entries.iterator(); it.hasNext(); ) {
                var curr = it.next();
                if (curr.expiresAt < System.currentTimeMillis()) {
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
        var now = System.currentTimeMillis();
        synchronized (this.entries) {
            return this.entries
                .stream()
                .filter(entry -> entry.expiresAt >= now)
                .map(Entry::value)
                .toList();
        }
    }

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        synchronized (this.entries) {
            var expired = Utils.removeIfAndReturn(this.entries, entry -> entry.expiresAt < now);
            log.trace(
                "removed {} expired timedstore entry: {}",
                expired.size(),
                expired.toString()
            );
        }
    }

    private record Entry<T>(T value, long expiresAt) { }
}
