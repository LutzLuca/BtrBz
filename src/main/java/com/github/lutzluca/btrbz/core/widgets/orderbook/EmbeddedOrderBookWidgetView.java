package com.github.lutzluca.btrbz.core.widgets.orderbook;

import com.github.lutzluca.btrbz.core.ui.RetainedTextRow;
import com.github.lutzluca.btrbz.core.ui.UiComponents;
import com.github.lutzluca.btrbz.core.ui.UiStyles;
import com.github.lutzluca.btrbz.core.widgets.WidgetView;
import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData.OrderSide;
import com.github.lutzluca.btrbz.core.widgets.orderbook.OrderBookDepth.Level;
import com.github.lutzluca.btrbz.core.widgets.orderbook.OrderBookWidgetConfig.DepthMode;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import com.github.lutzluca.btrbz.core.widgets.ui.RetainedFlowLayout;
import com.github.lutzluca.btrbz.core.widgets.ui.RetainedRows;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetLayoutTokens;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetScrollListComponent;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import io.wispforest.owo.ui.base.BaseParentUIComponent;
import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.component.ItemComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.ParentUIComponent;
import io.wispforest.owo.ui.core.Size;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/** Dense numeric order book whose price actions belong to the sign workflow. */
final class EmbeddedOrderBookWidgetView
    implements WidgetView<OrderBookWidgetData.Snapshot, OrderBookPriceWidgetConfig, OrderBookAction> {
    private static final int SIDE_GAP = 4;
    private static final int COLUMN_GAP = 4;
    private static final int SCROLLBAR_THICKNESS = 2;
    private static final int INSET = WidgetLayoutTokens.ROW_HORIZONTAL_PADDING + SCROLLBAR_THICKNESS;
    private final RetainedFlowLayout root = RetainedFlowLayout.vertical(Sizing.fixed(1), Sizing.content());
    private final RetainedFlowLayout header = RetainedFlowLayout.horizontal(Sizing.fill(100), Sizing.content());
    private @Nullable ItemComponent item;
    private final LabelComponent itemName = UiComponents.label("", UiStyles.palette().primary());
    private final LabelComponent sideTitle = UiComponents.label("", UiStyles.palette().buy());
    private final RetainedFlowLayout sides = RetainedFlowLayout.horizontal(Sizing.fill(100), Sizing.content());
    private final RetainedFlowLayout stackedSides = RetainedFlowLayout.vertical(Sizing.fill(100), Sizing.content());
    private final Side buy = new Side(OrderSide.Buy);
    private final Side sell = new Side(OrderSide.Sell);
    private OrderBookWidgetData.Snapshot data;
    private OrderBookDepth book;
    private OrderBookPriceWidgetConfig config;
    private OrderType workflowSide;
    private Consumer<OrderBookAction> actions = _ -> {};
    private boolean hasItem;

    EmbeddedOrderBookWidgetView() {
        this.root.allowOverflow(true);
        this.header.verticalAlignment(VerticalAlignment.CENTER);
        this.header.gap(3);
        this.header.padding(Insets.of(0, 2, INSET, INSET));
        this.itemName.horizontalSizing(Sizing.expand(100));
        this.sides.gap(SIDE_GAP);
        this.stackedSides.gap(SIDE_GAP);
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
        this.data = data;
        this.book = OrderBookDepth.from(data, DepthMode.Hidden);
        this.config = config;
        this.workflowSide = session.side().orElseThrow();
        this.actions = actions;
        this.itemName.text(data.formattedItemName());
        this.header.clearChildren();
        var itemStack = data.itemStack();
        this.hasItem = itemStack.isPresent();
        if (itemStack.isPresent()) {
            var stack = itemStack.orElseThrow();
            if (this.item == null) {
                this.item = UiComponents.icon(stack);
            } else {
                this.item.stack(stack);
            }
            this.header.child(this.item);
        }
        this.header.child(this.itemName);
        if (OrderBookWidget.embeddedVisibleSideCount(config, data) == 1) {
            var side = data.appropriateSide().orElseThrow();
            this.sideTitle.text(Component.literal(title(side)).withStyle(UiStyles.color(side.accentColor())));
            this.header.child(this.sideTitle);
        }
    }

    @Override
    public void updateLayout(Size availableSpace) {
        boolean showBuy = OrderBookWidget.showsEmbeddedSide(this.config, this.data, OrderSide.Buy);
        boolean showSell = OrderBookWidget.showsEmbeddedSide(this.config, this.data, OrderSide.Sell);
        boolean single = !showBuy || !showSell;
        var font = Minecraft.getInstance().font;
        var measured = OrderBookColumns.measure(showBuy ? this.data.buyOffers() : List.of(),
            showSell ? this.data.sellOffers() : List.of(), font::width);
        int preferredContentWidth = OrderBookWidget.embeddedContentWidth(this.config, this.data);
        int preferredWidth = single ? preferredContentWidth : Math.max(1, (preferredContentWidth - SIDE_GAP) / 2);
        int minimumWidth = OrderBookColumns.numeric(1, measured, this.config.showOrderCount, INSET, COLUMN_GAP).width();
        boolean stacked = !single && minimumWidth * 2 + SIDE_GAP > availableSpace.width();
        int fittingWidth = single || stacked ? availableSpace.width() : (availableSpace.width() - SIDE_GAP) / 2;
        var columns = OrderBookColumns.numeric(Math.min(preferredWidth, Math.max(1, fittingWidth)),
            measured, this.config.showOrderCount, INSET, COLUMN_GAP);
        int width = single || stacked ? columns.width() : columns.width() * 2 + SIDE_GAP;
        this.root.horizontalSizing(Sizing.fixed(width));
        int nameWidth = Math.max(1, width - INSET * 2 - (this.hasItem ? 16 + this.header.gap() : 0)
            - (single ? font.width(this.sideTitle.text()) + this.header.gap() : 0));
        this.itemName.maxWidth(nameWidth);
        int nameLines = Math.max(1, font.split(this.itemName.text(), nameWidth).size());
        int nameHeight = nameLines * (this.itemName.lineHeight() + this.itemName.lineSpacing())
            - this.itemName.lineSpacing();
        int headerHeight = Math.max(this.hasItem ? 16 : 0, nameHeight) + this.header.padding().get().vertical();
        int columnHeaderHeight = font.lineHeight + 4 + (single ? 0 : font.lineHeight + 1);
        int availableHeight = availableSpace.height() - headerHeight;
        int viewportSpace = (stacked ? (availableHeight - SIDE_GAP) / 2 : availableHeight) - columnHeaderHeight;
        int rowHeight = WidgetLayoutTokens.singleLineRowHeight(font.lineHeight);
        int fittingRows = Math.max(1, (viewportSpace + WidgetLayoutTokens.LIST_GAP)
            / (rowHeight + WidgetLayoutTokens.LIST_GAP));
        int levelCount = Math.max(showBuy ? this.book.buy().size() : 0, showSell ? this.book.sell().size() : 0);
        int visibleRows = Math.min(Math.max(1, levelCount),
            Math.min(Math.max(1, this.config.visibleRows), fittingRows));
        int viewportHeight = WidgetLayoutTokens.listViewportHeight(rowHeight, visibleRows);
        this.buy.update(this.book.buy(), columns, rowHeight, viewportHeight, columnHeaderHeight, single,
            this.workflowSide, this.actions);
        this.sell.update(this.book.sell(), columns, rowHeight, viewportHeight, columnHeaderHeight, single,
            this.workflowSide, this.actions);
        this.sides.clearChildren();
        this.stackedSides.clearChildren();
        var tables = stacked ? this.stackedSides : this.sides;
        if (showBuy) {
            tables.child(this.buy);
        }
        if (showSell) {
            tables.child(this.sell);
        }
        this.root.clearChildren();
        this.root.child(this.header);
        this.root.child(tables);
    }

    private static String title(OrderSide side) {
        return side == OrderSide.Buy ? "Buy orders" : "Sell offers";
    }

    private static List<Component> priceTooltip(Level level, OrderType workflowSide) {
        var entry = level.entry();
        double submittedPrice = OrderBookPriceComponent.adjustPrice(entry.price(), workflowSide);
        String adjustment = Double.compare(submittedPrice, entry.price()) == 0
            ? "" : workflowSide == OrderType.Buy ? " (+0.1)" : " (-0.1)";
        return List.of(
            Component.literal(level.boundText(DepthMode.Hidden)).withStyle(UiStyles.quantity()),
            Component.literal(level.ordersText()).withStyle(UiStyles.muted()),
            Component.literal("Click: submit ").withStyle(UiStyles.label())
                .append(Component.literal(BazaarWidgetViewData.formatPrice(submittedPrice)).withStyle(UiStyles.money()))
                .append(Component.literal(adjustment).withStyle(UiStyles.money())),
            Component.literal("Ctrl-click: copy ").withStyle(UiStyles.label())
                .append(Component.literal(entry.priceText()).withStyle(UiStyles.money())));
    }

    private static final class Side extends BaseParentUIComponent {
        private final Headers headers;
        private final WidgetScrollListComponent list = new WidgetScrollListComponent(
            1, WidgetLayoutTokens.LIST_GAP, true, UiStyles.palette().scrollbar());
        private final RetainedRows<Long, OrderBookRow> retainedRows = new RetainedRows<>();
        private List<OrderBookRow> rows = List.of();
        private final UIComponent empty;
        private final List<UIComponent> children;

        private Side(OrderSide side) {
            super(Sizing.fixed(1), Sizing.fixed(1));
            this.headers = new Headers(side);
            this.empty = UiComponents.label(side == OrderSide.Buy ? "No buy orders" : "No sell offers",
                UiStyles.palette().muted());
            this.empty.margins(Insets.left(INSET));
            this.children = List.of(this.headers, this.list);
            this.list.scrollbarThickness(SCROLLBAR_THICKNESS);
            this.allowOverflow(true);
        }

        private void update(
            List<Level> levels,
            OrderBookColumns columns,
            int rowHeight,
            int viewportHeight,
            int headerHeight,
            boolean single,
            OrderType workflowSide,
            Consumer<OrderBookAction> actions
        ) {
            this.rows = this.retainedRows.reconcile(levels,
                level -> Double.doubleToLongBits(level.entry().price()),
                (level, _) -> new OrderBookRow(level, this.list, true),
                (row, level, _) -> row.update(level, columns, 0, rowHeight, priceTooltip(level, workflowSide),
                    actions));
            var listRows = new ArrayList<UIComponent>(this.rows);
            if (levels.isEmpty()) {
                this.empty.verticalSizing(Sizing.fixed(rowHeight));
                listRows.add(this.empty);
            }
            this.list.updateRows(listRows, viewportHeight, true);
            this.headers.update(columns, headerHeight, !single);
            this.sizing(Sizing.fixed(columns.width()), Sizing.fixed(headerHeight + viewportHeight));
            this.dirty = true;
            this.updateLayout();
        }

        @Override
        public List<UIComponent> children() {
            return this.children;
        }

        @Override
        public ParentUIComponent removeChild(UIComponent child) {
            throw new UnsupportedOperationException("Order book owns its rows");
        }

        @Override
        public void layout(Size space) {
            var childSpace = this.calculateChildSpace(space);
            this.headers.inflate(childSpace);
            this.headers.mount(this, this.x, this.y);
            this.list.inflate(childSpace);
            this.list.mount(this, this.x, this.y + this.headers.height());
        }

        @Override
        public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
            boolean suppress = !this.list.isPointerInsideViewport(mouseX, mouseY)
                || this.list.scrollbarOwnsMouseCapture();
            this.rows.forEach(row -> row.suppressHover(suppress));
            super.draw(graphics, mouseX, mouseY, partialTicks, delta);
            this.drawChildren(graphics, mouseX, mouseY, partialTicks, delta, this.children);
        }
    }

    private static final class Headers extends BaseUIComponent {
        private final OrderSide side;
        private final RetainedTextRow text = new RetainedTextRow();
        private final FormattedCharSequence title;
        private final FormattedCharSequence price = Component.literal("Price").getVisualOrderText();
        private final FormattedCharSequence quantity = Component.literal("Quantity").getVisualOrderText();
        private final FormattedCharSequence orders = Component.literal("Orders").getVisualOrderText();
        private OrderBookColumns columns;
        private boolean showTitle;

        private Headers(OrderSide side) {
            this.side = side;
            this.title = Component.literal(title(side)).getVisualOrderText();
        }

        private void update(OrderBookColumns columns, int height, boolean showTitle) {
            this.columns = columns;
            this.showTitle = showTitle;
            this.sizing(Sizing.fill(100), Sizing.fixed(height));
        }

        @Override
        public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
            var font = Minecraft.getInstance().font;
            int headerY = this.y + (this.showTitle ? font.lineHeight + 1 : 0);
            this.text.begin();
            if (this.showTitle) {
                int titleX = this.side == OrderSide.Buy ? INSET : this.width - INSET - font.width(this.title);
                this.text.draw(graphics, font, this.title, this.x + titleX, this.y, this.side.accentColor(), false);
            }
            this.text.draw(graphics, font, this.price,
                this.x + this.columns.priceTextX(this.side, font.width(this.price)), headerY,
                UiStyles.palette().label(), false);
            this.text.draw(graphics, font, this.quantity,
                this.x + this.columns.quantityTextX(this.side, font.width(this.quantity)), headerY,
                UiStyles.palette().label(), false);
            if (this.columns.hasOrders()) {
                this.text.draw(graphics, font, this.orders,
                    this.x + this.columns.ordersTextX(this.side, font.width(this.orders)), headerY,
                    UiStyles.palette().label(), false);
            }
            graphics.fill(this.x + INSET, this.y + this.height - 2,
                this.x + this.width - INSET, this.y + this.height - 1, OrderBookStyles.dividerColor());
        }
    }
}
