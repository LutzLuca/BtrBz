package com.github.lutzluca.btrbz.core.widgets.pricedifference;

import com.github.lutzluca.btrbz.core.widgets.WidgetView;
import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarStyles;
import com.github.lutzluca.btrbz.core.widgets.ui.RetainedFlowLayout;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetDisplayOptions;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetLayoutTokens;
import io.wispforest.owo.ui.component.ItemComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;

import static com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi.icon;
import static com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi.spacer;
import static com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi.text;

final class PriceDifferenceWidgetView implements WidgetView<PriceDifferenceWidgetData.Snapshot, PriceDifferenceWidgetConfig, Void> {
    private final RetainedFlowLayout root = RetainedFlowLayout.vertical(Sizing.fixed(1), Sizing.content());
    private final RetainedFlowLayout product = RetainedFlowLayout.horizontal(Sizing.fill(100), Sizing.content());
    private final ItemComponent item = icon(net.minecraft.world.item.ItemStack.EMPTY);
    private final LabelComponent productName = text("", BazaarStyles.PRIMARY_TEXT);
    private final ValueLine perItem = new ValueLine(0);
    private final ValueLine total = new ValueLine(1);

    PriceDifferenceWidgetView() {
        this.root.allowOverflow(true);
        this.root.gap(WidgetLayoutTokens.LINE_GAP);
        this.product.verticalAlignment(VerticalAlignment.CENTER);
        this.product.gap(3);
    }

    @Override
    public UIComponent root() {
        return this.root;
    }

    @Override
    public void update(
        PriceDifferenceWidgetData.Snapshot data,
        PriceDifferenceWidgetConfig config,
        WidgetSession session,
        Consumer<Void> actions
    ) {
        this.root.horizontalSizing(Sizing.fixed(config.contentWidth));
        this.item.stack(data.iconCopy());
        this.productName.text(Component.literal(data.productName()));
        this.product.clearChildren();
        if (config.showItems) this.product.child(this.item);
        this.product.child(this.productName);
        int color = data.total() >= 0 ? BazaarStyles.BUY_ACCENT : BazaarStyles.STATUS_UNDERCUT;
        this.perItem.update("Per item", signed(data.perItem(), config.numberStyle), color);
        this.total.update(
            "Total (" + BazaarWidgetViewData.formatInt(data.quantity()) + " items)",
            signed(data.total(), config.numberStyle),
            color
        );
        this.root.clearChildren();
        if (config.showProduct) this.root.child(this.product);
        if (config.display != PriceDifferenceWidgetConfig.DiffDisplay.Total) this.root.child(this.perItem.root);
        if (config.display != PriceDifferenceWidgetConfig.DiffDisplay.PerItem) this.root.child(this.total.root);
    }

    private static String number(long value, WidgetDisplayOptions.NumberStyle style) {
        return style == WidgetDisplayOptions.NumberStyle.Compact
            ? BazaarWidgetViewData.formatCompact(value)
            : BazaarWidgetViewData.formatInt(value);
    }

    private static String signed(long value, WidgetDisplayOptions.NumberStyle style) {
        return (value >= 0 ? "+" : "") + number(value, style);
    }

    private static final class ValueLine {
        private final FlowLayout root = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        private final LabelComponent label = text("", BazaarStyles.SECONDARY_TEXT);
        private final LabelComponent value = text("", BazaarStyles.PRIMARY_TEXT);

        private ValueLine(int labelInset) {
            this.label.margins(Insets.left(labelInset));
            this.root.child(this.label);
            this.root.child(spacer());
            this.root.child(this.value);
        }

        private void update(String label, String value, int color) {
            this.label.text(Component.literal(label));
            this.value.text(Component.literal(value));
            this.value.color(BazaarStyles.color(color));
        }
    }
}
