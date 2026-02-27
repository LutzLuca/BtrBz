package com.github.lutzluca.btrbz.widgets.base;

import lombok.Getter;
import lombok.ToString;

/**
 * Encapsulates rendering permissions and constraints for a single render cycle.
 * Mutable to allow reuse and avoid per-frame allocations.
 */
@ToString
public final class RenderContext {
    @Getter
    private boolean isTopWidget;
    @Getter
    private boolean anyDraggingActive;

    public RenderContext() {
        this.isTopWidget = false;
        this.anyDraggingActive = false;
    }

    public RenderContext(boolean isTopWidget, boolean anyDraggingActive) {
        this.isTopWidget = isTopWidget;
        this.anyDraggingActive = anyDraggingActive;
    }

    public void update(boolean isTopWidget, boolean anyDraggingActive) {
        this.isTopWidget = isTopWidget;
        this.anyDraggingActive = anyDraggingActive;
    }

    public boolean canShowTooltips() {
        return this.isTopWidget && !this.anyDraggingActive;
    }

    public boolean canProcessHover() {
        return this.isTopWidget && !this.anyDraggingActive;
    }
}
