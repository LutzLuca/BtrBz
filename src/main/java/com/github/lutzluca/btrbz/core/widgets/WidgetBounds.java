package com.github.lutzluca.btrbz.core.widgets;

public record WidgetBounds(int x, int y, int width, int height) {
    public boolean contains(double pointX, double pointY) {
        return pointX >= this.x
            && pointY >= this.y
            && pointX < this.x + this.width
            && pointY < this.y + this.height;
    }
}
