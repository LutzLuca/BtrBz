package com.github.lutzluca.btrbz.core.widgets.orderbook;

public sealed interface OrderBookAction permits OrderBookAction.SelectPrice {
    record SelectPrice(double price, boolean copyOnly) implements OrderBookAction {}
}
