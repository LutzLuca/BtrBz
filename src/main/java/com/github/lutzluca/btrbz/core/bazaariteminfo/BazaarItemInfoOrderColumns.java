package com.github.lutzluca.btrbz.core.bazaariteminfo;

import com.github.lutzluca.btrbz.core.widgets.ui.WidgetLayoutTokens;

/** Shared Order Book column boundaries used by headings, rows, and hit testing. */
final class BazaarItemInfoOrderColumns {
    private static final int CELL_GAP = 3;

    private BazaarItemInfoOrderColumns() {}

    static Layout layout(int width, boolean showCumulative, boolean showOrders) {
        int left = WidgetLayoutTokens.ROW_HORIZONTAL_PADDING;
        int right = Math.max(left + 1, width - WidgetLayoutTokens.rowTrailingInset(true));
        int usable = Math.max(1, right - left);

        int priceWeight;
        int itemsWeight;
        int cumulativeWeight;
        int ordersWeight;
        if (showCumulative && showOrders) {
            priceWeight = 40;
            itemsWeight = 20;
            cumulativeWeight = 26;
            ordersWeight = 14;
        } else if (showCumulative) {
            priceWeight = 44;
            itemsWeight = 23;
            cumulativeWeight = 33;
            ordersWeight = 0;
        } else if (showOrders) {
            priceWeight = 50;
            itemsWeight = 30;
            cumulativeWeight = 0;
            ordersWeight = 20;
        } else {
            priceWeight = 58;
            itemsWeight = 42;
            cumulativeWeight = 0;
            ordersWeight = 0;
        }

        int totalWeight = priceWeight + itemsWeight + cumulativeWeight + ordersWeight;
        int priceEnd = left + usable * priceWeight / totalWeight;
        int itemsEnd = priceEnd + usable * itemsWeight / totalWeight;
        int cumulativeEnd = showCumulative
            ? itemsEnd + usable * cumulativeWeight / totalWeight
            : itemsEnd;

        var price = new Column(left, priceEnd, true);
        var items = new Column(priceEnd, itemsEnd, true);
        var cumulative = new Column(itemsEnd, cumulativeEnd, showCumulative);
        var orders = new Column(cumulativeEnd, right, showOrders);
        return new Layout(price, items, cumulative, orders);
    }

    record Layout(Column price, Column items, Column cumulative, Column orders) {}

    record Column(int start, int end, boolean visible) {
        int textLeft() {
            return this.start + CELL_GAP;
        }

        int textRight() {
            return Math.max(this.textLeft(), this.end - CELL_GAP);
        }
    }
}
