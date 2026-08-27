package com.github.lutzluca.btrbz.core.widgets.ui;

import io.wispforest.owo.ui.core.OwoUIGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

import java.util.Objects;

public final class RetainedText {
    private @Nullable Key key;
    private @Nullable GuiTextRenderState state;

    public void draw(
        OwoUIGraphics graphics,
        Font font,
        FormattedCharSequence text,
        int x,
        int y,
        int color,
        boolean dropShadow
    ) {
        if (ARGB.alpha(color) == 0) {
            return;
        }

        var pose = new Matrix3x2f(graphics.pose());
        var scissor = graphics.scissorStack.peek();
        var current = new Key(font, text, pose, x, y, color, dropShadow, scissor);

        if (this.state == null || !current.equals(this.key)) {
            this.state = new GuiTextRenderState(
                font, text, pose, x, y, color, 0, dropShadow, false, scissor);
            this.key = current;
        }

        if (this.state.bounds() == null) {
            this.state = null;
            this.key = null;
            return;
        }

        graphics.guiRenderState.addText(this.state);
    }

    private record Key(
        Font font,
        FormattedCharSequence text,
        Matrix3x2f pose,
        int x,
        int y,
        int color,
        boolean dropShadow,
        @Nullable ScreenRectangle scissor
    ) {
        @Override
        public boolean equals(Object other) {
            return other instanceof Key otherKey
                && this.font == otherKey.font
                && this.text == otherKey.text
                && this.x == otherKey.x
                && this.y == otherKey.y
                && this.color == otherKey.color
                && this.dropShadow == otherKey.dropShadow
                && this.pose.equals(otherKey.pose)
                && Objects.equals(this.scissor, otherKey.scissor);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                System.identityHashCode(this.text), this.x, this.y, this.color, this.scissor);
        }
    }
}
