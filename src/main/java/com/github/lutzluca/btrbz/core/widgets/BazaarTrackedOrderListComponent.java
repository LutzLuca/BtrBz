package com.github.lutzluca.btrbz.core.widgets;

import com.github.lutzluca.btrbz.widgets.framework.ui.WidgetLayoutTokens;
import com.github.lutzluca.btrbz.widgets.framework.ui.WidgetScrollListComponent;
import com.github.lutzluca.btrbz.widgets.framework.ui.WidgetScrollState;
import io.wispforest.owo.ui.base.BaseParentUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.ParentUIComponent;
import io.wispforest.owo.ui.core.Size;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Consumer;
import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrderId;
import java.util.function.Function;

/** Owns pointer-to-gap calculation for the tracked-orders list. */
final class BazaarTrackedOrderListComponent extends BaseParentUIComponent {
    private static final int AUTO_SCROLL_THRESHOLD = 14;
    private static final double AUTO_SCROLL_STEP = 0.028;

    private final WidgetScrollListComponent scrollList;
    private final List<BazaarTrackedOrderRowComponent> rows = new ArrayList<>();
    private final List<UIComponent> children;
    private final boolean reorderable;
    private final boolean interactive;
    private final BazaarData.DragController drag;
    private final BazaarData.HoverController hover;

    BazaarTrackedOrderListComponent(
        List<BazaarData.Order> orders,
        BazaarWidgetOptions.TrackedOrders options,
        boolean interactive,
        Function<BazaarData.Order, List<Component>> tooltipProvider,
        WidgetScrollState scrollState,
        BazaarData.DragController drag,
        BazaarData.HoverController hover,
        Consumer<BazaarAction> actions
    ) {
        super(Sizing.fill(100), Sizing.fixed(viewportHeight(options)));
        int rowHeight = rowHeight(options);
        this.reorderable = interactive && options.sort() == BazaarWidgetOptions.TrackedSort.MANUAL;
        this.interactive = interactive;
        this.drag = drag;
        this.hover = hover;

        for (int index = 0; index < orders.size(); index++) {
            var order = orders.get(index);
            var row = new BazaarTrackedOrderRowComponent(
                order, options, tooltipProvider.apply(order), index,
                interactive, drag, hover, this::updateDropIndex, actions
            );
            this.rows.add(row);
        }

        this.scrollList = new WidgetScrollListComponent(
            this.rows,
            viewportHeight(options),
            WidgetLayoutTokens.LIST_GAP,
            interactive,
            scrollState,
            BazaarStyles.SCROLLBAR
        );
        this.children = Collections.singletonList(this.scrollList);
        this.allowOverflow(true);
    }

    private static int viewportHeight(BazaarWidgetOptions.TrackedOrders options) {
        return WidgetLayoutTokens.listViewportHeight(rowHeight(options), options.visibleRows());
    }

    private static int rowHeight(BazaarWidgetOptions.TrackedOrders options) {
        return options.layout() == BazaarWidgetOptions.TrackedLayout.COMPACT
            ? BazaarTrackedOrderRowComponent.COMPACT_HEIGHT
            : BazaarTrackedOrderRowComponent.STANDARD_HEIGHT;
    }

    @Override
    public void layout(Size space) {
        this.scrollList.inflate(this.calculateChildSpace(space));
        this.scrollList.mount(this, this.x, this.y);
    }

    @Override public List<UIComponent> children() { return this.children; }

    @Override
    public ParentUIComponent removeChild(UIComponent child) {
        throw new UnsupportedOperationException("Tracked list owns its scroll container");
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        super.draw(graphics, mouseX, mouseY, partialTicks, delta);
        this.autoScroll(mouseX, mouseY);
        this.updateDropIndex(mouseY);
        this.updateHover(mouseX, mouseY);
        this.drawChildren(graphics, mouseX, mouseY, partialTicks, delta, this.children);
        this.drawInsertionIndicator(graphics);
    }

    private void updateHover(int mouseX, int mouseY) {
        if (!this.interactive
            || !this.scrollList.isInBoundingBox(mouseX, mouseY)
            || this.scrollList.scrollbarOwnsMouseCapture()
            || this.scrollList.isPointerOverScrollbar(mouseX, mouseY)) {
            this.hover.update(null);
            return;
        }

        TrackedOrderId hoveredId = null;
        for (var row : this.rows) {
            if (row.isInBoundingBox(mouseX, mouseY)) {
                hoveredId = row.orderId();
                break;
            }
        }
        this.hover.update(hoveredId);
    }

    private void autoScroll(int mouseX, int mouseY) {
        if (!this.reorderable || !this.drag.dragging()) return;
        if (mouseX < this.scrollList.x() || mouseX > this.scrollList.x() + this.scrollList.width()) return;

        if (mouseY < this.scrollList.y() + AUTO_SCROLL_THRESHOLD) {
            this.scrollList.scrollByProgress(-AUTO_SCROLL_STEP);
        } else if (mouseY > this.scrollList.y() + this.scrollList.height() - AUTO_SCROLL_THRESHOLD) {
            this.scrollList.scrollByProgress(AUTO_SCROLL_STEP);
        }
    }

    private void updateDropIndex(int pointerY) {
        if (!this.reorderable || !this.drag.dragging()) return;

        int gap = this.rows.size();
        for (int index = 0; index < this.rows.size(); index++) {
            var row = this.rows.get(index);
            if (pointerY < row.y() + row.height() / 2) {
                gap = index;
                break;
            }
        }
        this.drag.updateDropIndex(gap);
    }

    private void drawInsertionIndicator(OwoUIGraphics graphics) {
        if (!this.reorderable || !this.drag.dragging() || this.rows.isEmpty()) return;

        int gap = Math.max(0, Math.min(this.drag.dropIndex(), this.rows.size()));
        int lineY;
        if (gap == 0) {
            lineY = this.rows.getFirst().y() - 1;
        } else if (gap == this.rows.size()) {
            var last = this.rows.getLast();
            lineY = last.y() + last.height() + 1;
        } else {
            lineY = this.rows.get(gap).y() - 1;
        }
        var visibleLineY = visibleInsertionIndicatorY(
            lineY,
            this.scrollList.y(),
            this.scrollList.y() + this.scrollList.height() - 1
        );
        if (visibleLineY.isEmpty()) return;
        lineY = visibleLineY.getAsInt();

        graphics.fill(
            this.scrollList.x() + 5, lineY,
            this.scrollList.x() + this.scrollList.width() - 7, lineY + 1,
            BazaarStyles.INSERTION
        );
    }

    static OptionalInt visibleInsertionIndicatorY(int lineY, int viewportTop, int viewportBottom) {
        if (lineY < viewportTop - 1 || lineY > viewportBottom + 1) return OptionalInt.empty();
        return OptionalInt.of(Math.max(viewportTop, Math.min(viewportBottom, lineY)));
    }
}
