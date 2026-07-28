package com.github.lutzluca.btrbz.core.widgets.layout;

public final class WidgetScaleResolver {
    public static final double MIN_SCALE = 0.5;
    public static final double MAX_SCALE = 2.0;

    private WidgetScaleResolver() {}

    public static double fitToCanvas(
        double requestedScale,
        double minimumScale,
        int canvasWidth,
        int canvasHeight,
        int logicalWidth,
        int logicalHeight
    ) {
        double safeMinimum = Math.max(0.0001, minimumScale);
        double resolved = Math.max(requestedScale, safeMinimum);
        resolved = fitDimension(resolved, canvasWidth, logicalWidth);
        resolved = fitDimension(resolved, canvasHeight, logicalHeight);
        return Math.max(safeMinimum, resolved);
    }

    public static double fitToCanvas(
        double requestedScale,
        int canvasWidth,
        int canvasHeight,
        int logicalWidth,
        int logicalHeight
    ) {
        return fitToCanvas(
            requestedScale,
            MIN_SCALE,
            canvasWidth,
            canvasHeight,
            logicalWidth,
            logicalHeight
        );
    }

    public static double combineRequestedScale(double baseScale, double widgetScale) {
        double safeBase = Double.isFinite(baseScale) ? baseScale : 1.0;
        double safeWidget = Double.isFinite(widgetScale) ? widgetScale : 1.0;
        return Math.max(
            MIN_SCALE,
            Math.min(MAX_SCALE, safeBase * safeWidget)
        );
    }

    public static double clampScale(double value) {
        if (!Double.isFinite(value)) return 1.0;
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, value));
    }

    public static boolean fitsCanvas(
        double scale,
        int canvasWidth,
        int canvasHeight,
        int logicalWidth,
        int logicalHeight
    ) {
        return Math.ceil(Math.max(1, logicalWidth) * scale) <= Math.max(1, canvasWidth)
            && Math.ceil(Math.max(1, logicalHeight) * scale) <= Math.max(1, canvasHeight);
    }

    private static double fitDimension(double scale, int available, int logicalSize) {
        int safeAvailable = Math.max(1, available);
        int safeLogicalSize = Math.max(1, logicalSize);
        if (Math.ceil(safeLogicalSize * scale) <= safeAvailable) return scale;

        return Math.min(scale, Math.nextDown(safeAvailable / (double) safeLogicalSize));
    }
}
