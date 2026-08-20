package com.github.lutzluca.btrbz.core.widgets.orderbook;

import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.Utils;
import com.github.lutzluca.btrbz.core.widgets.WidgetActionHandler;
import net.minecraft.client.Minecraft;

public final class OrderBookActionHandler implements WidgetActionHandler<OrderBookAction> {
    private final OrderBookPriceComponent embeddedWorkflow;

    public OrderBookActionHandler(OrderBookPriceComponent embeddedWorkflow) {
        this.embeddedWorkflow = embeddedWorkflow;
    }

    @Override
    public void handle(OrderBookAction action, WidgetSession source, WidgetSession current) {
        if (!source.sameWorkflow(current) || !(current.inSign() || current.inOrderBook())) {
            return;
        }

        switch (action) {
            case OrderBookAction.SelectPrice select -> {
                if (current.inOrderBook()) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(
                        Utils.formatDecimal(select.price(), 1, false));

                    var screen = GameUtils.screen();

                    if (screen != null) {
                        screen.onClose();
                    }
                } else {
                    this.embeddedWorkflow.selectPrice(select.price(), select.copyOnly());
                }
            }
            case OrderBookAction.GoBack _ -> {
                if (!current.inOrderBook()) {
                    return;
                }

                var screen = GameUtils.screen();

                if (screen != null) {
                    screen.onClose();
                }
            }
        }
    }
}
