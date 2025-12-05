package com.github.lutzluca.btrbz.widgets;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

// TODO add colapse/expand functionality
// This definitely needs some rework as everything is full of magic numbers (but lgtm)
public class ScrollableListWidget<T extends DraggableWidget> extends DraggableWidget {

    private final List<T> children = new ArrayList<>();
    private int maxVisibleChildren = 5;
    private int scrollOffset = 0;
    private int childHeight = 30;
    private int childSpacing = 2;
    private int titleBarHeight = 20;
    private int topMargin = 5;
    private int bottomPadding = 2;

    private BiConsumer<T, Integer> onChildClickCallback;
    private Consumer<T> onChildRemovedCallback;
    private Runnable onChildReorderedCallback;

    private T draggedChild = null;
    private int draggedChildOriginalIdx = -1;
    private int mouseDragStartY = 0;
    private boolean isDraggingChild = false;
    private boolean canDeleteEntries = true;

    private T tooltipPendingChild = null;
    private int tooltipMouseX = 0;
    private int tooltipMouseY = 0;

    public ScrollableListWidget(int x, int y, int width, int height, Component message, Screen parent) {
        super(x, y, width, height, message, parent);
        this.setRenderBackground(false);
        this.setRenderBorder(false);
        this.setDragThreshold(5);
    }

    public ScrollableListWidget<T> setMaxVisibleChildren(int max) {
        this.maxVisibleChildren = Math.max(1, max);
        this.clampScrollOffset();
        this.updateDimensions();
        return this;
    }

    public ScrollableListWidget<T> setChildHeight(int height) {
        this.childHeight = Math.max(10, height);
        this.updateDimensions();
        return this;
    }

    public ScrollableListWidget<T> setChildSpacing(int spacing) {
        this.childSpacing = Math.max(0, spacing);
        this.updateDimensions();
        return this;
    }

    public ScrollableListWidget<T> setTitleBarHeight(int height) {
        this.titleBarHeight = Math.max(10, height);
        this.updateDimensions();
        return this;
    }

    public ScrollableListWidget<T> setTopMargin(int margin) {
        this.topMargin = Math.max(0, margin);
        this.updateDimensions();
        return this;
    }

    public ScrollableListWidget<T> setBottomPadding(int padding) {
        this.bottomPadding = Math.max(0, padding);
        this.updateDimensions();
        return this;
    }

    public ScrollableListWidget<T> setcanDeleteEntries(boolean canDeleteEntries ) {
        this.canDeleteEntries = canDeleteEntries ;
        return this;
    }

    public ScrollableListWidget<T> onChildClick(BiConsumer<T, Integer> callback) {
        this.onChildClickCallback = callback;
        return this;
    }

    public ScrollableListWidget<T> onChildRemoved(Consumer<T> callback) {
        this.onChildRemovedCallback = callback;
        return this;
    }

    public ScrollableListWidget<T> onChildReordered(Runnable callback) {
        this.onChildReorderedCallback = callback;
        return this;
    }

    private void updateDimensions() {
        int contentHeight = this.maxVisibleChildren * this.childHeight;

        contentHeight += (this.maxVisibleChildren - 1) * this.childSpacing;

        this.height = this.titleBarHeight + this.topMargin + contentHeight + this.bottomPadding;
    }

    public void addChild(T child) {
        this.children.add(child);
        this.clampScrollOffset();
        this.updateDimensions();
    }

    public void removeChild(T child) {
        if (this.tooltipPendingChild == child) {
            this.tooltipPendingChild = null;
        }

        this.children.remove(child);
        this.clampScrollOffset();
        this.updateDimensions();
    }

    public void removeChild(int idx) {
        if (idx >= 0 && idx < this.children.size()) {
            T removed = this.children.remove(idx);

            if (this.tooltipPendingChild == removed) {
                this.tooltipPendingChild = null;
            }

            if (this.onChildRemovedCallback != null) {
                this.onChildRemovedCallback.accept(removed);
            }

            this.clampScrollOffset();
            this.updateDimensions();
        }
    }

    public void clearChildren() {
        this.children.clear();
        this.scrollOffset = 0;
        this.tooltipPendingChild = null;
        this.updateDimensions();
    }

    public List<T> getChildren() {
        return new ArrayList<>(this.children);
    }

    public int getChildCount() {
        return this.children.size();
    }

    public void scrollTo(int offset) {
        this.scrollOffset = offset;
        this.clampScrollOffset();
    }

    private void clampScrollOffset() {
        int maxScroll = Math.max(0, this.children.size() - this.maxVisibleChildren);
        this.scrollOffset = Math.max(0, Math.min(this.scrollOffset, maxScroll));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean drag) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.buttonInfo().button();

        if (this.isMouseOverTitleBar(mouseX, mouseY) && button == 0) {
            return super.mouseClicked(event, drag);
        }

        if (!this.isMouseOverContent(mouseX, mouseY)) {
            return super.mouseClicked(event, drag);
        }

        int childIdx = this.getChildAtPosition(mouseX, mouseY);

        if (childIdx >= 0) {
            T child = children.get(childIdx);

            if (this.canDeleteEntries && (button == 1 && Minecraft.getInstance().hasControlDown()) ) {
                this.removeChild(childIdx);
                return true;
            }

            if (button == 0) {
                this.draggedChild = child;
                this.draggedChildOriginalIdx = childIdx;
                this.mouseDragStartY = (int) mouseY;
                this.isDraggingChild = false;
                return true;
            }
        }

        return super.mouseClicked(event, drag);
    }


    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        int button = event.buttonInfo().button();
        double mouseX = event.x();
        double mouseY = event.y();

        if (button == 0 && this.draggedChild != null) {
            boolean wasDragging = this.isDraggingChild;

            this.draggedChild = null;
            this.draggedChildOriginalIdx = -1;
            this.isDraggingChild = false;

            if (!wasDragging && this.onChildClickCallback != null) {
                int childIdx = this.getChildAtPosition(mouseX, mouseY);
                if (childIdx >= 0) {
                    this.onChildClickCallback.accept(children.get(childIdx), childIdx);
                }
            }

            return true;
        }

        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        double mouseY = event.y();

        if (this.draggedChild != null) {
            int dragDistance = Math.abs((int) mouseY - this.mouseDragStartY);

            if (!this.isDraggingChild && dragDistance > this.getDragThreshold()) {
                this.isDraggingChild = true;
            }

            if (this.isDraggingChild) {
                int newIdx = this.calculateDropIndex((int) mouseY);

                if (newIdx != this.draggedChildOriginalIdx && newIdx >= 0 && newIdx < this.children.size()) {
                    this.children.remove(this.draggedChildOriginalIdx);
                    this.children.add(newIdx, this.draggedChild);
                    this.draggedChildOriginalIdx = newIdx;

                    if (this.onChildReorderedCallback != null) {
                        this.onChildReorderedCallback.run();
                    }
                }
            }

            return true;
        }

        return super.mouseDragged(event, deltaX, deltaY);
    }


    @Override
    public boolean mouseScrolled(
        double mouseX,
        double mouseY,
        double horizontalAmount,
        double verticalAmount
    ) {
        if (this.isMouseOver(mouseX, mouseY) && this.children.size() > this.maxVisibleChildren) {
            int scrollDelta = verticalAmount > 0 ? -1 : 1;
            this.scrollOffset += scrollDelta;
            this.clampScrollOffset();
            return true;
        }

        return false;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= this.getX() && mouseX < this.getX() + this.width && mouseY >= this.getY() && mouseY < this.getY() + this.height;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape() && this.draggedChild != null) {
            this.draggedChild = null;
            this.draggedChildOriginalIdx = -1;
            this.isDraggingChild = false;
            return true;
        }

        return super.keyPressed(event);
    }

    private boolean isMouseOverTitleBar(double mouseX, double mouseY) {
        return mouseX >= this.getX() && mouseX <= this.getX() + this.width && mouseY >= this.getY() && mouseY <= this.getY() + this.titleBarHeight;
    }

    private boolean isMouseOverContent(double mouseX, double mouseY) {
        int contentStartY = this.getY() + this.titleBarHeight + this.topMargin;
        int contentEndY = this.getY() + this.height - this.bottomPadding;

        return mouseX >= this.getX() && mouseX <= this.getX() + this.width && mouseY >= contentStartY && mouseY <= contentEndY;
    }

    private int getChildAtPosition(double mouseX, double mouseY) {
        if (!this.isMouseOverContent(mouseX, mouseY)) {
            return -1;
        }

        int startIndex = this.scrollOffset;
        int endIndex = Math.min(this.scrollOffset + this.maxVisibleChildren, this.children.size());

        for (int i = startIndex; i < endIndex; i++) {
            int childY = this.getChildY(i);

            if (mouseY >= childY && mouseY < childY + childHeight) {
                return i;
            }
        }

        return -1;
    }

    private int calculateDropIndex(int mouseY) {
        int contentStartY = this.getY() + this.titleBarHeight + this.topMargin;
        int relativeY = mouseY - contentStartY;
        int slotHeight = this.childHeight + this.childSpacing;
        int idx = this.scrollOffset + (relativeY / slotHeight);
        return Math.max(0, Math.min(idx, this.children.size() - 1));
    }

    private int getChildY(int idx) {
        int contentStartY = this.getY() + this.titleBarHeight + this.topMargin;
        int relativeIdx = idx - this.scrollOffset;
        return contentStartY + relativeIdx * (this.childHeight + this.childSpacing);
    }

    @Override
    protected void renderWidget(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        this.renderCompleteWidget(ctx, mouseX, mouseY, delta);
    }

    private void renderCompleteWidget(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        boolean isHovered = this.isMouseOver(mouseX, mouseY);
        boolean isDraggingWidget = this.isDragging();

        int bgColor = isDraggingWidget ? 0xD0404040 : (isHovered ? 0xD0353535 : 0xD0282828);
        ctx.fill(
            this.getX(),
            this.getY(),
            this.getX() + this.width,
            this.getY() + this.height,
            bgColor
        );

        int borderColor = isDraggingWidget ? 0xFFFF6B6B : (isHovered ? 0xFF606060 : 0xFF404040);
        ctx.submitOutline(this.getX(), this.getY(), width, height, borderColor);

        this.renderTitleBar(ctx, isDraggingWidget, isHovered);

        if (this.children.isEmpty()) {
            this.renderEmptyMessage(ctx);
        } else {
            this.renderChildren(ctx, mouseX, mouseY, delta);
        }

        if (this.needsScrollbar()) {
            this.renderScrollbar(ctx);
        }

        if (this.tooltipPendingChild != null && this.draggedChild == null) {

            if (this.children.contains(this.tooltipPendingChild)) {
                var tooltip = this.tooltipPendingChild.getTooltipLines();
                ctx.setComponentTooltipForNextFrame(
                    Minecraft.getInstance().font,
                    tooltip,
                    this.tooltipMouseX,
                    this.tooltipMouseY
                );
            } else {
                this.tooltipPendingChild = null;
            }
        }
    }

    private void renderTitleBar(GuiGraphics ctx, boolean isDraggingWidget, boolean isHovered) {
        int separatorY = this.getY() + this.titleBarHeight;
        ctx.fill(
            this.getX() + 1,
            separatorY,
            this.getX() + this.width - 1,
            separatorY + 1,
            0x40FFFFFF
        );

        var textRenderer = Minecraft.getInstance().font;
        int textColor = isDraggingWidget ? 0xFFFFAAAA : 0xFFFFFFFF;
        ctx.drawCenteredString(
            textRenderer,
            this.getMessage(),
            this.getX() + this.width / 2,
            this.getY() + (this.titleBarHeight - textRenderer.lineHeight) / 2,
            textColor
        );
    }

    private void renderChildren(GuiGraphics context, int mouseX, int mouseY, float delta) {
        int contentStartY = this.getY() + this.titleBarHeight + this.topMargin;
        int contentEndY = this.getY() + this.height - this.bottomPadding;

        context.enableScissor(
            this.getX() + 1,
            contentStartY,
            this.getX() + this.width - 1,
            contentEndY
        );

        this.tooltipPendingChild = null;

        int startIndex = this.scrollOffset;
        int endIndex = Math.min(this.scrollOffset + this.maxVisibleChildren, this.children.size());

        for (int i = startIndex; i < endIndex; i++) {
            T child = this.children.get(i);
            int childY = this.getChildY(i);

            child.setX(this.getX() + 3);
            child.setY(childY);
            child.setWidth(this.width - 6 - (this.needsScrollbar() ? 8 : 0));
            child.setHeight(this.childHeight);

            boolean isBeingDragged = (child == this.draggedChild && this.isDraggingChild);

            if (isBeingDragged) {
                context.fill(
                    child.getX() - 1,
                    child.getY() - 1,
                    child.getX() + child.getWidth() + 1,
                    child.getY() + child.getHeight() + 1,
                    0xA000AA00
                );
            }

            child.render(context, mouseX, mouseY, delta);

            if (child.shouldShowTooltip()) {
                this.tooltipPendingChild = child;
                this.tooltipMouseX = mouseX;
                this.tooltipMouseY = mouseY;
            }
        }

        context.disableScissor();
    }

    private boolean needsScrollbar() {
        return this.children.size() > this.maxVisibleChildren;
    }

    private void renderScrollbar(GuiGraphics ctx) {
        int contentStartY = this.getY() + this.titleBarHeight + this.topMargin;
        int contentHeight = this.height - this.titleBarHeight - this.topMargin - this.bottomPadding;

        int scrollbarX = this.getX() + this.width - 7;
        int scrollbarWidth = 4;

        ctx.fill(
            scrollbarX,
            contentStartY,
            scrollbarX + scrollbarWidth,
            contentStartY + contentHeight,
            0x30FFFFFF
        );

        int totalScrollable = this.children.size() - this.maxVisibleChildren;
        int thumbHeight = Math.max(
            20,
            (this.maxVisibleChildren * contentHeight) / this.children.size()
        );
        int maxThumbY = contentHeight - thumbHeight - 4;
        int thumbY =
            totalScrollable > 0 ? (int) ((this.scrollOffset / (float) totalScrollable) * maxThumbY)
                : 0;

        ctx.fill(
            scrollbarX,
            contentStartY + thumbY + 2,
            scrollbarX + scrollbarWidth,
            contentStartY + thumbY + thumbHeight + 2,
            0xC0909090
        );
    }

    private void renderEmptyMessage(GuiGraphics context) {
        int contentStartY = this.getY() + this.titleBarHeight + this.topMargin;
        int contentHeight = this.height - this.titleBarHeight - this.topMargin - this.bottomPadding;

        context.drawCenteredString(
            Minecraft.getInstance().font,
            Component.literal("Empty"),
            this.getX() + this.width / 2,
            contentStartY + contentHeight / 2 - 4,
            0xFF808080
        );
    }
}
