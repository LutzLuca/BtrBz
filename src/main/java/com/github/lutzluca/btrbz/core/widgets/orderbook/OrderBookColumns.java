package com.github.lutzluca.btrbz.core.widgets.orderbook;

import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData.OrderSide;
import java.util.List;
import java.util.function.ToIntFunction;

/** Native text measurements and mirrored columns shared by both order books. */
record OrderBookColumns(
    int width, int priceWidth, int quantityWidth, int ordersWidth, int totalWidth,
    boolean quantityBar, int inset, int gap
) {
    private static final int PRICE_WEIGHT = 43;
    private static final int QUANTITY_WEIGHT = 32;
    private static final int ORDERS_WEIGHT = 17;

    static Widths measure(
        List<OrderBookWidgetData.Entry> buy,
        List<OrderBookWidgetData.Entry> sell,
        ToIntFunction<String> textWidth
    ) {
        int price = textWidth.applyAsInt("Price");
        int quantity = textWidth.applyAsInt("Quantity");
        int orders = textWidth.applyAsInt("Orders");
        for (var entries : List.of(buy, sell)) {
            for (var entry : entries) {
                price = Math.max(price, textWidth.applyAsInt(entry.priceText()));
                quantity = Math.max(quantity, textWidth.applyAsInt(entry.quantityText()));
                orders = Math.max(orders, textWidth.applyAsInt(BazaarWidgetViewData.formatInt(entry.orders())));
            }
        }
        return new Widths(price, quantity, orders);
    }

    static OrderBookColumns numeric(int preferredWidth, Widths measured, boolean showOrders, int inset, int gap) {
        int minimumOrders = showOrders ? measured.orders() : 0;
        int gaps = gap * (showOrders ? 2 : 1);
        int width = Math.max(preferredWidth, measured.price() + measured.quantity() + minimumOrders + gaps + inset * 2);
        int columnSpace = width - gaps - inset * 2;
        int orders = showOrders
            ? Math.clamp(columnSpace * ORDERS_WEIGHT / (PRICE_WEIGHT + QUANTITY_WEIGHT + ORDERS_WEIGHT),
                minimumOrders, columnSpace - measured.price() - measured.quantity())
            : 0;
        int priceAndQuantitySpace = columnSpace - orders;
        int price = Math.clamp(priceAndQuantitySpace * PRICE_WEIGHT / (PRICE_WEIGHT + QUANTITY_WEIGHT),
            measured.price(), priceAndQuantitySpace - measured.quantity());
        return new OrderBookColumns(width, price, priceAndQuantitySpace - price, orders, 0, false, inset, gap);
    }

    boolean hasTotal() {
        return this.totalWidth > 0;
    }

    boolean hasOrders() {
        return this.ordersWidth > 0;
    }

    boolean hasDepth() {
        return this.hasTotal() || this.quantityBar;
    }

    int priceX(OrderSide side) {
        return side == OrderSide.Buy ? this.inset : this.width - this.inset - this.priceWidth;
    }

    int priceTextX(OrderSide side, int textWidth) {
        return this.priceX(side) + this.priceWidth - textWidth;
    }

    int quantityX(OrderSide side) {
        if (this.quantityBar) {
            return side == OrderSide.Buy ? this.width - this.inset - this.quantityWidth : this.inset;
        }
        return side == OrderSide.Buy
            ? this.inset + this.priceWidth + this.gap
            : this.priceX(side) - this.gap - this.quantityWidth;
    }

    int quantityTextX(OrderSide side, int textWidth) {
        if (this.quantityBar) {
            return this.quantityX(side) + 3
                + (side == OrderSide.Buy ? this.quantityWidth - 6 - textWidth : 0);
        }
        return this.quantityX(side) + this.quantityWidth - textWidth;
    }

    int ordersX(OrderSide side) {
        if (this.quantityBar) {
            return side == OrderSide.Buy
                ? this.priceX(side) + this.priceWidth + this.gap
                : this.priceX(side) - this.gap - this.ordersWidth;
        }
        return side == OrderSide.Buy
            ? this.quantityX(side) + this.quantityWidth + this.gap
            : this.quantityX(side) - this.gap - this.ordersWidth;
    }

    int ordersTextX(OrderSide side, int textWidth) {
        return this.ordersX(side) + this.ordersWidth - textWidth;
    }

    int totalX(OrderSide side) {
        return side == OrderSide.Buy ? this.width - this.inset - this.totalWidth : this.inset;
    }

    int totalTextX(OrderSide side, int textWidth) {
        return this.totalX(side) + 3 + (side == OrderSide.Buy ? this.totalWidth - 6 - textWidth : 0);
    }

    int barX(OrderSide side) {
        return this.hasTotal() ? this.totalX(side) : this.quantityX(side);
    }

    int barWidth() {
        return this.hasTotal() ? this.totalWidth : this.quantityWidth;
    }

    record Widths(int price, int quantity, int orders) {}
}
