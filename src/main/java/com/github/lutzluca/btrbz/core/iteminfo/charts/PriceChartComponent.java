package com.github.lutzluca.btrbz.core.iteminfo.charts;

import java.util.function.Supplier;

public final class PriceChartComponent extends HistoryChartComponent {
    public PriceChartComponent(HistoryViewport viewport, Supplier<ChartOptions> options) {
        super(viewport, options, false, 150);
    }
}
