package com.github.lutzluca.btrbz.core.widgets.presets;

import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import com.github.lutzluca.btrbz.core.widgets.WidgetActionHandler;
import com.github.lutzluca.btrbz.screen.ScreenTracker.BazaarMenuType;

public final class OrderPresetsActionHandler implements WidgetActionHandler<OrderPresetsAction> {
    private final OrderPresetsComponent presets;

    public OrderPresetsActionHandler(OrderPresetsComponent presets) {
        this.presets = presets;
    }

    @Override
    public void handle(OrderPresetsAction action, WidgetSession current) {
        if (!canApply(current, this.presets.inTransaction())) {
            return;
        }

        switch (action) {
            case OrderPresetsAction.Apply apply -> this.presets.apply(apply.preset());
        }
    }

    static boolean canApply(WidgetSession current, boolean inTransaction) {
        return current.inBazaarMenu(BazaarMenuType.BuyOrderSetupVolume)
            || current.inSign()
                && current.previousBazaarMenu(BazaarMenuType.BuyOrderSetupVolume)
                && inTransaction;
    }
}
