package com.github.lutzluca.coflnet;

import java.time.Instant;
import java.util.Objects;

public record HistoryQuery(Instant start, Instant end) {
    public HistoryQuery {
        Objects.requireNonNull(start);
        Objects.requireNonNull(end);
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("History start must precede end");
        }
    }
}
