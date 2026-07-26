package com.github.lutzluca.btrbz.core.widgets.trackedorders;

import com.github.lutzluca.btrbz.core.widgets.action.BazaarAction;
import com.github.lutzluca.btrbz.core.widgets.config.BazaarWidgetOptions;
import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import com.github.lutzluca.btrbz.core.widgets.hud.BazaarHudWidget;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarStyles;
import com.github.lutzluca.btrbz.widgets.framework.ui.WidgetLayoutTokens;
import com.github.lutzluca.btrbz.widgets.framework.ui.WidgetScrollState;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import static com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi.*;

public final class TrackedOrdersWidget {
    private TrackedOrdersWidget() {}

    public static UIComponent render(
        BazaarWidgetViewData.OrdersData data,
        BazaarWidgetOptions.TrackedOrders options,
        boolean interactive,
        WidgetScrollState scrollState,
        TrackedOrderDragController drag,
        TrackedOrderHoverController hover,
        Consumer<BazaarAction> actions
    ) {
        var sorted = sortedOrders(data.orders(), options.sort());
        var layout = UIContainers.verticalFlow(Sizing.fixed(options.contentWidth()), Sizing.content());
        layout.allowOverflow(true);
        layout.gap(WidgetLayoutTokens.SECTION_GAP);

        var header = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        header.allowOverflow(true);
        header.verticalAlignment(VerticalAlignment.CENTER);
        header.child(label("Tracked Orders", BazaarStyles.PRIMARY_TEXT));
        header.child(spacer());
        header.child(label(sorted.size() + " active", BazaarStyles.MUTED_TEXT));
        layout.child(header);

        if (options.showStatusSummary()) {
            layout.child(BazaarHudWidget.statusCountsStrip(data, options.contentWidth()));
        }
        layout.child(new BazaarTrackedOrderListComponent(
            sorted,
            options,
            interactive,
            BazaarWidgetViewData.Order::tooltipLines,
            scrollState,
            drag,
            hover,
            actions
        ));
        return layout;
    }

    public static List<BazaarWidgetViewData.Order> sortedOrders(
        List<BazaarWidgetViewData.Order> orders,
        BazaarWidgetOptions.TrackedSort sort
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
