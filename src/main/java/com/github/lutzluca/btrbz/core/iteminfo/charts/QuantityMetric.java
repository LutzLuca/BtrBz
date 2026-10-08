package com.github.lutzluca.btrbz.core.iteminfo.charts;

import com.github.lutzluca.coflnet.HistoryPoint;

public enum QuantityMetric {
    MovingWeek("7-day moving volume"),
    OpenOrders("Open-order quantity");

    private final String label;

    QuantityMetric(String label) {
        this.label = label;
    }

    public String label() {
        return this.label;
    }

    public Long value(HistoryPoint point, boolean buy) {
        return switch (this) {
            case MovingWeek -> buy ? point.buyMovingWeek() : point.sellMovingWeek();
            case OpenOrders -> buy ? point.buyVolume() : point.sellVolume();
        };
    }
}
