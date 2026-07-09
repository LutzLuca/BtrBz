package com.github.lutzluca.btrbz.core.trackedorders;

import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus;
import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrder;

public record StatusUpdate(TrackedOrder order, OrderStatus curr, OrderStatus prev) { }
