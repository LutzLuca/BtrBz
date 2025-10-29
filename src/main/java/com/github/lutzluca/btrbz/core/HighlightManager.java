package com.github.lutzluca.btrbz.core;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.OrderManager.StatusUpdate;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class HighlightManager {

    private final Map<Integer, Integer> highlights = new HashMap<>();

    private static int colorForStatus(OrderStatus status) {
        return switch (status) {
            case OrderStatus.Top ignored -> 0xFF55FF55;
            case OrderStatus.Matched ignored -> 0xFF5555FF;
            case OrderStatus.Undercut ignored -> 0xFFFF5555;
            case OrderStatus.Unknown ignored -> 0xFFAA55FF;
        };
    }

    public Optional<Integer> getHighlight(int idx) {
        return Optional.ofNullable(highlights.get(idx));
    }

    public void setStatuses(List<OrderInfo> parsedOrders) {
        this.highlights.clear();

        var trackedCopy = BtrBz.orderManager().getTrackedOrders();
        for (var tracked : trackedCopy) {
            parsedOrders.stream().filter(tracked::matches).findFirst().ifPresent(info -> {
                parsedOrders.remove(info);
                this.highlights.put(info.slotIdx(), colorForStatus(tracked.status));
            });
        }

        for (var info : parsedOrders) {
            var color = info.filled() ? 0xFFEFBF04 : 0xFFAA55FF;
            this.highlights.put(info.slotIdx(), color);
        }
    }

    public void updateStatus(StatusUpdate update) {
        int slotIdx = update.trackedOrder().slot;
        if (slotIdx < 0) {
            return;
        }

        this.highlights.put(slotIdx, colorForStatus(update.status()));
    }
}
