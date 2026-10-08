package com.github.lutzluca.btrbz.core.widgets.pricedifference;

import com.github.lutzluca.btrbz.core.ui.UiComponents;

import com.github.lutzluca.btrbz.core.ui.UiStyles;

import com.github.lutzluca.btrbz.core.widgets.WidgetView;
import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import com.github.lutzluca.btrbz.core.widgets.ui.RetainedFlowLayout;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetLayoutTokens;
import com.github.lutzluca.btrbz.utils.Utils;
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
import org.jetbrains.annotations.Nullable;

final class PriceDifferenceWidgetView
    implements WidgetView<PriceDifferenceWidgetData.Snapshot, PriceDifferenceWidgetConfig, Void> {
    private final RetainedFlowLayout root = RetainedFlowLayout.vertical(Sizing.fixed(1), Sizing.content());

    private final RetainedFlowLayout product = RetainedFlowLayout.horizontal(Sizing.fill(100), Sizing.content());
    private @Nullable ItemComponent item;
    private final LabelComponent productName = UiComponents.label("", UiStyles.palette().primary());

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
        this.productName.text(data.productName());
        this.product.clearChildren();

        var itemStack = data.itemStack();

        if (itemStack.isPresent()) {
            var stack = itemStack.orElseThrow();

            if (this.item == null) {
                this.item = UiComponents.icon(stack);
            } else {
                this.item.stack(stack);
            }

            this.product.child(this.item);
        }

        this.product.child(this.productName);

        String perItemValue = signed(data.perItem());
        var totalLabel = Component.literal("Total (").withStyle(UiStyles.label())
            .append(Component.literal(BazaarWidgetViewData.formatInt(data.quantity())).withStyle(UiStyles.quantity()))
            .append(Component.literal(" items)").withStyle(UiStyles.label()));
        String totalValue = signed(data.total());

        this.perItem.update(Component.literal("Per item"), perItemValue);
        this.total.update(totalLabel, totalValue);

        this.root.horizontalSizing(Sizing.fixed(config.contentWidth));
        this.root.clearChildren();
        this.root.child(this.product);
        this.root.child(this.perItem.root);
        this.root.child(this.total.root);
    }

    private static String signed(double value) {
        return (value >= 0 ? "+" : "") + Utils.formatDecimal(value, 1, true);
    }

    private static final class ValueLine {
        private final FlowLayout root = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        private final LabelComponent label = UiComponents.label("", UiStyles.palette().label());
        private final LabelComponent value = UiComponents.label("", UiStyles.palette().primary());

        private ValueLine(int labelInset) {
            this.label.margins(Insets.left(labelInset));

            this.root.child(this.label);
            this.root.child(UiComponents.spacer());
            this.root.child(this.value);
        }

        private void update(Component label, String value) {
            this.label.text(label);
            this.value.text(Component.literal(value).withStyle(UiStyles.money()));
        }
    }
}
