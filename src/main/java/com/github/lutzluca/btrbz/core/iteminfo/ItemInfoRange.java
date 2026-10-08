package com.github.lutzluca.btrbz.core.iteminfo;

import com.github.lutzluca.coflnet.HistorySource;
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

    public HistorySource source() {
        return switch (this) {
            case Hour -> HistorySource.Hour;
            case Day -> HistorySource.Day;
            case Week -> HistorySource.Week;
            case Month, Year, Custom -> HistorySource.Range;
        };
    }
}
