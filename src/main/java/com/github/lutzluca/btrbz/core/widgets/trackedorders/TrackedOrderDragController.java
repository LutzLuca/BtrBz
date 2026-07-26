package com.github.lutzluca.btrbz.core.widgets.trackedorders;

import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrderId;

public final class TrackedOrderDragController {
    private TrackedOrderId activeOrderId;
    private int dropIndex;

    public TrackedOrderDragController() {}

    boolean dragging() {
        return this.activeOrderId != null;
    }

    boolean dragging(TrackedOrderId orderId) {
        return orderId.equals(this.activeOrderId);
    }

    void start(TrackedOrderId orderId, int index) {
        this.activeOrderId = orderId;
        this.dropIndex = index;
    }

    int dropIndex() {
        return this.dropIndex;
    }

    void updateDropIndex(int dropIndex) {
        this.dropIndex = Math.max(0, dropIndex);
    }

    TrackedOrderDragResult finish() {
        var result = this.activeOrderId == null
            ? null
            : new TrackedOrderDragResult(this.activeOrderId, this.dropIndex);
        this.clear();
        return result;
    }

    void clear() {
        this.activeOrderId = null;
        this.dropIndex = 0;
    }
}
