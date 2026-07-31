package com.github.lutzluca.btrbz.core.widgets.ui;

/** Keeps an absolute scroll offset independent of transient retained-layout passes. */
final class RetainedScrollState {
    private double offset;

    public double restore(double maximumOffset) {
        return Math.min(this.offset, Math.max(0.0, maximumOffset));
    }

    public void remember(double offset) {
        this.offset = Math.max(0.0, offset);
    }
}
