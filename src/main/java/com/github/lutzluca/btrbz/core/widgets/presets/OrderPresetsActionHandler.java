package com.github.lutzluca.btrbz.core.widgets.presets;

import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import com.github.lutzluca.btrbz.core.widgets.WidgetActionHandler;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;

public final class OrderPresetsActionHandler implements WidgetActionHandler<OrderPresetsAction> {
    private final OrderPresetsComponent presets;

    public OrderPresetsActionHandler(OrderPresetsComponent presets) {
        this.presets = presets;
    }

    @Override
    public void handle(OrderPresetsAction action, WidgetSession source, WidgetSession current) {
        boolean eligible = current.inBazaarMenu(BazaarMenuType.BuyOrderSetupVolume)
            || current.inSign()
                && current.previousBazaarMenu(BazaarMenuType.BuyOrderSetupVolume)
                && this.presets.inTransaction();

        if (!eligible || !source.sameWorkflow(current)) {
            return;
        }

        switch (action) {
            case OrderPresetsAction.Apply apply -> this.presets.apply(apply.preset());
        }
    }
}
