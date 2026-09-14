package com.github.lutzluca.btrbz.core.widgets.ui;

import com.github.lutzluca.btrbz.core.widgets.WidgetMath;
import lombok.Getter;
import lombok.experimental.Accessors;

/** Keeps an absolute scroll offset independent of transient retained-layout passes. */
final class RetainedScrollState {
    @Getter
    @Accessors(fluent = true)
    private double offset;

    public double restore(double maximumOffset) {
        return WidgetMath.clamp(this.offset, 0.0, Math.max(0.0, maximumOffset));
    }

    public void remember(double offset) {
        this.offset = Math.max(0.0, offset);
    }

}
