package com.github.lutzluca.coflnet;

import java.time.Instant;
import java.util.Objects;

public record HistoryQuery(Instant start, Instant end, HistorySource source) {
    public HistoryQuery(Instant start, Instant end) {
        this(start, end, HistorySource.Range);
    }

    public HistoryQuery {
        Objects.requireNonNull(start);
        Objects.requireNonNull(end);
        Objects.requireNonNull(source);
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("History start must precede end");
        }
    }
}
