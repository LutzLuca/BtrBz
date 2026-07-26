package com.github.lutzluca.btrbz.widgets.framework.ui;

import io.wispforest.owo.ui.core.OwoUIGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.util.FormattedCharSequence;

/** Aligns directly drawn widget text to the native render-surface pixel grid. */
public final class WidgetSurfaceText {
    private WidgetSurfaceText() {}

    public static void draw(
        OwoUIGraphics graphics,
        Font font,
        FormattedCharSequence text,
        int x,
        int y,
        int color,
        boolean shadow
    ) {
        graphics.push();
        try {
            graphics.translate(0, 1.0 / WidgetSurfaceRenderContext.density());
            graphics.text(font, text, x, y, color, shadow);
        } finally {
            graphics.pop();
        }
    }
}
