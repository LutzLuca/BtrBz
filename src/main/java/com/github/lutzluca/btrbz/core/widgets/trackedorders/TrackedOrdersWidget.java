package com.github.lutzluca.btrbz.core.widgets.trackedorders;

import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class TrackedOrdersWidget {
    private TrackedOrdersWidget() {}

    static String headerStatus(
        BazaarWidgetViewData.OrdersData data,
        int activeOrderCount,
        boolean showFilledCount
    ) {
        String active = activeOrderCount + " active";
        return showFilledCount ? active + " · " + data.filledOrderCount() + " filled" : active;
    }

    public static List<BazaarWidgetViewData.Order> sortedOrders(
        List<BazaarWidgetViewData.Order> orders,
        TrackedOrdersWidgetConfig.TrackedSort sort
    ) {
        var sorted = new ArrayList<>(orders);
        switch (sort) {
            case Newest -> sorted.sort(Comparator.comparingLong(BazaarWidgetViewData.Order::creationSequence).reversed());
            case Oldest -> sorted.sort(Comparator.comparingLong(BazaarWidgetViewData.Order::creationSequence));
            case Status -> sorted.sort(Comparator.comparing(order -> order.status().ordinal()));
            case Side -> sorted.sort(Comparator.comparing(order -> order.side().ordinal()));
            case Product -> sorted.sort(Comparator.comparing(BazaarWidgetViewData.Order::itemName));
            case Value -> sorted.sort(Comparator.comparingLong(
                (BazaarWidgetViewData.Order order) -> order.unitPrice() * order.amount()
            ).reversed());
            case Manual -> { }
        }
        return List.copyOf(sorted);
    }
}
