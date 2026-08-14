package com.github.lutzluca.btrbz.core.widgets.trackedorders;

import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarStyles;
import com.github.lutzluca.btrbz.core.widgets.ui.ReorderableScrollListComponent;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetLayoutTokens;
import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrderId;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Retained tracked-order rows and hover behavior over shared reorder mechanics. */
final class BazaarTrackedOrderListComponent extends ReorderableScrollListComponent<TrackedOrderId> {
    private boolean interactive;
    private @Nullable TrackedOrderId hoveredId;

    BazaarTrackedOrderListComponent() {
        super(
            BazaarTrackedOrderRowComponent.STANDARD_HEIGHT,
            WidgetLayoutTokens.LIST_GAP,
            true,
            BazaarStyles.SCROLLBAR,
            BazaarStyles.INSERTION,
            0,
            4,
            2);
    }

    void update(
        List<BazaarWidgetViewData.Order> orders,
        TrackedOrdersWidgetConfig options,
        boolean interactive,
        Function<BazaarWidgetViewData.Order, List<Component>> tooltipProvider,
        Consumer<TrackedOrdersAction> actions
    ) {
        this.interactive = interactive;

        this.reconcileRows(
            orders,
            BazaarWidgetViewData.Order::id,
            (order, index) -> new BazaarTrackedOrderRowComponent(
                this, order, options, tooltipProvider.apply(order), index, interactive, actions),
            (row, order, index) -> row.update(
                order, options, tooltipProvider.apply(order), index, interactive, actions),
            viewportHeight(options, orders.size()),
            interactive,
            options.sort == TrackedOrdersWidgetConfig.TrackedSort.Manual);
    }

    boolean isHovered(TrackedOrderId id) {
        return id.equals(this.hoveredId);
    }

    @Override
    protected void beforeChildrenDraw(int mouseX, int mouseY) {
        if (!this.interactive
            || !this.scrollList().isInBoundingBox(mouseX, mouseY)
            || this.hoverSuppressed()
            || this.scrollList().isPointerOverScrollbar(mouseX, mouseY)) {
            this.hoveredId = null;
            return;
        }

        this.hoveredId = null;

        for (var component : this.rows()) {
            var row = (BazaarTrackedOrderRowComponent) component;

            if (row.isInBoundingBox(mouseX, mouseY)) {
                this.hoveredId = row.orderId();
                break;
            }
        }
    }

    private static int viewportHeight(TrackedOrdersWidgetConfig options, int orderCount) {
        return WidgetLayoutTokens.configuredListViewportHeight(
            rowHeight(options), orderCount, options.visibleRows, true);
    }

    private static int rowHeight(TrackedOrdersWidgetConfig options) {
        return options.layout == TrackedOrdersWidgetConfig.TrackedLayout.Compact
            ? BazaarTrackedOrderRowComponent.COMPACT_HEIGHT
            : BazaarTrackedOrderRowComponent.STANDARD_HEIGHT;
    }

    public static OptionalInt visibleInsertionIndicatorY(int lineY, int viewportTop, int viewportBottom) {
        return ReorderableScrollListComponent.visibleInsertionIndicatorY(lineY, viewportTop, viewportBottom);
    }
}
