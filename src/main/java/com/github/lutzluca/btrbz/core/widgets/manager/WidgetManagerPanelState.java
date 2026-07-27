package com.github.lutzluca.btrbz.core.widgets.manager;

/**
 * Tracks the widget-manager panel independently from the owo component tree so
 * dragging and window resizing do not discard its position.
 */
final class WidgetManagerPanelState {
    private int x;
    private int y;
    private boolean initialized;
    private boolean userPositioned;
    private boolean dragging;
    private double pointerOffsetX;
    private double pointerOffsetY;

    int x() {
        return this.x;
    }

    int y() {
        return this.y;
    }

    boolean isDragging() {
        return this.dragging;
    }

    void fitToViewport(
        int viewportWidth,
        int viewportHeight,
        int panelWidth,
        int panelHeight,
        int margin
    ) {
        if (!this.initialized || !this.userPositioned) {
            this.x = Math.max(0, viewportWidth - panelWidth - margin);
            this.y = clamp(margin, viewportHeight - panelHeight);
            this.initialized = true;
            return;
        }

        this.clampToViewport(viewportWidth, viewportHeight, panelWidth, panelHeight);
    }

    void resizePanel(
        int oldPanelWidth,
        int newPanelWidth,
        int viewportWidth,
        int viewportHeight,
        int newPanelHeight,
        int margin
    ) {
        if (!this.initialized || !this.userPositioned) {
            this.initialized = false;
            this.fitToViewport(viewportWidth, viewportHeight, newPanelWidth, newPanelHeight, margin);
            return;
        }

        // Keep the control on the right edge stationary when the panel folds
        // down to its title bar or expands again.
        this.x += oldPanelWidth - newPanelWidth;
        this.clampToViewport(viewportWidth, viewportHeight, newPanelWidth, newPanelHeight);
    }

    void beginDrag(double pointerX, double pointerY) {
        this.pointerOffsetX = pointerX - this.x;
        this.pointerOffsetY = pointerY - this.y;
        this.dragging = true;
    }

    boolean dragTo(
        double pointerX,
        double pointerY,
        int viewportWidth,
        int viewportHeight,
        int panelWidth,
        int panelHeight
    ) {
        if (!this.dragging) return false;

        this.x = (int) Math.round(pointerX - this.pointerOffsetX);
        this.y = (int) Math.round(pointerY - this.pointerOffsetY);
        this.userPositioned = true;
        this.clampToViewport(viewportWidth, viewportHeight, panelWidth, panelHeight);
        return true;
    }

    boolean endDrag() {
        if (!this.dragging) return false;
        this.dragging = false;
        return true;
    }

    private void clampToViewport(
        int viewportWidth,
        int viewportHeight,
        int panelWidth,
        int panelHeight
    ) {
        this.x = clamp(this.x, viewportWidth - panelWidth);
        this.y = clamp(this.y, viewportHeight - panelHeight);
    }

    private static int clamp(int value, int maximum) {
        return Math.max(0, Math.min(value, Math.max(0, maximum)));
    }
}
