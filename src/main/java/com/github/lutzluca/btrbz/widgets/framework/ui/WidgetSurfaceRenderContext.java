package com.github.lutzluca.btrbz.widgets.framework.ui;

public final class WidgetSurfaceRenderContext {
    private static final ThreadLocal<Double> DENSITY = ThreadLocal.withInitial(() -> 1.0);

    private WidgetSurfaceRenderContext() {}

    public static double density() {
        return DENSITY.get();
    }

    static double enter(double density) {
        double previous = DENSITY.get();
        DENSITY.set(Math.max(0.0001, density));
        return previous;
    }

    static void restore(double density) {
        DENSITY.set(density);
    }
}
