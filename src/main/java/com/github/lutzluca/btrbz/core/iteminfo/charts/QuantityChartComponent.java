package com.github.lutzluca.btrbz.core.iteminfo.charts;

import java.util.function.Supplier;

public final class QuantityChartComponent extends HistoryChartComponent {
    public QuantityChartComponent(HistoryViewport viewport, Supplier<ChartOptions> options) {
        super(viewport, options, true, 78);
    }
}
