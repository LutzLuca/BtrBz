package com.github.lutzluca.coflnet;

import java.time.Instant;

public record HistoryPoint(
    Instant timestamp, Double buy, Double sell, Double minBuy,
    Double maxBuy, Double minSell, Double maxSell, Long buyVolume, Long sellVolume,
    Long buyMovingWeek, Long sellMovingWeek
) {}
