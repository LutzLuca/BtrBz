package com.github.lutzluca.btrbz.widgets;

import lombok.Getter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

@Getter
public class TextDisplayWidget extends DraggableWidget {

    public static final int PADDING_X = 4;
    public static final int PADDING_Y = 2;

    private Text text;

    public TextDisplayWidget(int x, int y, int width, int height, Text text, Screen parentScreen) {
        super(x, y, width, height, text, parentScreen);

        this.text = text;
    }

    public TextDisplayWidget(int x, int y, Text text, Screen parentScreen) {
        super(
            x,
            y,
            computeTextWidth(text) + PADDING_X * 2,
            computeTextHeight() + PADDING_Y * 2,
            text,
            parentScreen
        );

        this.text = text;
    }

    private static int computeTextWidth(final Text text) {
        return MinecraftClient.getInstance().textRenderer.getWidth(text);
    }

    private static int computeTextHeight() {
        return MinecraftClient.getInstance().textRenderer.fontHeight;
    }

    public TextDisplayWidget setText(Text text) {
        return this.setText(text, true);
    }

    public TextDisplayWidget setText(Text text, boolean autoResize) {
        this.text = text;
        this.setMessage(text);

        if (autoResize) {
            this.width = computeTextWidth(text) + PADDING_X * 2;
            this.height = computeTextHeight() + PADDING_Y * 2;
        }

        return this;
    }

    @Override
    protected void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (!this.isDragging()) {
            return;
        }

        ctx.fill(
            this.getX(),
            this.getY(),
            this.getX() + this.getWidth(),
            this.getY() + this.getHeight(),
            0x80202020
        );
    }

    @Override
    protected void renderBorder(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (!this.isDragging()) {
            return;
        }

        ctx.drawBorder(this.getX(), this.getY(), this.width, this.height, 0xFFFFD700);
    }

    @Override
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        var textRenderer = MinecraftClient.getInstance().textRenderer;
        var textWidth = textRenderer.getWidth(this.text);
        var textX = this.getX() + (this.width - textWidth) / 2;
        var textY = this.getY() + (this.height - textRenderer.fontHeight) / 2;

        ctx.enableScissor(
            this.getX(),
            this.getY(),
            this.getX() + this.width,
            this.getY() + this.height
        );

        ctx.drawText(textRenderer, this.text, textX, textY, 0xFFFFFF, false);
        ctx.disableScissor();
    }
}