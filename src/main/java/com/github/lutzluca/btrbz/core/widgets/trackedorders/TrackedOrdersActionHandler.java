package com.github.lutzluca.btrbz.core.widgets.trackedorders;

import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.trackedorders.TrackedOrderManager;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import com.github.lutzluca.btrbz.core.widgets.WidgetActionHandler;

public final class TrackedOrdersActionHandler implements WidgetActionHandler<TrackedOrdersAction> {
    private final TrackedOrderManager trackedOrders;
    public TrackedOrdersActionHandler(TrackedOrderManager trackedOrders) { this.trackedOrders = trackedOrders; }

    @Override
    public void handle(TrackedOrdersAction action, WidgetSession source, WidgetSession current) {
        if (!source.sameWorkflow(current)
            || ConfigManager.get().widgets.trackedOrders.sort != TrackedOrdersWidgetConfig.TrackedSort.Manual) return;
        switch (action) {
            case TrackedOrdersAction.Reorder reorder -> {
                if (this.trackedOrders.currentOrders().stream()
                    .noneMatch(order -> order.id().equals(reorder.id()))) return;
                this.trackedOrders.reorder(reorder.id(), reorder.insertionIndex());
            }
        }
    }
}
