package com.github.lutzluca.btrbz.core.widgets;

public record WidgetCanvas(int x, int y, int width, int height) {
    public WidgetCanvas {
        width = Math.max(1, width);
        height = Math.max(1, height);
    }
}
