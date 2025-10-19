package com.github.lutzluca.btrbz.widgets;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

@Getter
public class TextDisplayWidget extends DraggableWidget {

    public static final int PADDING_X = 4;
    public static final int PADDING_Y = 2;
    public static final int LINE_SPACING = 2;

    private List<Text> lines;

    public TextDisplayWidget(int x, int y, Text text, Screen parentScreen) {
        this(x, y, List.of(text), parentScreen);
    }

    public TextDisplayWidget(int x, int y, List<Text> lines, Screen parentScreen) {
        super(
            x,
            y,
            computeMaxTextWidth(lines) + PADDING_X * 2,
            computeTotalTextHeight(lines) + PADDING_Y * 2,
            mergeLines(lines),
            parentScreen
        );

        this.lines = new ArrayList<>(lines);
    }

    public TextDisplayWidget(
        int x,
        int y,
        int width,
        int height,
        List<Text> lines,
        Screen parentScreen
    ) {
        super(x, y, width, height, mergeLines(lines), parentScreen);
        this.lines = new ArrayList<>(lines);
    }

    private static int computeMaxTextWidth(List<Text> lines) {
        var textRenderer = MinecraftClient.getInstance().textRenderer;
        return lines.stream().mapToInt(textRenderer::getWidth).max().orElse(0);
    }

    private static int computeTotalTextHeight(List<Text> lines) {
        var textRenderer = MinecraftClient.getInstance().textRenderer;
        return lines.size() * textRenderer.fontHeight + (lines.size() - 1) * LINE_SPACING;
    }

    private static Text mergeLines(List<Text> lines) {
        if (lines.isEmpty()) { return Text.empty(); }

        MutableText merged = Text.empty();
        lines.forEach(line -> {
            if (!merged.getString().isEmpty()) { merged.append(Text.literal("\n")); }
            merged.append(line);
        });
        return merged;
    }

    public TextDisplayWidget setLines(List<Text> newLines) {
        return this.setLines(newLines, true);
    }

    public TextDisplayWidget setLines(List<Text> lines, boolean autoResize) {
        this.lines = new ArrayList<>(lines);
        this.setMessage(mergeLines(lines));

        if (autoResize) {
            this.width = computeMaxTextWidth(lines) + PADDING_X * 2;
            this.height = computeTotalTextHeight(lines) + PADDING_Y * 2;
        }

        return this;
    }

    public TextDisplayWidget setText(Text text) {
        return this.setLines(List.of(text), true);
    }

    @Override
    protected void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (!this.isDragging()) { return; }

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
        if (!this.isDragging()) { return; }

        ctx.drawBorder(this.getX(), this.getY(), this.width, this.height, 0xFFFFD700);
    }

    @Override
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        var textRenderer = MinecraftClient.getInstance().textRenderer;
        int textY = this.getY() + PADDING_Y;

        ctx.enableScissor(
            this.getX(),
            this.getY(),
            this.getX() + this.getWidth(),
            this.getY() + this.getHeight()
        );

        for (Text line : this.lines) {
            int textWidth = textRenderer.getWidth(line);
            int textX = this.getX() + (this.width - textWidth) / 2;
            ctx.drawText(textRenderer, line, textX, textY, 0xFFFFFF, false);
            textY += textRenderer.fontHeight + LINE_SPACING;
        }

        ctx.disableScissor();
    }
}
