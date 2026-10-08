package com.github.lutzluca.btrbz.core.widgets.orderbook;

import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import com.github.lutzluca.btrbz.core.widgets.WidgetActionHandler;

public final class OrderBookActionHandler implements WidgetActionHandler<OrderBookAction> {
    private final OrderBookPriceComponent embeddedWorkflow;

    public OrderBookActionHandler(OrderBookPriceComponent embeddedWorkflow) {
        this.embeddedWorkflow = embeddedWorkflow;
    }

    @Override
    public void handle(OrderBookAction action, WidgetSession source, WidgetSession current) {
        if (!source.sameWorkflow(current) || !current.inSign()) {
            return;
        }

        switch (action) {
            case OrderBookAction.SelectPrice select -> {
                this.embeddedWorkflow.selectPrice(select.price(), select.copyOnly());
            }
        }
    }
}
