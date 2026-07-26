package com.github.lutzluca.btrbz.widgets.framework.ui;

import io.wispforest.owo.ui.core.Surface;
import org.joml.Matrix3x2f;

public final class WidgetSurfaces {
    private WidgetSurfaces() {}

    public static Surface roundedPanel(int color, int radius) {
        return (context, component) -> drawRoundedPanel(
            context,
            component.x(),
            component.y(),
            component.width(),
            component.height(),
            color,
            radius
        );
    }

    public static void drawRoundedPanel(
        io.wispforest.owo.ui.core.OwoUIGraphics context,
        int x,
        int y,
        int width,
        int height,
        int color,
        int radius
    ) {
        int resolvedRadius = Math.max(0, Math.min(radius, Math.min(width, height) / 2));

        if (resolvedRadius == 0) {
            context.fill(x, y, x + width, y + height, color);
            return;
        }

        context.guiRenderState.addGuiElement(new RoundedRectangleElementRenderState(
            new Matrix3x2f(context.pose()),
            context.scissorStack.peek(),
            x,
            y,
            width,
            height,
            resolvedRadius,
            Math.max(8, resolvedRadius * 2),
            color
        ));
    }

}
