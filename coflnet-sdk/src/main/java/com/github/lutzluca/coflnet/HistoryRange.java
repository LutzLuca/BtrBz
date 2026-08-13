package com.github.lutzluca.coflnet;

import java.time.Instant;
import java.util.Objects;

/** A Coflnet history endpoint selection. */
public sealed interface HistoryRange permits HistoryRange.Preset, HistoryRange.Custom {
    enum Preset implements HistoryRange {
        HOUR,
        DAY,
        WEEK
    }

    record Custom(Instant start, Instant end) implements HistoryRange {
        public Custom {
            Objects.requireNonNull(start, "start");
            Objects.requireNonNull(end, "end");
            if (!start.isBefore(end)) {
                throw new IllegalArgumentException("start must be before end");
            }
        }
    }
}
