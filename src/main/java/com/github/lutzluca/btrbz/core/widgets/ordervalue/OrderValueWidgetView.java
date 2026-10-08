package com.github.lutzluca.btrbz.core.widgets.ordervalue;

import com.github.lutzluca.btrbz.core.ui.UiComponents;

import com.github.lutzluca.btrbz.core.ui.UiStyles;

import com.github.lutzluca.btrbz.core.widgets.WidgetView;
import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import com.github.lutzluca.btrbz.core.widgets.ui.RetainedFlowLayout;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetLayoutTokens;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;

final class OrderValueWidgetView implements WidgetView<OrderValueWidgetData.Snapshot, OrderValueWidgetConfig, Void> {
    private final RetainedFlowLayout root = RetainedFlowLayout.vertical(Sizing.fixed(1), Sizing.content());
    private final LabelComponent header = UiComponents.boldLabel("Bazaar Overview", UiStyles.palette().primary());

    private final ValueLine buyLocked = new ValueLine("Buy Orders (Locked)");
    private final ValueLine buyItems = new ValueLine("Buy Orders (Items)");

    private final ValueLine sellClaimable = new ValueLine("Sell Offers (Claimable)");
    private final ValueLine sellPending = new ValueLine("Sell Offers (Pending)");

    private final ValueLine total = new ValueLine("Total Worth");

    OrderValueWidgetView() {
        this.root.allowOverflow(true);
        this.root.gap(WidgetLayoutTokens.LINE_GAP);
    }

    @Override
    public UIComponent root() {
        return this.root;
    }

    @Override
    public void update(
        OrderValueWidgetData.Snapshot data,
        OrderValueWidgetConfig config,
        WidgetSession session,
        Consumer<Void> actions
    ) {
        this.buyLocked.update(data.buyLocked());
        this.buyItems.update(data.buyItems());

        this.sellClaimable.update(data.sellClaimable());
        this.sellPending.update(data.sellPending());

        this.total.update(data.total());

        this.root.horizontalSizing(Sizing.fixed(config.contentWidth));
        this.root.clearChildren();
        this.root.child(this.header);

        if (config.display == OrderValueWidgetConfig.ValueDisplay.Detailed) {
            if (data.buyLocked() != 0) {
                this.root.child(this.buyLocked.root);
            }

            if (data.buyItems() != 0) {
                this.root.child(this.buyItems.root);
            }

            if (data.sellClaimable() != 0) {
                this.root.child(this.sellClaimable.root);
            }

            if (data.sellPending() != 0) {
                this.root.child(this.sellPending.root);
            }
        }

        this.root.child(this.total.root);
    }

    private static String number(long value) {
        return BazaarWidgetViewData.formatCompact(value);
    }

    private static final class ValueLine {
        private final FlowLayout root = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        private final LabelComponent value;

        private ValueLine(String name) {
            this.root.allowOverflow(true);

            this.root.child(UiComponents.label(name, UiStyles.palette().label()));
            this.root.child(UiComponents.spacer());

            this.value = UiComponents.label("", UiStyles.palette().primary());

            this.root.child(this.value);
        }

        private void update(long amount) {
            String text = number(amount) + " coins";

            this.value.text(Component.literal(text).withStyle(UiStyles.money()));
        }
    }
}
