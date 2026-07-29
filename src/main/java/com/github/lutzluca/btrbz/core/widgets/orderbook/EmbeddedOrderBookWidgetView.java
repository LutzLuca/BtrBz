package com.github.lutzluca.btrbz.core.widgets.orderbook;

import com.github.lutzluca.btrbz.core.widgets.WidgetView;
import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarOrderListComponent;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarOrderRowComponent;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarStyles;
import com.github.lutzluca.btrbz.core.widgets.ui.RetainedFlowLayout;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetLayoutTokens;
import io.wispforest.owo.ui.component.ItemComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import static com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi.icon;
import static com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi.label;
import static com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi.text;

final class EmbeddedOrderBookWidgetView implements WidgetView<
    OrderBookWidgetData.Snapshot, OrderBookPriceWidgetConfig, OrderBookAction
> {
    private final RetainedFlowLayout root = RetainedFlowLayout.vertical(Sizing.fixed(1), Sizing.content());
    private final RetainedFlowLayout header = RetainedFlowLayout.horizontal(Sizing.fill(100), Sizing.content());
    private @Nullable ItemComponent item;
    private final LabelComponent itemName = text("", BazaarStyles.PRIMARY_TEXT);
    private final LabelComponent prices = text("Prices", BazaarStyles.MUTED_TEXT);
    private final RetainedFlowLayout sides = RetainedFlowLayout.horizontal(Sizing.fill(100), Sizing.content());
    private final Side buy = new Side("Buy", BazaarStyles.BUY_ACCENT, BazaarWidgetViewData.OrderSide.Buy);
    private final Side sell = new Side("Sell", BazaarStyles.SELL_ACCENT, BazaarWidgetViewData.OrderSide.Sell);

    EmbeddedOrderBookWidgetView() {
        this.root.allowOverflow(true);
        this.root.gap(0);
        this.header.verticalAlignment(VerticalAlignment.CENTER);
        this.header.gap(3);
        this.sides.gap(4);
    }

    @Override
    public UIComponent root() {
        return this.root;
    }

    @Override
    public void update(
        OrderBookWidgetData.Snapshot data,
        OrderBookPriceWidgetConfig config,
        WidgetSession session,
        Consumer<OrderBookAction> actions
    ) {
        this.root.horizontalSizing(Sizing.fixed(OrderBookWidget.embeddedContentWidth(config, data)));
        this.itemName.text(Component.literal(data.itemName()));
        this.header.clearChildren();
        var itemStack = data.itemStack();
        if (config.showItem && itemStack.isPresent()) {
            var stack = itemStack.orElseThrow();
            if (this.item == null) {
                this.item = icon(stack);
            } else {
                this.item.stack(stack);
            }
            this.header.child(this.item);
        }
        this.header.child(this.itemName);
        this.header.child(this.prices);

        boolean single = OrderBookWidget.embeddedVisibleSideCount(config, data) <= 1;
        this.buy.update(data.buyOffers(), config, single, actions);
        this.sell.update(data.sellOffers(), config, single, actions);
        this.sides.clearChildren();
        if (OrderBookWidget.showsEmbeddedSide(config, data, BazaarWidgetViewData.OrderSide.Buy)) {
            this.sides.child(this.buy.root);
        }
        if (OrderBookWidget.showsEmbeddedSide(config, data, BazaarWidgetViewData.OrderSide.Sell)) {
            this.sides.child(this.sell.root);
        }

        this.root.clearChildren();
        if (config.showHeader) this.root.child(this.header);
        this.root.child(this.sides);
    }

    private static int rowHeight() {
        return WidgetLayoutTokens.singleLineRowHeight(Minecraft.getInstance().font.lineHeight);
    }

    private static final class Side {
        private final int color;
        private final BazaarWidgetViewData.OrderSide side;
        private final RetainedFlowLayout root = RetainedFlowLayout.vertical(Sizing.expand(50), Sizing.content());
        private final BazaarOrderListComponent list = new BazaarOrderListComponent(true, 1, 1);

        private Side(String title, int color, BazaarWidgetViewData.OrderSide side) {
            this.color = color;
            this.side = side;
            this.root.gap(0);
            this.root.child(label(title, color));
            this.root.child(this.list);
        }

        private void update(
            List<OrderBookWidgetData.Entry> entries,
            OrderBookPriceWidgetConfig config,
            boolean single,
            Consumer<OrderBookAction> actions
        ) {
            this.root.horizontalSizing(single ? Sizing.fill(100) : Sizing.expand(50));
            var rows = new ArrayList<BazaarOrderRowComponent.BazaarRow>();
            for (int index = 0; index < entries.size(); index++) {
                var entry = entries.get(index);
                rows.add(new BazaarOrderRowComponent.BazaarRow(
                    this.side.name() + "-" + Double.doubleToLongBits(entry.price()) + "-" + index,
                    entry.priceText(), this.color, "", OrderBookWidget.embeddedMetadata(entry, config),
                    BazaarStyles.MUTED_TEXT, 0,
                    List.of(Component.literal("Click to use " + entry.priceText() + "; Ctrl-click to copy")),
                    copyOnly -> actions.accept(new OrderBookAction.SelectPrice(entry.price(), copyOnly)),
                    true
                ));
            }
            int rowHeight = rowHeight();
            this.list.update(
                rows,
                true,
                rowHeight,
                WidgetLayoutTokens.listViewportHeight(rowHeight, config.visibleRows)
            );
        }
    }
}
