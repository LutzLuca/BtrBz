package com.github.lutzluca.coflnet;

import java.time.Duration;

/** The endpoint that supplied history, including its documented sampling cadence. */
public enum HistorySource {
    Range(null, null),
    Hour(Duration.ofHours(1), Duration.ofSeconds(20)),
    Day(Duration.ofDays(1), Duration.ofMinutes(5)),
    Week(Duration.ofDays(7), Duration.ofHours(2));

    private final Duration duration;
    private final Duration cadence;

    HistorySource(Duration duration, Duration cadence) {
        this.duration = duration;
        this.cadence = cadence;
    }

    public Duration duration() {
        return this.duration;
    }

    public Duration cadence() {
        return this.cadence;
    }
}
