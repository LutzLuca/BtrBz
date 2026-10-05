package com.github.lutzluca.btrbz.core.iteminfo;

import java.time.Duration;

public enum ItemInfoRange {
    Hour(Duration.ofHours(1)),
    Day(Duration.ofDays(1)),
    Week(Duration.ofDays(7)),
    Month(Duration.ofDays(30)),
    Year(Duration.ofDays(365)),
    Custom(Duration.ZERO);

    private final Duration duration;

    ItemInfoRange(Duration duration) {
        this.duration = duration;
    }

    public Duration duration() {
        return this.duration;
    }
}
