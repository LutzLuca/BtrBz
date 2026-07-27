package com.github.lutzluca.btrbz.core.widgets.ordervalue;

import com.github.lutzluca.btrbz.core.widgets.WidgetView;
import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarStyles;
import com.github.lutzluca.btrbz.core.widgets.ui.RetainedFlowLayout;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetDisplayOptions;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetLayoutTokens;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;

import static com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi.boldLabel;
import static com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi.label;
import static com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi.spacer;

final class OrderValueWidgetView implements WidgetView<OrderValueWidgetData.Snapshot, OrderValueWidgetConfig, Void> {
    private final RetainedFlowLayout root = RetainedFlowLayout.vertical(Sizing.fixed(1), Sizing.content());
    private final LabelComponent header = label("Bazaar Overview", BazaarStyles.SELL_ACCENT);
    private final ValueLine buyLocked = new ValueLine("Buy Orders (Locked)", false);
    private final ValueLine buyItems = new ValueLine("Buy Orders (Items)", false);
    private final ValueLine sellClaimable = new ValueLine("Sell Offers (Claimable)", false);
    private final ValueLine sellPending = new ValueLine("Sell Offers (Pending)", false);
    private final ValueLine total = new ValueLine("Total Worth", true);

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
        this.root.horizontalSizing(Sizing.fixed(config.contentWidth()));
        int buyColor = semantic(config.colorMode(), BazaarStyles.BUY_ACCENT);
        int sellColor = semantic(config.colorMode(), BazaarStyles.SELL_ACCENT);
        this.buyLocked.update(data.buyLocked(), buyColor, config);
        this.buyItems.update(data.buyItems(), buyColor, config);
        this.sellClaimable.update(data.sellClaimable(), sellColor, config);
        this.sellPending.update(data.sellPending(), sellColor, config);
        this.total.update(data.total(), BazaarStyles.PRIMARY_TEXT, config);

        this.root.clearChildren();
        this.root.child(this.header);
        if (config.display() == OrderValueWidgetConfig.ValueDisplay.Detailed) {
            if (config.buyLocked()) this.root.child(this.buyLocked.root);
            if (config.buyItems()) this.root.child(this.buyItems.root);
            if (config.sellClaimable()) this.root.child(this.sellClaimable.root);
            if (config.sellPending()) this.root.child(this.sellPending.root);
        }
        this.root.child(this.total.root);
    }

    private static String number(long value, WidgetDisplayOptions.NumberStyle style) {
        return style == WidgetDisplayOptions.NumberStyle.Compact
            ? BazaarWidgetViewData.formatCompact(value)
            : BazaarWidgetViewData.formatInt(value);
    }

    private static int semantic(OrderValueWidgetConfig.ColorMode mode, int semanticColor) {
        return mode == OrderValueWidgetConfig.ColorMode.Semantic
            ? semanticColor
            : BazaarStyles.SECONDARY_TEXT;
    }

    private static final class ValueLine {
        private final FlowLayout root = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        private final LabelComponent value;

        private ValueLine(String name, boolean bold) {
            this.root.allowOverflow(true);
            this.root.child(label(name, BazaarStyles.SECONDARY_TEXT));
            this.root.child(spacer());
            this.value = bold ? boldLabel("", BazaarStyles.PRIMARY_TEXT) : label("", BazaarStyles.PRIMARY_TEXT);
            this.root.child(this.value);
        }

        private void update(long amount, int color, OrderValueWidgetConfig config) {
            String text = number(amount, config.numberStyle()) + (config.showCoinsSuffix() ? " coins" : "");
            this.value.text(Component.literal(text).setStyle(this.value.text().getStyle()));
            this.value.color(BazaarStyles.color(color));
        }
    }
}
