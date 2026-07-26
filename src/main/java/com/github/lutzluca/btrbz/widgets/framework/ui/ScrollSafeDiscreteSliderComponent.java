package com.github.lutzluca.btrbz.widgets.framework.ui;

import io.wispforest.owo.ui.component.DiscreteSliderComponent;
import io.wispforest.owo.ui.core.Sizing;

/** A discrete slider that leaves mouse-wheel input to an enclosing scroll container. */
public final class ScrollSafeDiscreteSliderComponent extends DiscreteSliderComponent {
    private final double step;

    public ScrollSafeDiscreteSliderComponent(Sizing horizontalSizing, double min, double max) {
        this(horizontalSizing, min, max, 1.0);
    }

    public ScrollSafeDiscreteSliderComponent(
        Sizing horizontalSizing,
        double min,
        double max,
        double step
    ) {
        super(horizontalSizing, min, max);
        if (!Double.isFinite(step) || step <= 0) throw new IllegalArgumentException("step must be positive");
        this.step = step;
    }

    @Override
    public double discreteValue() {
        if (this.step <= 0) return super.discreteValue();
        return snapToStep(super.discreteValue(), this.min, this.max, this.step);
    }

    @Override
    protected void applyValue() {
        if (this.step > 0 && this.max > this.min) {
            this.value = (this.discreteValue() - this.min) / (this.max - this.min);
        }
        super.applyValue();
    }

    @Override
    public boolean onMouseScroll(double mouseX, double mouseY, double amount) {
        return false;
    }

    static double snapToStep(double value, double min, double max, double step) {
        double snapped = min + Math.round((value - min) / step) * step;
        return Math.max(min, Math.min(max, snapped));
    }
}
