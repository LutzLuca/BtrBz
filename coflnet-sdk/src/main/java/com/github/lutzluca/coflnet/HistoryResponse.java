package com.github.lutzluca.coflnet;

import java.time.Instant;
import java.util.List;

public record HistoryResponse(
    List<HistoryPoint> points, Instant checkedAt,
    Instant coverageStart, Instant coverageEnd, HistorySource source
) {
    public HistoryResponse {
        points = List.copyOf(points);
    }
}
