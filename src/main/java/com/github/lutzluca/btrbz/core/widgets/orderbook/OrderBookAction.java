package com.github.lutzluca.btrbz.core.widgets.orderbook;

public sealed interface OrderBookAction permits OrderBookAction.SelectPrice, OrderBookAction.GoBack {
    record SelectPrice(double price, boolean copyOnly) implements OrderBookAction {}

    record GoBack() implements OrderBookAction {}
}
