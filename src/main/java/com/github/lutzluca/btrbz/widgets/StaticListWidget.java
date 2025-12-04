package com.github.lutzluca.btrbz.widgets;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class StaticListWidget<T extends AbstractWidget> extends DraggableWidget {

    private static final int CHILD_HEIGHT = 14;
    private static final int CHILD_SPACING = 1;
    private static final int TITLE_BAR_HEIGHT = 20;
    private static final int TOP_MARGIN = 5;
    private static final int BOTTOM_PADDING = 2;
    private final List<T> children = new ArrayList<>();
    private int maxVisibleChildren = 5;
    private int scrollOffset = 0;
    private BiConsumer<T, Integer> onChildClickCallback;
    private T clickedChild = null;

    public StaticListWidget(int x, int y, int width, int height, Component message, Screen parent) {
        super(x, y, width, height, message, parent);
        this.setRenderBackground(false);
        this.setRenderBorder(false);
        this.setDragThreshold(5);
        this.updateDimensions();
    }

    public StaticListWidget<T> setMaxVisibleChildren(int max) {
        this.maxVisibleChildren = Math.max(1, max);
        this.clampScrollOffset();
        this.updateDimensions();
        return this;
    }

    public StaticListWidget<T> onChildClick(BiConsumer<T, Integer> callback) {
        this.onChildClickCallback = callback;
        return this;
    }

    public void rebuildEntries(List<T> newEntries) {
        this.children.clear();
        this.children.addAll(newEntries);
        this.scrollOffset = 0;
        this.clampScrollOffset();
        this.updateDimensions();
    }

    private void updateDimensions() {
        int contentHeight = this.maxVisibleChildren * CHILD_HEIGHT;
        contentHeight += (this.maxVisibleChildren - 1) * CHILD_SPACING;
        this.height = TITLE_BAR_HEIGHT + TOP_MARGIN + contentHeight + BOTTOM_PADDING;
    }

    public List<T> getChildren() {
        return new ArrayList<>(this.children);
    }

    public int getChildCount() {
        return this.children.size();
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

        if (childIdx >= 0 && button == 0) {
            this.clickedChild = children.get(childIdx);
            return true;
        }

        return super.mouseClicked(event, drag);
    }


    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        int button = event.buttonInfo().button();
        double mouseX = event.x();
        double mouseY = event.y();

        if (button == 0 && this.clickedChild != null) {
            T clicked = this.clickedChild;
            this.clickedChild = null;

            int childIdx = this.getChildAtPosition(mouseX, mouseY);
            if (childIdx >= 0 && this.children.get(childIdx) == clicked && this.onChildClickCallback != null) {
                this.onChildClickCallback.accept(clicked, childIdx);
            }

            return true;
        }

        return super.mouseReleased(event);
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
        return mouseX >= this.getX() &&
            mouseX < this.getX() + this.width &&
            mouseY >= this.getY() &&
            mouseY < this.getY() + this.height;
    }

    private boolean isMouseOverTitleBar(double mouseX, double mouseY) {
        return mouseX >= this.getX() &&
            mouseX <= this.getX() + this.width &&
            mouseY >= this.getY() &&
            mouseY <= this.getY() + TITLE_BAR_HEIGHT;
    }

    private boolean isMouseOverContent(double mouseX, double mouseY) {
        int contentStartY = this.getY() + TITLE_BAR_HEIGHT + TOP_MARGIN;
        int contentEndY = this.getY() + this.height - BOTTOM_PADDING;

        return mouseX >= this.getX() &&
            mouseX <= this.getX() + this.width &&
            mouseY >= contentStartY &&
            mouseY <= contentEndY;
    }

    private int getChildAtPosition(double mouseX, double mouseY) {
        if (!this.isMouseOverContent(mouseX, mouseY)) {
            return -1;
        }

        int startIndex = this.scrollOffset;
        int endIndex = Math.min(this.scrollOffset + this.maxVisibleChildren, this.children.size());

        for (int i = startIndex; i < endIndex; i++) {
            int childY = this.getChildY(i);

            if (mouseY >= childY && mouseY < childY + CHILD_HEIGHT) {
                return i;
            }
        }

        return -1;
    }

    private int getChildY(int idx) {
        int contentStartY = this.getY() + TITLE_BAR_HEIGHT + TOP_MARGIN;
        int relativeIdx = idx - this.scrollOffset;
        return contentStartY + relativeIdx * (CHILD_HEIGHT + CHILD_SPACING);
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
    }

    private void renderTitleBar(GuiGraphics ctx, boolean isDraggingWidget, boolean isHovered) {
        int separatorY = this.getY() + TITLE_BAR_HEIGHT;
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
            this.getY() + (TITLE_BAR_HEIGHT - textRenderer.lineHeight) / 2,
            textColor
        );
    }

    private void renderChildren(GuiGraphics context, int mouseX, int mouseY, float delta) {
        int contentStartY = this.getY() + TITLE_BAR_HEIGHT + TOP_MARGIN;
        int contentEndY = this.getY() + this.height - BOTTOM_PADDING;

        context.enableScissor(
            this.getX() + 1,
            contentStartY,
            this.getX() + this.width - 1,
            contentEndY
        );

        T tooltipPendingChild = null;
        int tooltipMouseX = 0;
        int tooltipMouseY = 0;

        int startIndex = this.scrollOffset;
        int endIndex = Math.min(this.scrollOffset + this.maxVisibleChildren, this.children.size());

        for (int i = startIndex; i < endIndex; i++) {
            T child = this.children.get(i);
            int childY = this.getChildY(i);

            child.setX(this.getX() + 3);
            child.setY(childY);
            child.setWidth(this.width - 6 - (this.needsScrollbar() ? 8 : 0));
            child.setHeight(CHILD_HEIGHT);

            child.render(context, mouseX, mouseY, delta);

            if (child instanceof SimpleTextWidget stw && stw.shouldShowTooltip()) {
                tooltipPendingChild = child;
                tooltipMouseX = mouseX;
                tooltipMouseY = mouseY;
            }
        }

        context.disableScissor();

        if (tooltipPendingChild instanceof SimpleTextWidget stw) {
            var tooltip = stw.getTooltipLines();
            if (tooltip != null) {
                context.setComponentTooltipForNextFrame(
                    Minecraft.getInstance().font,
                    tooltip,
                    tooltipMouseX,
                    tooltipMouseY
                );
            }
        }
    }

    private boolean needsScrollbar() {
        return this.children.size() > this.maxVisibleChildren;
    }

    private void renderScrollbar(GuiGraphics ctx) {
        int contentStartY = this.getY() + TITLE_BAR_HEIGHT + TOP_MARGIN;
        int contentHeight = this.height - TITLE_BAR_HEIGHT - TOP_MARGIN - BOTTOM_PADDING;

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
        int thumbY = totalScrollable > 0
            ? (int) ((this.scrollOffset / (float) totalScrollable) * maxThumbY)
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
        int contentStartY = this.getY() + TITLE_BAR_HEIGHT + TOP_MARGIN;
        int contentHeight = this.height - TITLE_BAR_HEIGHT - TOP_MARGIN - BOTTOM_PADDING;

        context.drawCenteredString(
            Minecraft.getInstance().font,
            Component.literal("Empty"),
            this.getX() + this.width / 2,
            contentStartY + contentHeight / 2 - 4,
            0x808080
        );
    }
}