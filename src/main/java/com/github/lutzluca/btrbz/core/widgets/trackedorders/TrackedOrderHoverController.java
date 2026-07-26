package com.github.lutzluca.btrbz.core.widgets.trackedorders;

import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrderId;
import java.util.Objects;

public final class TrackedOrderHoverController {
    private TrackedOrderId hoveredOrderId;

    public TrackedOrderHoverController() {}

    void update(TrackedOrderId orderId) {
        if (Objects.equals(this.hoveredOrderId, orderId)) return;
        this.hoveredOrderId = orderId;
    }

    boolean isHovered(TrackedOrderId orderId) {
        return orderId.equals(this.hoveredOrderId);
    }
}
