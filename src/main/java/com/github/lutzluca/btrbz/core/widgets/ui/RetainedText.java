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

        var pose = graphics.pose();
        var scissor = graphics.scissorStack.peek();
        long revision = TextRenderRevision.current();

        if (this.key == null
            || !this.key.matches(font, text, pose, x, y, color, dropShadow, scissor, revision)) {
            var poseSnapshot = new Matrix3x2f(pose);

            this.key = new Key(
                font, text, poseSnapshot, x, y, color, dropShadow, scissor, revision);
            this.state = new GuiTextRenderState(
                font, text, poseSnapshot, x, y, color, 0, dropShadow, false, scissor);
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
        @Nullable ScreenRectangle scissor,
        long textRevision
    ) {
        boolean matches(
            Font otherFont,
            FormattedCharSequence otherText,
            Matrix3x2f otherPose,
            int otherX,
            int otherY,
            int otherColor,
            boolean otherDropShadow,
            @Nullable ScreenRectangle otherScissor,
            long otherRevision
        ) {
            return this.font == otherFont
                && this.text == otherText
                && this.x == otherX
                && this.y == otherY
                && this.color == otherColor
                && this.dropShadow == otherDropShadow
                && this.textRevision == otherRevision
                && this.pose.equals(otherPose)
                && Objects.equals(this.scissor, otherScissor);
        }
    }
}
