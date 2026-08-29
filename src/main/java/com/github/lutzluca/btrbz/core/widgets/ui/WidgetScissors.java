package com.github.lutzluca.btrbz.core.widgets.ui;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2fc;
import org.joml.Vector2f;

public final class WidgetScissors {
    private WidgetScissors() {}

    public static ScreenRectangle conservative(
        Matrix3x2fc pose,
        int left,
        int top,
        int right,
        int bottom
    ) {
        var topLeft = pose.transformPosition(left, top, new Vector2f());
        var bottomRight = pose.transformPosition(right, bottom, new Vector2f());

        int minX = Mth.floor(topLeft.x);
        int minY = Mth.floor(topLeft.y);
        int maxX = Mth.ceil(bottomRight.x);
        int maxY = Mth.ceil(bottomRight.y);

        return new ScreenRectangle(minX, minY, maxX - minX, maxY - minY);
    }
}
