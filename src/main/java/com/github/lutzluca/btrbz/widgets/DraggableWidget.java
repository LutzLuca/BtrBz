package com.github.lutzluca.btrbz.widgets;

import com.github.lutzluca.btrbz.utils.Position;
import java.time.Duration;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

// NOTE: the tooltip stuff is obnoxious; kinda needed to roll my own, for the integration
// with the `ScrollableListWidget` using scissor so it would not be cut.
public class DraggableWidget extends AbstractWidget {

    @Getter
    private final Screen parentScreen;

    @Getter
    private boolean mousePressed = false;

    @Getter
    private boolean dragging = false;

    private int dragStartX;
    private int dragStartY;
    private int widgetStartX;
    private int widgetStartY;

    @Getter
    private int dragThreshold = 3;

    private boolean renderBackground = true;
    private boolean renderBorder = true;

    @Getter
    private Supplier<List<Component>> tooltipSupplier = null;
    @Getter
    private final Duration TOOLTIP_DELAY = Duration.ofMillis(200);

    private long hoverStartTime = 0;
    private boolean wasHoveredLastFrame = false;

    private Consumer<DraggableWidget> onClickCallback;
    private BiConsumer<DraggableWidget, Position> onDragEndCallback;

    public DraggableWidget(int x, int y, int width, int height, Component message, Screen parentScreen) {
        super(x, y, width, height, message);
        this.parentScreen = parentScreen;
    }

    public DraggableWidget onClick(Consumer<DraggableWidget> callback) {
        this.onClickCallback = callback;
        return this;
    }

    public DraggableWidget onDragEnd(BiConsumer<DraggableWidget, Position> callback) {
        this.onDragEndCallback = callback;
        return this;
    }

    public DraggableWidget setRenderBackground(boolean shouldRender) {
        this.renderBackground = shouldRender;
        return this;
    }

    public DraggableWidget setRenderBorder(boolean shouldRender) {
        this.renderBorder = shouldRender;
        return this;
    }

    public DraggableWidget setDragThreshold(int threshold) {
        this.dragThreshold = threshold;
        return this;
    }

    public DraggableWidget setTooltipSupplier(Supplier<List<Component>> supplier) {
        this.tooltipSupplier = supplier;
        return this;
    }

    public Position getPosition() {
        return new Position(this.getX(), this.getY());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean drag) {
        if (!this.isActive()) {
            return false;
        }

        int button = event.buttonInfo().button();
        double mouseX = event.x();
        double mouseY = event.y();

        if (button == 0 && this.isMouseOver(mouseX, mouseY)) {
            this.mousePressed = true;
            this.dragging = false;
            this.dragStartX = (int) mouseX;
            this.dragStartY = (int) mouseY;
            this.widgetStartX = this.getX();
            this.widgetStartY = this.getY();
            return true;
        }

        return super.mouseClicked(event, drag);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (!this.isValidClickButton(event.buttonInfo())) {
            return false;
        }

        int button = event.buttonInfo().button();
        double mouseX = event.x();
        double mouseY = event.y();

        if (button == 0 && this.mousePressed) {
            boolean wasDragging = this.dragging;
            this.mousePressed = false;
            this.dragging = false;

            if (!wasDragging && this.isMouseOver(mouseX, mouseY) && this.onClickCallback != null) {
                this.onClickCallback.accept(this);
            } else if (wasDragging && this.onDragEndCallback != null) {
                this.onDragEndCallback.accept(this, this.getPosition());
            }

            return true;
        }

        return false;
    }


    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (!this.isValidClickButton(event.buttonInfo())) {
            return false;
        }

        int button = event.buttonInfo().button();
        double mouseX = event.x();
        double mouseY = event.y();

        if (button != 0 || !this.mousePressed) {
            return false;
        }

        int totalDragDistance = Math.abs((int) mouseX - this.dragStartX) + Math.abs((int) mouseY - this.dragStartY);

        if (!this.dragging && totalDragDistance > dragThreshold) {
            this.dragging = true;
        }

        if (this.dragging) {
            int proposedX = this.widgetStartX + ((int) mouseX - this.dragStartX);
            int proposedY = this.widgetStartY + ((int) mouseY - this.dragStartY);

            var newPosition = this.clipToScreenBounds(new Position(proposedX, proposedY));

            this.setX(newPosition.x());
            this.setY(newPosition.y());
        }

        return true;
    }


    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape() && this.mousePressed) {
            this.cancelDrag();
            return true;
        }

        return super.keyPressed(event);
    }



    private void cancelDrag() {
        this.setX(this.widgetStartX);
        this.setY(this.widgetStartY);

        this.mousePressed = false;
        this.dragging = false;
    }

    private Position clipToScreenBounds(Position pos) {
        int x = Math.max(0, Math.min(pos.x(), parentScreen.width - this.width));
        int y = Math.max(0, Math.min(pos.y(), parentScreen.height - this.height));
        return new Position(x, y);
    }

    public List<Component> getTooltipLines() {
        if (this.tooltipSupplier == null) {
            return null;
        }
        return this.tooltipSupplier.get();
    }

    public DraggableWidget setTooltipLines(List<Component> lines) {
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
    protected void renderWidget(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        if (!this.isHovered()) {
            this.wasHoveredLastFrame = false;
        }

        if (this.renderBackground) {
            renderBackground(ctx, mouseX, mouseY, delta);
        }
        if (this.renderBorder) {
            renderBorder(ctx, mouseX, mouseY, delta);
        }
        renderContent(ctx, mouseX, mouseY, delta);
    }

    protected void renderBackground(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        int color = this.dragging ? 0x80FF6B6B : (this.isHovered() ? 0x80A0A0A0 : 0x80404040);

        ctx.fill(
            this.getX(),
            this.getY(),
            this.getX() + this.width,
            this.getY() + this.height,
            color
        );
    }

    protected void renderBorder(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        var borderColor = this.dragging ? 0xFFFF0000 : 0xFFFFFFFF;
        ctx.submitOutline(this.getX(), this.getY(), this.width, this.height, borderColor);
    }

    protected void renderContent(GuiGraphics context, int mouseX, int mouseY, float delta) {
        var textRenderer = Minecraft.getInstance().font;

        context.drawCenteredString(
            textRenderer,
            this.getMessage(),
            this.getX() + this.width / 2,
            this.getY() + (this.height - textRenderer.lineHeight) / 2,
            0xFFFFFFFF
        );
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
        this.defaultButtonNarrationText(builder);
    }
}