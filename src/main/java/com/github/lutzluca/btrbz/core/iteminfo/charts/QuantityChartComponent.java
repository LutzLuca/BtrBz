package com.github.lutzluca.btrbz.core.iteminfo.charts;

import java.util.List;
import java.util.function.Supplier;

public final class QuantityChartComponent extends HistoryChartComponent {
    public QuantityChartComponent(HistoryViewport viewport, Supplier<ChartOptions> options) {
        super(viewport, options, List::of, true, 78);
    }
}
