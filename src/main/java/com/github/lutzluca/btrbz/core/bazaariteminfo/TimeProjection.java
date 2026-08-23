package com.github.lutzluca.btrbz.core.bazaariteminfo;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Maps elapsed time to one shared plot X axis. */
public record TimeProjection(long minimumEpochMillis, long maximumEpochMillis, int left, int width) {
    public TimeProjection {
        width = Math.max(1, width);
    }

    public static TimeProjection from(List<Instant> timestamps, int left, int width) {
        Objects.requireNonNull(timestamps, "timestamps");
        long minimum = Long.MAX_VALUE;
        long maximum = Long.MIN_VALUE;
        for (var timestamp : timestamps) {
            if (timestamp == null) {
                continue;
            }
            long epochMillis = timestamp.toEpochMilli();
            minimum = Math.min(minimum, epochMillis);
            maximum = Math.max(maximum, epochMillis);
        }
        if (minimum == Long.MAX_VALUE) {
            return new TimeProjection(0, 0, left, width);
        }
        return new TimeProjection(minimum, maximum, left, width);
    }

    public int x(Instant timestamp) {
        Objects.requireNonNull(timestamp, "timestamp");
        long span = this.maximumEpochMillis - this.minimumEpochMillis;
        if (span <= 0) {
            return this.left + this.width / 2;
        }
        double fraction = (double) (timestamp.toEpochMilli() - this.minimumEpochMillis) / span;
        return this.left + (int) Math.round(clamp(fraction) * Math.max(0, this.width - 1));
    }

    public boolean hasRange() {
        return this.maximumEpochMillis > this.minimumEpochMillis;
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
