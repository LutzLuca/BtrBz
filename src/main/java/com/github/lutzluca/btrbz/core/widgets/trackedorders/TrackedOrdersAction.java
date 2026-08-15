package com.github.lutzluca.btrbz.core.widgets.trackedorders;

import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrderId;

public sealed interface TrackedOrdersAction permits TrackedOrdersAction.Reorder {
    record Reorder(TrackedOrderId id, int insertionIndex) implements TrackedOrdersAction {}
}
