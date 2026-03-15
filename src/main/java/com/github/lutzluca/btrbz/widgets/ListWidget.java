package com.github.lutzluca.btrbz.widgets;

import com.github.lutzluca.btrbz.widgets.base.DraggableWidget;
import com.github.lutzluca.btrbz.widgets.base.RenderContext;
import com.github.lutzluca.btrbz.widgets.core.TooltipRenderer;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;


public class ListWidget extends DraggableWidget {
    private final List<Renderable> items = new ArrayList<>();
    private final Component title;

    private int itemHeight = 14;
    private int itemSpacing = 1;
    private int scrollSpeed = 10;
    private int maxVisibleItems = -1;

    private int scrollOffset = 0;
    private int maxScrollOffset = 0;
    private boolean draggingScrollbar = false;
    private int scrollbarDragStartY = 0;
    private int scrollbarStartOffset = 0;

    private int hoveredItemIndex = -1;
    private int lastHoveredItemIndex = -1;

    private static final int TOOLTIP_DELAY_TICKS = 10;
    private int tooltipHoverTicks = 0;
    private List<Component> cachedTooltip;

    private boolean draggingItem = false;
    private int draggedItemIndex = -1;
    private Renderable draggedItem = null;
    private int trackingClickItemIndex = -1;
    private int dragInsertIndex = -1;
    private double dragOffsetY = 0;
    private double dragStartMouseX = 0;
    private double dragStartMouseY = 0;
    private static final int ITEM_DRAG_THRESHOLD = 3;
    private static final int AUTO_SCROLL_MARGIN = 20;
    private static final float AUTO_SCROLL_SPEED = 20.0f;

    private enum AutoScrollDirection { NONE, UP, DOWN }
    private AutoScrollDirection autoScrollDirection = AutoScrollDirection.NONE;

    private TriConsumer<ListWidget, Integer, Integer> itemReorderedCallback;
    private TriConsumer<ListWidget, Integer, Integer> onHoverChangeCallback;
    private TriConsumer<ListWidget, Renderable, Integer> itemClickCallback;
    private TriConsumer<ListWidget, Renderable, Integer> itemRemovedCallback;

    private boolean removable = true;
    private boolean reorderable = true;

    private static final int COLOR_BACKGROUND = 0xC0101010;
    private static final int COLOR_INSERT_INDICATOR = 0xFFFFFFFF;
    private static final int COLOR_TITLE_BG = 0x80000000;
    private static final int COLOR_NON_ACTIVE_TITLE_BG = 0x40000000;
    private static final int COLOR_SCROLLBAR_TRACK = 0x80000000;
    private static final int COLOR_SCROLLBAR_THUMB = 0xFF555555;
    private static final int COLOR_SCROLLBAR_THUMB_HOVER = 0xFF777777;
    private static final int COLOR_SCROLLBAR_THUMB_DRAG = 0xFF888888;
    private static final int COLOR_BORDER = 0xFF353535;
    private static final int COLOR_TITLE_TEXT = 0xFFDDDDDD;

    // Layout constants
    private static final int TITLE_BAR_HEIGHT = 20;
    private static final int TITLE_SEPARATOR_HEIGHT = 1;
    private static final int CONTENT_START_Y = TITLE_BAR_HEIGHT + TITLE_SEPARATOR_HEIGHT;
    private static final int SCROLLBAR_WIDTH = 10;
    private static final int CONTENT_MARGIN_X = 2;
    private static final int CONTENT_PADDING_X = 4;
    private static final int SCROLLBAR_MIN_THUMB_HEIGHT = 20;

    @FunctionalInterface
    public interface TriConsumer<T, U, V> {
        void accept(T t, U u, V v);
    }

    public ListWidget(
        int initialX, int initialY,
        int width, int height,
        String title
    ) {
        super(initialX, initialY, width, height);
        this.title = Component.literal(title);
    }

    public void setItems(List<Renderable> newItems) {
        this.items.clear();
        this.items.addAll(newItems);
        this.updateScrollLimits();

    }

    public void addItem(Renderable item) {
        this.items.add(item);
        this.updateScrollLimits();

    }
    public List<Renderable> getItems() {
        return new ArrayList<>(this.items);
    }

    public int size() {
        return this.items.size();
    }

    public Optional<Renderable> getItem(int index) {
        if (index >= 0 && index < this.items.size()) {
            return Optional.of(Objects.requireNonNull(this.items.get(index)));
        }
        return Optional.empty();
    }

    public void clear() {
        this.items.clear();
        this.scrollOffset = 0;
        this.updateScrollLimits();

    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float delta, RenderContext ctx) {
        if (this.draggingItem && this.autoScrollDirection != AutoScrollDirection.NONE) {
            float scrollAmount = AUTO_SCROLL_SPEED * delta;
            if (this.autoScrollDirection == AutoScrollDirection.UP) {
                this.scrollOffset = Math.max(0, this.scrollOffset - (int)scrollAmount);
            } else {
                this.scrollOffset = Math.min(this.maxScrollOffset, this.scrollOffset + (int)scrollAmount);
            }
        }

        int x = this.getX();
        int y = this.getY();

        // Background - outer border
        graphics.fill(x - 1, y - 1, x + this.width + 1, y, COLOR_BORDER);
        graphics.fill(x - 1, y + this.height, x + this.width + 1, y + this.height + 1, COLOR_BORDER);
        graphics.fill(x - 1, y, x, y + this.height, COLOR_BORDER);
        graphics.fill(x + this.width, y, x + this.width + 1, y + this.height, COLOR_BORDER);

        graphics.fill(x, y, x + this.width, y + this.height, COLOR_BACKGROUND);

        // Title bar
        graphics.fill(x, y, x + this.width, y + TITLE_BAR_HEIGHT, ctx.isTopWidget() ? COLOR_TITLE_BG : COLOR_NON_ACTIVE_TITLE_BG);
        graphics.fill(x, y + TITLE_BAR_HEIGHT, x + this.width, y + CONTENT_START_Y, COLOR_BORDER); // Title separator

        graphics.drawString(
            this.client.font,
            this.title,
            x + 4, y + 6,
            COLOR_TITLE_TEXT,
            true
        );

        // Content area
        int contentX = x + CONTENT_MARGIN_X;
        int contentY = y + CONTENT_START_Y;
        int contentWidth = this.width - CONTENT_PADDING_X - (this.needsScrollbar() ? SCROLLBAR_WIDTH : 0);
        int contentHeight = this.height - CONTENT_START_Y;

        // Calculate hovered item (but not when another widget is being dragged or not top widget)
        boolean canProcessHover = ctx.canProcessHover();
        if (!canProcessHover) {
            int previousHovered = this.hoveredItemIndex;
            this.hoveredItemIndex = -1;
            this.tooltipHoverTicks = 0;
            if (previousHovered >= 0 && this.onHoverChangeCallback != null) {
                this.onHoverChangeCallback.accept(this, previousHovered, -1);
            }
        } else {
            this.hoveredItemIndex = this.getItemIndexAtPosition(mouseX, mouseY);

            // Track hover time for lazy tooltip evaluation
            if (this.hoveredItemIndex != this.lastHoveredItemIndex) {
                if (this.onHoverChangeCallback != null && canProcessHover) {
                    this.onHoverChangeCallback.accept(this, this.lastHoveredItemIndex, this.hoveredItemIndex);
                }
                this.tooltipHoverTicks = 0;
                this.cachedTooltip = null; // Clear cached tooltip on item change
            } else if (this.hoveredItemIndex >= 0) {
                this.tooltipHoverTicks++;
            }

            this.lastHoveredItemIndex = this.hoveredItemIndex;
        }

        // Enable scissor for content area | clip to content bounds
        graphics.enableScissor(contentX, contentY, contentX + contentWidth, contentY + contentHeight);

        // Check if we need to render first insertion indicator (at content top boundary)
        if (this.draggingItem && this.dragInsertIndex == 0) {
            // Render at contentY (the top boundary of scissor region)
            graphics.fill(contentX, contentY, contentX + contentWidth, contentY + 2, COLOR_INSERT_INDICATOR);
        }

        this.renderItems(graphics, contentX, contentY, contentWidth, mouseX, mouseY, delta);
        graphics.disableScissor();

        // Scrollbar
        if (this.needsScrollbar()) {
            this.renderScrollbar(graphics, x + this.width - 10, contentY, contentHeight, mouseX, mouseY);
        }

        // Render tooltip for hovered item (after scissor disabled so it's not clipped)
        // Guard: Don't show tooltips when dragging the entire list widget or when another widget is dragging
        boolean canShowTooltips = ctx.canShowTooltips();
        boolean hoverDelayMet = this.tooltipHoverTicks >= TOOLTIP_DELAY_TICKS;
        boolean shouldRenderTooltip = this.hoveredItemIndex >= 0 && this.hoveredItemIndex < this.items.size() && !this.isDragging() && canShowTooltips && hoverDelayMet;

        if (shouldRenderTooltip) {
            Renderable hoveredItem = this.items.get(this.hoveredItemIndex);

            if (this.cachedTooltip == null) {
                this.cachedTooltip = hoveredItem.getTooltip();
            }

            if (this.cachedTooltip != null && !this.cachedTooltip.isEmpty()) {
                TooltipRenderer.renderDeferred(graphics, this.cachedTooltip, mouseX, mouseY);
            }
        }
    }

    private void renderItems(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY, float delta) {
        int visibleHeight = this.height - CONTENT_START_Y;
        int firstVisible = Math.max(0, this.scrollOffset / (this.itemHeight + this.itemSpacing));
        int lastVisible = Math.min(this.items.size() - 1, (this.scrollOffset + visibleHeight) / (this.itemHeight + this.itemSpacing) + 1);

        for (int idx = firstVisible; idx <= lastVisible; idx++) {
            if (idx >= this.items.size()) {
                break;
            }

            Renderable item = this.items.get(idx);
            int itemY = y + idx * (this.itemHeight + this.itemSpacing) - this.scrollOffset;

            // Skip if this is the dragged item (render it separately)
            if (this.draggingItem && idx == this.draggedItemIndex) {
                continue;
            }

            // Render insertion indicator after this item (which is before the next item)
            if (this.draggingItem && this.dragInsertIndex == idx + 1) {
                graphics.fill(
                    x, itemY + this.itemHeight,
                    x + width, itemY + this.itemHeight + 2,
                    COLOR_INSERT_INDICATOR
                );
            }

            // Render item
            boolean hovered = idx == this.hoveredItemIndex && !this.draggingItem;
            item.render(graphics, x, itemY, width, this.itemHeight, mouseX, mouseY, delta, hovered);
        }

        // Render dragged item at mouse position
        if (this.draggingItem && this.draggedItem != null) {
            int draggedY = (int)(mouseY - this.dragOffsetY);
            // Semi-transparent background as dragging indicator
            graphics.fill(x, draggedY, x + width, draggedY + this.itemHeight, 0x40FFFFFF);
            this.draggedItem.render(graphics, x, draggedY, width, this.itemHeight, mouseX, mouseY, delta, false);
        }
    }

    private void renderScrollbar(GuiGraphics graphics, int x, int y, int height, int mouseX, int mouseY) {
        // Track
        graphics.fill(x, y, x + 8, y + height, COLOR_SCROLLBAR_TRACK);

        // Thumb
        int thumbHeight = this.calculateScrollbarThumbHeight(height);
        int thumbY = this.calculateScrollbarThumbY(y, height, thumbHeight);

        int thumbColor;
        if (this.draggingScrollbar) {
            thumbColor = COLOR_SCROLLBAR_THUMB_DRAG;
        } else if (this.isMouseOverScrollbar(mouseX, mouseY, x, thumbY, thumbHeight)) {
            thumbColor = COLOR_SCROLLBAR_THUMB_HOVER;
        } else {
            thumbColor = COLOR_SCROLLBAR_THUMB;
        }

        graphics.fill(x + 1, thumbY, x + 7, thumbY + thumbHeight, thumbColor);
    }

    private void updateScrollLimits() {
        int totalHeight = this.items.size() * (this.itemHeight + this.itemSpacing);
        int visibleHeight = this.height - CONTENT_START_Y;
        this.maxScrollOffset = Math.max(0, totalHeight - visibleHeight);
        this.scrollOffset = Math.min(this.scrollOffset, this.maxScrollOffset);
    }

    private boolean needsScrollbar() {
        int totalHeight = this.items.size() * (this.itemHeight + this.itemSpacing);
        int visibleHeight = this.height - CONTENT_START_Y;
        return totalHeight > visibleHeight;
    }

    private int calculateScrollbarThumbHeight(int trackHeight) {
        int totalHeight = this.items.size() * (this.itemHeight + this.itemSpacing);
        int visibleHeight = this.height - CONTENT_START_Y;
        return Math.max(SCROLLBAR_MIN_THUMB_HEIGHT, (int)((double)visibleHeight / totalHeight * trackHeight));
    }

    private int calculateScrollbarThumbY(int trackY, int trackHeight, int thumbHeight) {
        if (this.maxScrollOffset == 0) return trackY;
        double scrollPercent = (double)this.scrollOffset / this.maxScrollOffset;
        return trackY + (int)(scrollPercent * (trackHeight - thumbHeight));
    }

    private boolean isMouseOverScrollbar(double mouseX, double mouseY, int scrollbarX, int thumbY, int thumbHeight) {
        return mouseX >= scrollbarX && mouseX < scrollbarX + 8 &&
               mouseY >= thumbY && mouseY < thumbY + thumbHeight;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (event.button() != 0) {
            return false;
        }

        // Handle scrollbar dragging first
        if (this.draggingScrollbar) {
            int trackHeight = this.height - 21;
            int thumbHeight = this.calculateScrollbarThumbHeight(trackHeight);
            int dragDelta = (int)(event.y() - this.scrollbarDragStartY);
            double scrollDelta = (double)dragDelta / (trackHeight - thumbHeight) * this.maxScrollOffset;
            this.scrollOffset = Math.max(0, Math.min(this.scrollbarStartOffset + (int)scrollDelta, this.maxScrollOffset));
            return true;
        }

        // Handle item dragging
        if (this.reorderable && this.draggedItemIndex >= 0) {
            // Check if drag threshold has been exceeded
            if (!this.draggingItem) {
                double distance = Math.sqrt(Math.pow(event.x() - this.dragStartMouseX, 2) + Math.pow(event.y() - this.dragStartMouseY, 2));
                if (distance < ITEM_DRAG_THRESHOLD) {
                    return true;
                }

                this.draggingItem = true;
            }

            // Calculate insertion index based on mouse Y
            int contentY = this.getY() + CONTENT_START_Y - this.scrollOffset;
            int relativeY = (int)event.y() - contentY;
            this.dragInsertIndex = Math.max(0, Math.min(this.items.size(), relativeY / (this.itemHeight + this.itemSpacing)));

            // Detect auto-scroll zone during drag
            int contentHeight = this.height - CONTENT_START_Y;
            int relativeMouseY = (int)event.y() - (this.getY() + CONTENT_START_Y);

            if (relativeMouseY < AUTO_SCROLL_MARGIN && this.scrollOffset > 0) {
                this.autoScrollDirection = AutoScrollDirection.UP;
            } else if (relativeMouseY > contentHeight - AUTO_SCROLL_MARGIN && this.scrollOffset < this.maxScrollOffset) {
                this.autoScrollDirection = AutoScrollDirection.DOWN;
            } else {
                this.autoScrollDirection = AutoScrollDirection.NONE;
            }

            return true;
        }

        // Check if we should allow widget dragging based on content area
        if (this.shouldAllowWidgetDrag(event.x(), event.y())) {
            return super.mouseDragged(event, deltaX, deltaY);
        }

        return false;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // Check for item removal (Ctrl+Right-click)
        if (this.removable && event.button() == 1 && event.hasControlDown()) {
            int idx = this.getItemIndexAtPosition(event.x(), event.y());
            if (idx >= 0) {
                this.removeItem(idx);
                return true;
            }
        }

        // Check if clicking scrollbar
        if (this.needsScrollbar() && event.button() == 0) {
            int scrollbarX = this.getX() + this.width - SCROLLBAR_WIDTH;
            int scrollbarY = this.getY() + CONTENT_START_Y;
            int scrollbarHeight = this.height - CONTENT_START_Y;
            int thumbHeight = this.calculateScrollbarThumbHeight(scrollbarHeight);
            int thumbY = this.calculateScrollbarThumbY(scrollbarY, scrollbarHeight, thumbHeight);

            if (this.isMouseOverScrollbar(event.x(), event.y(), scrollbarX, thumbY, thumbHeight)) {
                this.draggingScrollbar = true;
                this.scrollbarDragStartY = (int)event.y();
                this.scrollbarStartOffset = this.scrollOffset;

                return true;
            }
        }

        // Check for item click
        if (event.button() == 0) {
            int idx = this.getItemIndexAtPosition(event.x(), event.y());
            if (idx >= 0) {
                if (this.reorderable) {
                    // potential drag
                    this.draggedItemIndex = idx;
                    this.draggedItem = this.items.get(idx);
                    this.dragStartMouseX = event.x();
                    this.dragStartMouseY = event.y();

                    int itemY = this.getY() + CONTENT_START_Y + idx * (this.itemHeight + this.itemSpacing) - this.scrollOffset;
                    this.dragOffsetY = event.y() - itemY;

                } else {
                    this.trackingClickItemIndex = idx;
                }

                return true;
            }
        }

        if (this.shouldAllowWidgetDrag(event.x(), event.y())) {
            return super.mouseClicked(event, doubleClick);
        }

        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0) {
            if (this.draggingScrollbar) {
                this.draggingScrollbar = false;
            } else if (this.draggingItem) {
                this.reorderItem(this.draggedItemIndex, this.dragInsertIndex);
                this.resetDragState();
            } else if (this.draggedItemIndex >= 0) {
                int originalIdx = this.draggedItemIndex;
                this.resetDragState();
                if (this.handleItemClick(event, originalIdx)) {
                    super.mouseReleased(event);
                    return true;
                }
                super.mouseReleased(event);
                return false;
            } else if (this.trackingClickItemIndex >= 0) {
                int originalIdx = this.trackingClickItemIndex;
                this.trackingClickItemIndex = -1;
                if (this.handleItemClick(event, originalIdx)) {
                    super.mouseReleased(event);
                    return true;
                }
                super.mouseReleased(event);
                return false;
            }
        }

        super.mouseReleased(event);
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.isMouseOver(mouseX, mouseY) && this.needsScrollbar()) {
            int scrollDelta = (int)(-verticalAmount * this.scrollSpeed);
            this.scrollOffset = Math.max(0, Math.min(this.scrollOffset + scrollDelta, this.maxScrollOffset));
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) {
            if (this.draggingItem || this.draggedItemIndex >= 0) {
                this.resetDragState();
                return true;
            }

            if (this.draggingScrollbar) {
                this.draggingScrollbar = false;
                return true;
            }
        }

        return super.keyPressed(event);
    }

    public boolean isDragging() {
        return this.draggingItem || this.draggingScrollbar || this.isDragging;
    }

    /**
     * Determine if widget dragging should be allowed at the given position.
     * Title bar always allows dragging.
     * Content area allows dragging if there are no items at that position.
     */
    private boolean shouldAllowWidgetDrag(double mouseX, double mouseY) {
        // Always allow dragging in title bar
        if (mouseY >= this.getY() && mouseY < this.getY() + TITLE_BAR_HEIGHT) {
            return true;
        }

        // In content area, only allow if not over an item
        int idx = this.getItemIndexAtPosition(mouseX, mouseY);
        return idx == -1;
    }

    /**
     * Get the item index at the given mouse position.
     * Returns -1 if no item at position.
     */
    private int getItemIndexAtPosition(double mouseX, double mouseY) {
        if (!this.isMouseOver(mouseX, mouseY)) {
            return -1;
        }

        int contentX = this.getX() + CONTENT_MARGIN_X;
        int contentY = this.getY() + CONTENT_START_Y;
        int contentWidth = this.width - CONTENT_PADDING_X - (this.needsScrollbar() ? SCROLLBAR_WIDTH : 0);

        // Check if in content area
        if (mouseX < contentX || mouseX >= contentX + contentWidth) {
            return -1;
        }
        if (mouseY < contentY || mouseY >= contentY + (this.height - CONTENT_START_Y)) {
            return -1;
        }

        // Calculate item index
        int relativeY = (int)mouseY - contentY + this.scrollOffset;
        int idx = relativeY / (this.itemHeight + this.itemSpacing);

        // Validate index
        if (idx < 0 || idx >= this.items.size()) {
            return -1;
        }

        // Check if within item bounds (not in spacing)
        int itemOffset = relativeY % (this.itemHeight + this.itemSpacing);
        if (itemOffset >= this.itemHeight) {
            return -1;
        }

        return idx;
    }

    /**
     * Reset all drag-related state to initial values.
     */
    private void resetDragState() {
        this.draggingItem = false;
        this.draggedItemIndex = -1;
        this.draggedItem = null;
        this.dragInsertIndex = -1;
        this.trackingClickItemIndex = -1;
        this.autoScrollDirection = AutoScrollDirection.NONE;
    }

    /**
     * Handle item click on mouse release. Returns true if click was handled.
     */
    private boolean handleItemClick(MouseButtonEvent event, int itemIndex) {
        int currentIdx = this.getItemIndexAtPosition(event.x(), event.y());
        if (currentIdx != itemIndex) {
            return false;
        }

        Renderable clickedItem = this.items.get(itemIndex);

        if (clickedItem.mouseClicked(event)) {
            return true;
        }

        if (this.itemClickCallback != null) {
            this.itemClickCallback.accept(this, clickedItem, itemIndex);
            return true;
        }

        return false;
    }

    /**
     * Reorder an item from one position to another.
     */
    private void reorderItem(int fromIdx, int toIdx) {
        if (!this.reorderable) {
            return;
        }
        if (fromIdx < 0 || fromIdx >= this.items.size()) {
            return;
        }
        if (toIdx < 0 || toIdx > this.items.size()) {
            return;
        }
        if (fromIdx == toIdx) {
            return;
        }

        Renderable item = this.items.remove(fromIdx);

        // Adjust toIdx if removing from before it
        if (fromIdx < toIdx) {
            toIdx--;
        }

        this.items.add(toIdx, item);

        if (this.itemReorderedCallback != null) {
            this.itemReorderedCallback.accept(this, fromIdx, toIdx);
        }
    }

    /**
     * Remove an item at the specified index.
     */
    public void removeItem(int idx) {
        if (idx >= 0 && idx < this.items.size()) {
            Renderable removed = this.items.remove(idx);
            this.updateScrollLimits();

            if (this.itemRemovedCallback != null) {
                this.itemRemovedCallback.accept(this, removed, idx);
            }
        }
    }

    public ListWidget setItemHeight(int height) {
        this.itemHeight = height;
        this.updateScrollLimits();
        this.updateHeightFromMaxVisibleItems();
        return this;
    }

    /**
     * Set widget height in pixels.
     */
    @Override
    public void setHeight(int height) {
        super.setHeight(height);
        this.updateScrollLimits();
    }

    /**
     * Set spacing between items in pixels.
     */
    public ListWidget setItemSpacing(int spacing) {
        this.itemSpacing = spacing;
        this.updateScrollLimits();
        this.updateHeightFromMaxVisibleItems();
        return this;
    }

    /**
     * Set scroll speed in pixels per scroll notch.
     */
    public ListWidget setScrollSpeed(int speed) {
        this.scrollSpeed = speed;
        return this;
    }

    public ListWidget setMaxVisibleItems(int count) {
        this.maxVisibleItems = count;
        this.updateHeightFromMaxVisibleItems();
        return this;
    }

    private void updateHeightFromMaxVisibleItems() {
        if (this.maxVisibleItems > 0) {
            int contentHeight = this.maxVisibleItems * (this.itemHeight + this.itemSpacing) - this.itemSpacing;
            this.height = 21 + contentHeight;
            this.updateScrollLimits();
        }
    }

    public ListWidget onItemReordered(Runnable callback) {
        this.itemReorderedCallback = (self, fromIdx, toIdx) -> callback.run();
        return this;
    }

    public ListWidget onReorder(TriConsumer<ListWidget, Integer, Integer> callback) {
        this.itemReorderedCallback = callback;
        return this;
    }

    public ListWidget onHoverChange(TriConsumer<ListWidget, Integer, Integer> callback) {
        this.onHoverChangeCallback = callback;
        return this;
    }

    /**
     * Set whether items can be removed via Ctrl+Right-click.
     */
    public ListWidget setRemovable(boolean removable) {
        this.removable = removable;
        return this;
    }

    /**
     * Set whether items can be reordered via drag-and-drop.
     */
    public ListWidget setReorderable(boolean reorderable) {
        this.reorderable = reorderable;
        return this;
    }

    /**
     * Configure this list as static - no reordering, no removal, and widget position is fixed.
     * This is a convenience method that sets removable=false, reorderable=false.
     * Note: To make the widget position static, call setDraggable(false) on the parent DraggableWidget.
     */
    public ListWidget setStatic() {
        this.removable = false;
        this.reorderable = false;
        return this;
    }

    /**
     * Create a static list widget with fixed position and non-interactive items.
     * Items cannot be reordered or removed, and the widget itself cannot be dragged.
     */
    public static ListWidget createStatic(
            int defaultX, int defaultY,
            int width, int height,
            String title
    ) {
        ListWidget widget = new ListWidget(defaultX, defaultY, width, height, title);
        widget.setStatic().setDraggable(false);
        return widget;
    }

    public ListWidget onItemClick(TriConsumer<ListWidget, Renderable, Integer> callback) {
        this.itemClickCallback = callback;
        return this;
    }

    public ListWidget onItemRemoved(TriConsumer<ListWidget, Renderable, Integer> callback) {
        this.itemRemovedCallback = callback;
        return this;
    }
}
