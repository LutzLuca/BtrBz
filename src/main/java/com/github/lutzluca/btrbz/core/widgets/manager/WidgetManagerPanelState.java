package com.github.lutzluca.btrbz.core.widgets.manager;

import com.github.lutzluca.btrbz.core.widgets.layout.WidgetPlacement;

/**
 * Tracks the widget-manager panel independently from the owo component tree so
 * dragging and window resizing do not discard its position.
 */
final class WidgetManagerPanelState {
    static final int MINIMUM_WIDTH = 170;
    static final int MAXIMUM_WIDTH = 240;
    static final int MINIMUM_HEIGHT_PERCENT = 55;
    static final int MAXIMUM_HEIGHT_PERCENT = 90;

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
            this.y = clampToViewport(margin, viewportHeight - panelHeight);
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

    static int configuredWidth(int value) {
        return WidgetPlacement.clampInt(value, MINIMUM_WIDTH, MAXIMUM_WIDTH);
    }

    static int configuredHeightPercent(int value) {
        return WidgetPlacement.clampInt(value, MINIMUM_HEIGHT_PERCENT, MAXIMUM_HEIGHT_PERCENT);
    }

    private void clampToViewport(
        int viewportWidth,
        int viewportHeight,
        int panelWidth,
        int panelHeight
    ) {
        this.x = clampToViewport(this.x, viewportWidth - panelWidth);
        this.y = clampToViewport(this.y, viewportHeight - panelHeight);
    }

    private static int clampToViewport(int value, int maximum) {
        return WidgetPlacement.clampInt(value, 0, Math.max(0, maximum));
    }
}
