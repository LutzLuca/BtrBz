package com.github.lutzluca.btrbz.widgets;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

@Getter
public class TextDisplayWidget extends DraggableWidget {

    public static final int PADDING_X = 4;
    public static final int PADDING_Y = 2;
    public static final int LINE_SPACING = 2;

    private List<Component> lines;

    public TextDisplayWidget(int x, int y, Component text, Screen parentScreen) {
        this(x, y, List.of(text), parentScreen);
    }

    public TextDisplayWidget(int x, int y, List<Component> lines, Screen parentScreen) {
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
        List<Component> lines,
        Screen parentScreen
    ) {
        super(x, y, width, height, mergeLines(lines), parentScreen);
        this.lines = new ArrayList<>(lines);
    }

    private static int computeMaxTextWidth(List<Component> lines) {
        var textRenderer = Minecraft.getInstance().font;
        return lines.stream().mapToInt(textRenderer::width).max().orElse(0);
    }

    private static int computeTotalTextHeight(List<Component> lines) {
        var textRenderer = Minecraft.getInstance().font;
        return lines.size() * textRenderer.lineHeight + (lines.size() - 1) * LINE_SPACING;
    }

    private static Component mergeLines(List<Component> lines) {
        if (lines.isEmpty()) { return Component.empty(); }

        MutableComponent merged = Component.empty();
        lines.forEach(line -> {
            if (!merged.getString().isEmpty()) { merged.append(Component.literal("\n")); }
            merged.append(line);
        });
        return merged;
    }

    public TextDisplayWidget setLines(List<Component> newLines) {
        return this.setLines(newLines, true);
    }

    public TextDisplayWidget setLines(List<Component> lines, boolean autoResize) {
        this.lines = new ArrayList<>(lines);
        this.setMessage(mergeLines(lines));

        if (autoResize) {
            this.width = computeMaxTextWidth(lines) + PADDING_X * 2;
            this.height = computeTotalTextHeight(lines) + PADDING_Y * 2;
        }

        return this;
    }

    public TextDisplayWidget setText(Component text) {
        return this.setLines(List.of(text), true);
    }

    @Override
    protected void renderBackground(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
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
    protected void renderBorder(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        if (!this.isDragging()) { return; }

       ctx.submitOutline(this.getX(), this.getY(), this.width, this.height, 0xFFFFD700);
    }

    @Override
    protected void renderContent(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        var textRenderer = Minecraft.getInstance().font;
        int textY = this.getY() + PADDING_Y;

        ctx.enableScissor(
            this.getX(),
            this.getY(),
            this.getX() + this.getWidth(),
            this.getY() + this.getHeight()
        );

        for (Component line : this.lines) {
            int textWidth = textRenderer.width(line);
            int textX = this.getX() + (this.width - textWidth) / 2;
            ctx.drawString(textRenderer, line, textX, textY, 0xFFFFFF, false);
            textY += textRenderer.lineHeight + LINE_SPACING;
        }

        ctx.disableScissor();
    }
}
