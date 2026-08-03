package com.github.lutzluca.btrbz.core.widgets.trackedorders;

import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class TrackedOrdersWidget {
    private TrackedOrdersWidget() {}

    static String headerStatus(BazaarWidgetViewData.OrdersData data, int activeOrderCount) {
        return activeOrderCount + " active · " + data.filledOrderCount() + " filled";
    }

    public static List<BazaarWidgetViewData.Order> sortedOrders(
        List<BazaarWidgetViewData.Order> orders,
        TrackedOrdersWidgetConfig.TrackedSort sort
    ) {
        var sorted = new ArrayList<>(orders);
        switch (sort) {
            case Newest -> sorted.sort(Comparator.comparingLong(BazaarWidgetViewData.Order::creationSequence).reversed());
            case Status -> sorted.sort(Comparator.comparing(order -> order.status().ordinal()));
            case Manual -> { }
        }
        return List.copyOf(sorted);
    }
}
