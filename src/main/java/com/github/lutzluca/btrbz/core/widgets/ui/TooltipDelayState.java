package com.github.lutzluca.btrbz.core.widgets.ui;

import org.jetbrains.annotations.Nullable;

public final class TooltipDelayState<T> {
    private final long delayNanos;
    private @Nullable T target;
    private long hoverStartedAt;

    public TooltipDelayState(long delayMillis) {
        if (delayMillis < 0) throw new IllegalArgumentException("Tooltip delay cannot be negative");
        this.delayNanos = delayMillis * 1_000_000L;
    }

    public boolean ready(@Nullable T target, long nowNanos) {
        if (target == null) {
            this.reset();
            return false;
        }
        if (this.target != target) {
            this.target = target;
            this.hoverStartedAt = nowNanos;
            return this.delayNanos == 0;
        }
        return nowNanos - this.hoverStartedAt >= this.delayNanos;
    }

    public void reset() {
        this.target = null;
        this.hoverStartedAt = 0;
    }
}
