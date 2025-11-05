package com.github.lutzluca.btrbz.widgets;

import com.github.lutzluca.btrbz.utils.Position;
import java.time.Duration;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.Getter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

// NOTE: the tooltip stuff is obnoxious; kinda needed to roll my own, for the integration
// with the `ScrollableListWidget` using scissor so it would not be cut.
public class DraggableWidget extends ClickableWidget {

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
    private Supplier<List<Text>> tooltipSupplier = null;
    @Getter
    private Duration tooltipDelay = Duration.ofMillis(500);

    private long hoverStartTime = 0;
    private boolean wasHoveredLastFrame = false;

    private Consumer<DraggableWidget> onClickCallback;
    private BiConsumer<DraggableWidget, Position> onDragEndCallback;

    public DraggableWidget(int x, int y, int width, int height, Text message, Screen parentScreen) {
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

    public DraggableWidget setTooltipSupplier(Supplier<List<Text>> supplier) {
        this.tooltipSupplier = supplier;
        return this;
    }

    public DraggableWidget setTooltipShowDelay(Duration delay) {
        this.tooltipDelay = delay;
        return this;
    }

    public Position getPosition() {
        return new Position(this.getX(), this.getY());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && this.isHovered()) {
            this.mousePressed = true;
            this.dragging = false;
            this.dragStartX = (int) mouseX;
            this.dragStartY = (int) mouseY;
            this.widgetStartX = this.getX();
            this.widgetStartY = this.getY();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && this.mousePressed) {
            boolean wasDragging = this.dragging;
            this.mousePressed = false;
            this.dragging = false;

            if (!wasDragging && this.isHovered() && this.onClickCallback != null) {
                this.onClickCallback.accept(this);
            } else if (wasDragging && this.onDragEndCallback != null) {
                this.onDragEndCallback.accept(this, this.getPosition());
            }

            return true;
        }

        return false;
    }

    @Override
    public boolean mouseDragged(
        double mouseX,
        double mouseY,
        int button,
        double deltaX,
        double deltaY
    ) {
        if (!this.mousePressed) {
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && this.mousePressed) {
            this.cancelDrag();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
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

    public List<Text> getTooltipLines() {
        System.out.println("getTooltipLines");
        if (this.tooltipSupplier == null) {
            return null;
        }
        return this.tooltipSupplier.get();
    }

    public DraggableWidget setTooltipLines(List<Text> lines) {
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
        return hoverDuration >= this.tooltipDelay.toMillis();
    }

    @Override
    protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
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

    protected void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int color = this.dragging ? 0x80FF6B6B : (this.isHovered() ? 0x80A0A0A0 : 0x80404040);

        ctx.fill(
            this.getX(),
            this.getY(),
            this.getX() + this.width,
            this.getY() + this.height,
            color
        );
    }

    protected void renderBorder(DrawContext ctx, int mouseX, int mouseY, float delta) {
        var borderColor = this.dragging ? 0xFFFF0000 : 0xFFFFFFFF;
        ctx.drawBorder(this.getX(), this.getY(), this.width, this.height, borderColor);
    }

    protected void renderContent(DrawContext context, int mouseX, int mouseY, float delta) {
        var textRenderer = MinecraftClient.getInstance().textRenderer;

        context.drawCenteredTextWithShadow(
            textRenderer,
            this.getMessage(),
            this.getX() + this.width / 2,
            this.getY() + (this.height - textRenderer.fontHeight) / 2,
            0xFFFFFFFF
        );
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        this.appendDefaultNarrations(builder);
    }
}