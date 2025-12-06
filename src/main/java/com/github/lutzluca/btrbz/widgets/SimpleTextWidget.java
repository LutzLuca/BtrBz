package com.github.lutzluca.btrbz.widgets;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

public class SimpleTextWidget extends AbstractWidget {

    private int backgroundColor;
    private int textColor;
    private int disabledTextColor;

    @Getter
    private boolean disabled = false;

    @Getter
    private Supplier<List<Component>> tooltipSupplier = null;

    @Getter
    private final Duration TOOLTIP_DELAY = Duration.ofMillis(200);

    private long hoverStartTime = 0;
    private boolean wasHoveredLastFrame = false;

    public SimpleTextWidget(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
        this.backgroundColor = 0x80303030;
        this.textColor = 0xFFFFFFFF;
        this.disabledTextColor = 0xFF808080;
    }

    public SimpleTextWidget setBackgroundColor(int color) {
        this.backgroundColor = color;
        return this;
    }

    public SimpleTextWidget setTextColor(int color) {
        this.textColor = color;
        return this;
    }

    public SimpleTextWidget setDisabledTextColor(int color) {
        this.disabledTextColor = color;
        return this;
    }

    public SimpleTextWidget setDisabled(boolean disabled) {
        this.disabled = disabled;
        return this;
    }

    public SimpleTextWidget setTooltipSupplier(Supplier<List<Component>> supplier) {
        this.tooltipSupplier = supplier;
        return this;
    }


    public List<Component> getTooltipLines() {
        if (this.tooltipSupplier == null) {
            return null;
        }
        return this.tooltipSupplier.get();
    }

    public SimpleTextWidget setTooltipLines(List<Component> lines) {
        this.tooltipSupplier = () -> lines;
        return this;
    }

    public boolean shouldShowTooltip() {
        if (this.tooltipSupplier == null || !this.isHovered()) {
            return false;
        }

        long now = System.currentTimeMillis();

        if (!this.wasHoveredLastFrame) {
            this.hoverStartTime = now;
            this.wasHoveredLastFrame = true;
            return false;
        }

        long hoverDuration = now - this.hoverStartTime;
        return hoverDuration >= this.TOOLTIP_DELAY.toMillis();
    }

    @Override
    public boolean isHovered() {
        return super.isHovered();
    }

    @Override
    protected void renderWidget(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        if (!super.isHovered()) {
            this.wasHoveredLastFrame = false;
        }

        int bgColor = this.isHovered() ? this.brighten(this.backgroundColor) : this.backgroundColor;

        ctx.fill(
            this.getX(),
            this.getY(),
            this.getX() + this.width,
            this.getY() + this.height,
            bgColor
        );

        var textRenderer = Minecraft.getInstance().font;
        int currentTextColor = this.disabled ? this.disabledTextColor : this.textColor;

        ctx.drawString(
            textRenderer,
            this.getMessage(),
            this.getX() + 4,
            this.getY() + (this.height - textRenderer.lineHeight) / 2,
            currentTextColor
        );
    }

    private int brighten(int color) {
        int a = (color >> 24) & 0xFF;
        int r = Math.min(255, ((color >> 16) & 0xFF) + 20);
        int g = Math.min(255, ((color >> 8) & 0xFF) + 20);
        int b = Math.min(255, (color & 0xFF) + 20);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
        this.defaultButtonNarrationText(builder);
    }
}