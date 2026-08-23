package com.github.lutzluca.btrbz.core.widgets.ui;

import com.github.lutzluca.btrbz.core.widgets.WidgetMath;

/** Pure boundary policy for handing wheel input from a nested scroller to its parent. */
final class ScrollWheelPolicy {
    private ScrollWheelPolicy() {}

    static boolean wouldMove(double currentOffset, double maximumOffset, double amount, double distance) {
        double target = WidgetMath.clamp(
            currentOffset - amount * distance,
            0.0,
            Math.max(0.0, maximumOffset));
        return target != currentOffset;
    }
}
