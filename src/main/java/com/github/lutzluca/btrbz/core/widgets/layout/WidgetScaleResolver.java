package com.github.lutzluca.btrbz.core.widgets.layout;

import com.github.lutzluca.btrbz.core.widgets.WidgetMath;
import net.minecraft.client.Minecraft;

public final class WidgetScaleResolver {
    public static final double MIN_SCALE = 0.5;
    public static final double MAX_SCALE = 2.0;

    private WidgetScaleResolver() {
    }

    public static double automaticGuiScale() {
        var window = Minecraft.getInstance().getWindow();
        double guiScale = window.getGuiScale();

        return switch ((int) Math.round(guiScale)) {
            case 1, 2 -> 1.00;
            case 3 -> 0.71;
            case 4 -> 0.64;
            default -> 0.64 * Math.pow(4.0 / guiScale, 0.8);
        };
    }

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

    public static double clampScale(double value) {
        if (!Double.isFinite(value)) {
            return 1.0;
        }

        return WidgetMath.clamp(value, MIN_SCALE, MAX_SCALE);
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

        if (Math.ceil(safeLogicalSize * scale) <= safeAvailable) {
            return scale;
        }

        return Math.min(scale, Math.nextDown(safeAvailable / (double) safeLogicalSize));
    }
}
