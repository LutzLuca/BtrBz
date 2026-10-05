package com.github.lutzluca.btrbz.core.iteminfo.charts;

import com.github.lutzluca.coflnet.MayorTerm;

import java.util.List;
import java.util.function.Supplier;

public final class QuantityChartComponent extends HistoryChartComponent {
    public QuantityChartComponent(
        HistoryViewport viewport,
        Supplier<ChartOptions> options,
        Supplier<List<MayorTerm>> mayors
    ) {
        super(viewport, options, mayors, true, 78);
    }
}
