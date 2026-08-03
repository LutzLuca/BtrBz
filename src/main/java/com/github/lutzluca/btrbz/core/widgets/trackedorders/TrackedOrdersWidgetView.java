package com.github.lutzluca.btrbz.core.widgets.trackedorders;

import com.github.lutzluca.btrbz.core.widgets.ScrollOffsetView;
import com.github.lutzluca.btrbz.core.widgets.WidgetView;
import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarStyles;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetLayoutTokens;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;
import java.util.function.Consumer;

import static com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi.label;
import static com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi.spacer;

final class TrackedOrdersWidgetView implements
    WidgetView<BazaarWidgetViewData.OrdersData, TrackedOrdersWidgetConfig, TrackedOrdersAction>,
    ScrollOffsetView {
    private final FlowLayout root = UIContainers.verticalFlow(Sizing.fixed(1), Sizing.content());
    private final LabelComponent status = label("", BazaarStyles.MUTED_TEXT);
    private final BazaarTrackedOrderListComponent list = new BazaarTrackedOrderListComponent();

    TrackedOrdersWidgetView() {
        this.root.allowOverflow(true);
        this.root.gap(WidgetLayoutTokens.SECTION_GAP);
        var header = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        header.allowOverflow(true);
        header.verticalAlignment(VerticalAlignment.CENTER);
        header.child(label("Tracked Orders", BazaarStyles.PRIMARY_TEXT));
        header.child(spacer());
        header.child(this.status);
        this.root.child(header);
        this.root.child(this.list);
    }

    @Override
    public UIComponent root() {
        return this.root;
    }

    @Override
    public double scrollOffset() {
        return this.list.scrollOffset();
    }

    @Override
    public void scrollOffset(double offset) {
        this.list.scrollOffset(offset);
    }

    @Override
    public void update(
        BazaarWidgetViewData.OrdersData data,
        TrackedOrdersWidgetConfig config,
        WidgetSession session,
        Consumer<TrackedOrdersAction> actions
    ) {
        var sorted = TrackedOrdersWidget.sortedOrders(data.orders(), config.sort);
        this.root.horizontalSizing(Sizing.fixed(config.contentWidth));
        this.status.text(net.minecraft.network.chat.Component.literal(
            TrackedOrdersWidget.headerStatus(data, sorted.size())
        ));
        this.list.update(sorted, config, true, BazaarWidgetViewData.Order::tooltipLines, actions);
    }
}
