package com.github.lutzluca.btrbz.widgets.base;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.function.BiConsumer;

import com.github.lutzluca.btrbz.utils.Position;

import lombok.Getter;

public abstract class DraggableWidget extends AbstractWidget {
    private BiConsumer<DraggableWidget, Position> onDragEndCallback;

    @Getter
    protected boolean isDragging = false;

    protected boolean isPressed = false;
    protected double dragStartX;
    protected double dragStartY;
    protected int initialX;
    protected int initialY;

    protected long dragStartTime;

    protected int dragThreshold = 5;
    protected long dragTimeThreshold = 200;
    protected boolean draggable = true;

    protected TooltipProvider tooltipProvider;

    public DraggableWidget(
        int defaultX, int defaultY,
        int width, int height
    ) {
        super(defaultX, defaultY, width, height);
    }

    @Override
    protected void renderWidget(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        RenderContext ctx = new RenderContext(true, false);
        this.renderWidget(graphics, mouseX, mouseY, delta, ctx);
    }

    public void renderWidget(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, RenderContext ctx) {
        this.renderContent(graphics, mouseX, mouseY, delta, ctx);

        boolean canShowTooltip = this.isMouseOver(this.client.mouseHandler.xpos(), this.client.mouseHandler.ypos()) && !this.isDragging && ctx.canShowTooltips();

        if (this.tooltipProvider != null && !canShowTooltip) {
            this.tooltipProvider.resetHover();
        }

        if (this.tooltipProvider != null && canShowTooltip) {
            this.tooltipProvider.renderTooltip(graphics, mouseX, mouseY);
        }
    }

    protected abstract void renderContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, RenderContext ctx);

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && this.draggable) {
            this.isPressed = true;
            this.dragStartX = event.x();
            this.dragStartY = event.y();
            this.initialX = this.x;
            this.initialY = this.y;
            this.dragStartTime = System.currentTimeMillis();
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (event.button() != 0 || !this.isPressed) {
            return false;
        }

        double distanceFromStart = Math.sqrt(
            Math.pow(event.x() - this.dragStartX, 2) +
            Math.pow(event.y() - this.dragStartY, 2)
        );
        long dragDuration = System.currentTimeMillis() - this.dragStartTime;

        if (!this.isDragging && (distanceFromStart >= this.dragThreshold || dragDuration >= this.dragTimeThreshold)) {
            this.isDragging = true;
            this.onDragStart();
        }

        if (this.isDragging) {
            int newX = this.initialX + (int)(event.x() - this.dragStartX);
            int newY = this.initialY + (int)(event.y() - this.dragStartY);

            this.setX(this.constrainX(newX));
            this.setY(this.constrainY(newY));
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() != 0) {
            return false;
        }

        this.isPressed = false;
        if (this.isDragging) {
            this.isDragging = false;
            this.dragStartX = 0;
            this.dragStartY = 0;
            this.initialX = 0;
            this.initialY = 0;
            this.onDragEnd();
        }

        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.isDragging && event.isEscape()) {
            this.cancelDrag();
            return true;
        }

        return false;
    }

    protected void cancelDrag() {
        this.isPressed = false;

        if (this.isDragging) {
            this.isDragging = false;
            this.setX(this.initialX);
            this.setY(this.initialY);
        }
    }

    protected int constrainX(int x) {
        int screenWidth = this.client.getWindow().getGuiScaledWidth();
        return Math.max(0, Math.min(x, screenWidth - this.width));
    }

    protected int constrainY(int y) {
        int screenHeight = this.client.getWindow().getGuiScaledHeight();
        return Math.max(0, Math.min(y, screenHeight - this.height));
    }

    public DraggableWidget setDragThreshold(int pixels) {
        this.dragThreshold = pixels;
        return this;
    }

    public DraggableWidget setDragTimeThreshold(long millis) {
        this.dragTimeThreshold = millis;
        return this;
    }

    public DraggableWidget setTooltip(TooltipProvider provider) {
        this.tooltipProvider = provider;
        return this;
    }

    public DraggableWidget setDraggable(boolean draggable) {
        this.draggable = draggable;
        return this;
    }

    public boolean containsPoint(double x, double y) {
        return x >= this.x &&
               x < this.x + this.width &&
               y >= this.y &&
               y < this.y + this.height;
    }

    protected void onDragStart() {}

    public DraggableWidget onDragEnd(BiConsumer<DraggableWidget, Position> callback) {
        this.onDragEndCallback = callback;
        return this;
    }

    protected void onDragEnd() {
        if (this.onDragEndCallback != null) {
            this.onDragEndCallback.accept(this, new Position(this.x, this.y));
        }
    }
}
