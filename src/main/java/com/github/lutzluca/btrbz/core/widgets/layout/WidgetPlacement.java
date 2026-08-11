package com.github.lutzluca.btrbz.core.widgets.layout;

import com.github.lutzluca.btrbz.core.widgets.WidgetMath;

public record WidgetPlacement(double x, double y) {
    public WidgetPlacement {
        x = WidgetMath.unit(x);
        y = WidgetMath.unit(y);
    }

    public static WidgetPlacement topLeft(double x, double y) {
        return new WidgetPlacement(x, y);
    }

    public WidgetBounds resolve(int canvasWidth, int canvasHeight, int scaledWidgetWidth, int scaledWidgetHeight) {
        int maxX = Math.max(0, canvasWidth - Math.max(0, scaledWidgetWidth));
        int maxY = Math.max(0, canvasHeight - Math.max(0, scaledWidgetHeight));

        int absoluteX = WidgetMath.clamp((int) Math.round(this.x * Math.max(1, canvasWidth)), 0, maxX);
        int absoluteY = WidgetMath.clamp((int) Math.round(this.y * Math.max(1, canvasHeight)), 0, maxY);

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

        int clampedX = WidgetMath.clamp(absoluteX, 0, maxX);
        int clampedY = WidgetMath.clamp(absoluteY, 0, maxY);

        double relativeX = clampedX / (double) Math.max(1, canvasWidth);
        double relativeY = clampedY / (double) Math.max(1, canvasHeight);

        return topLeft(relativeX, relativeY);
    }
}
