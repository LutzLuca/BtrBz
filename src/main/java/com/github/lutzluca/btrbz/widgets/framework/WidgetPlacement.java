package com.github.lutzluca.btrbz.widgets.framework;

public record WidgetPlacement(double x, double y, Anchor anchor) {
    public WidgetPlacement {
        x = clamp01(x);
        y = clamp01(y);
        anchor = anchor == null ? Anchor.TopLeft : anchor;
    }

    public static WidgetPlacement topLeft(double x, double y) {
        return new WidgetPlacement(x, y, Anchor.TopLeft);
    }

    public WidgetBounds resolve(int canvasWidth, int canvasHeight, int scaledWidgetWidth, int scaledWidgetHeight) {
        int maxX = Math.max(0, canvasWidth - Math.max(0, scaledWidgetWidth));
        int maxY = Math.max(0, canvasHeight - Math.max(0, scaledWidgetHeight));

        int usableWidth = Math.max(1, canvasWidth - Math.max(0, scaledWidgetWidth));
        int usableHeight = Math.max(1, canvasHeight - Math.max(0, scaledWidgetHeight));

        int absoluteX = clampInt((int) Math.round(this.x * usableWidth), 0, maxX);
        int absoluteY = clampInt((int) Math.round(this.y * usableHeight), 0, maxY);

        return new WidgetBounds(absoluteX, absoluteY, Math.max(0, scaledWidgetWidth), Math.max(0, scaledWidgetHeight));
    }

    public static WidgetPlacement fromAbsolute(
        int absoluteX,
        int absoluteY,
        int canvasWidth,
        int canvasHeight,
        int scaledWidgetWidth,
        int scaledWidgetHeight
    ) {
        int maxX = Math.max(0, canvasWidth - Math.max(0, scaledWidgetWidth));
        int maxY = Math.max(0, canvasHeight - Math.max(0, scaledWidgetHeight));

        int clampedX = clampInt(absoluteX, 0, maxX);
        int clampedY = clampInt(absoluteY, 0, maxY);

        double relativeX = clampedX / (double) Math.max(1, canvasWidth - Math.max(0, scaledWidgetWidth));
        double relativeY = clampedY / (double) Math.max(1, canvasHeight - Math.max(0, scaledWidgetHeight));

        return topLeft(relativeX, relativeY);
    }

    public static double clamp01(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }

    public static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public enum Anchor {
        TopLeft
    }
}
