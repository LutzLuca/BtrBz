package com.github.lutzluca.btrbz.core.widgets.ui;

/** Shared presentation choices used by more than one BtrBz widget. */
public final class WidgetDisplayOptions {
    private WidgetDisplayOptions() {}

    public enum NumberStyle { Compact, Exact }
    public enum PriceDisplay { None, Unit, Total, Both }
    public enum QueueDisplay { Items, OrdersAndItems, Hidden }
    public enum UndercutDetail { PriceGap, Queue, PriceGapAndQueue, Hidden }
}
