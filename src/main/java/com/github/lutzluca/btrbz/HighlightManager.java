package com.github.lutzluca.btrbz;

import com.github.lutzluca.btrbz.TrackedOrder.OrderInfo;
import com.github.lutzluca.btrbz.TrackedOrder.OrderStatus;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// TODO: there is still some flickering when claiming or canceling an order
// the UI reorders the orders before parsing and reevaluating highlights,
// which can cause desync due to using outdated highlights
public final class HighlightManager {

    private static final Map<Integer, Integer> highlights = new HashMap<>();

    private HighlightManager() { }

    public static Optional<Integer> getHighlight(int idx) {
        return Optional.ofNullable(highlights.get(idx));
    }

    public static void setStatuses(List<OrderInfo> parsedOrders) {
        highlights.clear();

        var trackedCopy = BtrBz.getManager().getTrackedOrders();
        for (var tracked : trackedCopy) {
            parsedOrders.stream().filter(tracked::match).findFirst().ifPresent(info -> {
                parsedOrders.remove(info);
                highlights.put(info.slotIdx(), colorForStatus(tracked.status));
            });
        }

        for (var info : parsedOrders) {
            var color = info.filled() ? 0xFFEFBF04 : 0xFFAA55FF;
            highlights.put(info.slotIdx(), color);
        }
    }

    public static void updateStatus(int slotIdx, OrderStatus status) {
        if (slotIdx < 0) {
            return;
        }

        highlights.put(slotIdx, colorForStatus(status));
    }

    private static int colorForStatus(OrderStatus status) {
        return switch (status) {
            case OrderStatus.Top ignored -> 0xFF55FF55;
            case OrderStatus.Matched ignored -> 0xFF5555FF;
            case OrderStatus.Undercut ignored -> 0xFFFF5555;
            case OrderStatus.Unknown ignored -> 0xFFAA55FF;
        };
    }
}
