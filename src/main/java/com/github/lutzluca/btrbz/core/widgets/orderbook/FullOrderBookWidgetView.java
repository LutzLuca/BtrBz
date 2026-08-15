package com.github.lutzluca.btrbz.core.widgets.orderbook;

import com.github.lutzluca.btrbz.core.widgets.WidgetView;
import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarOrderListComponent;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarOrderRowComponent;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarStyles;
import com.github.lutzluca.btrbz.core.widgets.ui.RetainedFlowLayout;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetDisplayOptions;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetLayoutTokens;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.ItemComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.core.Insets;
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

final class FullOrderBookWidgetView
    implements WidgetView<OrderBookWidgetData.Snapshot, OrderBookWidgetConfig, OrderBookAction> {
    private final RetainedFlowLayout root = RetainedFlowLayout.vertical(Sizing.fixed(1), Sizing.content());

    private final RetainedFlowLayout header = RetainedFlowLayout.horizontal(Sizing.fill(100), Sizing.content());
    private @Nullable ItemComponent item;
    private final LabelComponent itemName = label("", BazaarStyles.PRIMARY_TEXT);
    private final LabelComponent bookTitle = label("Order Book", BazaarStyles.MUTED_TEXT);

    private final RetainedFlowLayout lists = RetainedFlowLayout.horizontal(Sizing.fill(100), Sizing.content());
    private final Side buy = new Side("Buy Offers", BazaarWidgetViewData.OrderSide.Buy);
    private final Side sell = new Side("Sell Offers", BazaarWidgetViewData.OrderSide.Sell);

    private final RetainedFlowLayout footer = RetainedFlowLayout.horizontal(Sizing.fill(100), Sizing.content());
    private final LabelComponent instruction = label(
        "Click a price to copy it and return",
        BazaarStyles.MUTED_TEXT);
    private final ButtonComponent goBack = UIComponents.button(
        Component.literal("Go Back"),
        _ -> this.actions.accept(new OrderBookAction.GoBack()));

    private Consumer<OrderBookAction> actions = _ -> {};

    FullOrderBookWidgetView() {
        this.root.allowOverflow(true);
        this.root.gap(0);

        this.header.allowOverflow(true);
        this.header.verticalAlignment(VerticalAlignment.CENTER);
        this.header.gap(WidgetLayoutTokens.HEADER_GAP);

        this.lists.allowOverflow(true);
        this.lists.gap(2);

        this.footer.allowOverflow(true);
        this.footer.padding(Insets.top(WidgetLayoutTokens.SECTION_GAP));
        this.footer.gap(WidgetLayoutTokens.HEADER_GAP);
        this.footer.verticalAlignment(VerticalAlignment.CENTER);

        this.instruction.horizontalSizing(Sizing.expand(100));

        this.goBack.sizing(Sizing.fixed(60), Sizing.fixed(16));
        this.goBack.renderer(ButtonComponent.Renderer.flat(0xFF2C3340, 0xFF384252, 0xFF20242D));
        this.goBack.textShadow(false);

        this.footer.child(this.instruction);
        this.footer.child(this.goBack);
    }

    @Override
    public UIComponent root() {
        return this.root;
    }

    @Override
    public void update(
        OrderBookWidgetData.Snapshot data,
        OrderBookWidgetConfig config,
        WidgetSession session,
        Consumer<OrderBookAction> actions
    ) {
        this.actions = actions;
        this.root.horizontalSizing(Sizing.fixed(OrderBookWidget.contentWidth(config)));

        this.itemName.text(Component.literal(data.itemName()));
        this.header.clearChildren();

        var itemStack = data.itemStack();

        if (itemStack.isPresent()) {
            var stack = itemStack.orElseThrow();

            if (this.item == null) {
                this.item = icon(stack);
            } else {
                this.item.stack(stack);
            }

            this.header.child(this.item);
        }

        this.header.child(this.itemName);
        this.header.child(this.bookTitle);

        int rowHeight = rowHeight();
        int viewportHeight = WidgetLayoutTokens.listViewportHeight(rowHeight, config.visibleRows);
        int sideHeight = Minecraft.getInstance().font.lineHeight + WidgetLayoutTokens.LINE_GAP + viewportHeight;
        boolean compactMetadata = OrderBookWidget.sideWidth(config) < 190;

        this.lists.verticalSizing(Sizing.fixed(sideHeight));
        this.buy.update(data.buyOffers(), config, compactMetadata, rowHeight, viewportHeight, actions);
        this.sell.update(data.sellOffers(), config, compactMetadata, rowHeight, viewportHeight, actions);

        this.lists.clearChildren();

        if (config.layout != OrderBookWidgetConfig.BookLayout.SellOnly) {
            this.lists.child(this.buy.root);
        }

        if (config.layout != OrderBookWidgetConfig.BookLayout.BuyOnly) {
            this.lists.child(this.sell.root);
        }

        this.root.clearChildren();
        this.root.child(this.header);
        this.root.child(this.lists);
        this.root.child(this.footer);
    }

    private static int rowHeight() {
        return WidgetLayoutTokens.singleLineRowHeight(Minecraft.getInstance().font.lineHeight);
    }

    private static String number(long value, WidgetDisplayOptions.NumberStyle style) {
        return style == WidgetDisplayOptions.NumberStyle.Compact
            ? BazaarWidgetViewData.formatCompact(value)
            : BazaarWidgetViewData.formatInt(value);
    }

    private static final class Side {
        private final String title;
        private final BazaarWidgetViewData.OrderSide side;

        private final RetainedFlowLayout root = RetainedFlowLayout.vertical(Sizing.expand(50), Sizing.fill(100));
        private final BazaarOrderListComponent list = new BazaarOrderListComponent(true, 1, 1);

        private Side(String title, BazaarWidgetViewData.OrderSide side) {
            this.title = title;
            this.side = side;

            this.root.allowOverflow(true);
            this.root.gap(0);

            this.root.child(label(title, BazaarStyles.SECONDARY_TEXT));
            this.root.child(this.list);
        }

        private void update(
            List<OrderBookWidgetData.Entry> entries,
            OrderBookWidgetConfig config,
            boolean compactMetadata,
            int rowHeight,
            int viewportHeight,
            Consumer<OrderBookAction> actions
        ) {
            this.root.horizontalSizing(config.layout == OrderBookWidgetConfig.BookLayout.Split
                ? Sizing.expand(50)
                : Sizing.fill(100));

            var rows = new ArrayList<BazaarOrderRowComponent.BazaarRow>();

            for (int index = 0; index < entries.size(); index++) {
                var entry = entries.get(index);

                String metadata = compactMetadata
                    ? number(entry.quantity(), config.numberStyle) + "v"
                        + (config.showOrderCount ? " · " + entry.orders() + "o" : "")
                    : "Volume: " + number(entry.quantity(), config.numberStyle)
                        + (config.showOrderCount ? " · Orders: " + entry.orders() : "");

                rows.add(new BazaarOrderRowComponent.BazaarRow(
                    this.side.name() + "-" + Double.doubleToLongBits(entry.price()) + "-" + index,
                    entry.priceText(), entry.side().accentColor(), "", metadata,
                    BazaarStyles.MUTED_TEXT, 0, List.of(),
                    copyOnly -> actions.accept(new OrderBookAction.SelectPrice(entry.price(), copyOnly)),
                    true));
            }

            this.list.update(rows, true, rowHeight, viewportHeight);
        }
    }
}
