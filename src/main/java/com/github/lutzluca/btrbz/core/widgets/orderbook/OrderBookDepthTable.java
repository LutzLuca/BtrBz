package com.github.lutzluca.btrbz.core.widgets.orderbook;

import com.github.lutzluca.btrbz.core.ui.RetainedTextRow;
import com.github.lutzluca.btrbz.core.ui.UiComponents;
import com.github.lutzluca.btrbz.core.ui.UiStyles;
import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData.OrderSide;
import com.github.lutzluca.btrbz.core.widgets.orderbook.OrderBookDepth.Level;
import com.github.lutzluca.btrbz.core.widgets.orderbook.OrderBookWidgetConfig.DepthMode;
import com.github.lutzluca.btrbz.core.widgets.orderbook.OrderBookWidgetConfig.ScrollMode;
import com.github.lutzluca.btrbz.core.widgets.ui.RetainedRows;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetLayoutTokens;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetScrollListComponent;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetTooltips;
import io.wispforest.owo.ui.base.BaseParentUIComponent;
import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.ParentUIComponent;
import io.wispforest.owo.ui.core.Size;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;

/** Mirrored native depth geometry owned by the full order book. */
final class OrderBookDepthTable extends BaseParentUIComponent {
    private static final int SIDE_GAP = 12;
    private static final int SCROLLBAR_THICKNESS = 2;
    static final int INSET = WidgetLayoutTokens.ROW_HORIZONTAL_PADDING
        + SCROLLBAR_THICKNESS + WidgetLayoutTokens.SCROLLBAR_CONTENT_GAP;
    private static final int COLUMN_GAP = 8;
    private static final int HIDDEN_PRICE_WEIGHT = 43;
    private static final int HIDDEN_QUANTITY_WEIGHT = 32;
    private static final int HIDDEN_ORDERS_WEIGHT = 17;

    private final Side buy = new Side(OrderSide.Buy);
    private final Side sell = new Side(OrderSide.Sell);
    private final List<UIComponent> children = List.of(this.buy, this.sell);
    private boolean stacked;
    private boolean synchronizedScrolling;
    private boolean synchronizing;
    private Side scrollLeader = this.buy;

    OrderBookDepthTable() {
        super(Sizing.fixed(1), Sizing.fixed(1));
        this.allowOverflow(true);
        this.buy.list.onScroll((offset, instant) -> this.synchronizeScroll(this.buy, this.sell, offset, instant));
        this.sell.list.onScroll((offset, instant) -> this.synchronizeScroll(this.sell, this.buy, offset, instant));
    }

    Layout measure(OrderBookDepth book, OrderBookWidgetConfig config, int availableWidth) {
        var font = Minecraft.getInstance().font;
        int priceWidth = font.width("Price");
        int quantityWidth = font.width("Quantity");
        int ordersWidth = font.width("Orders");
        int totalWidth = font.width("Total");

        var levels = new ArrayList<>(book.buy());
        levels.addAll(book.sell());
        for (var level : levels) {
            priceWidth = Math.max(priceWidth, font.width(level.entry().priceText()));
            quantityWidth = Math.max(quantityWidth, font.width(level.entry().quantityText()));
            ordersWidth = Math.max(ordersWidth, font.width(level.orderCountText()));
            totalWidth = Math.max(totalWidth, font.width(level.depthText()));
        }

        ordersWidth = config.showOrderCount ? ordersWidth : 0;
        boolean cumulative = book.mode() == DepthMode.Cumulative;
        int minimumTotalWidth = cumulative ? Math.max(40, totalWidth + 6) : 0;
        if (book.mode() == DepthMode.Relative) {
            quantityWidth = Math.max(64, quantityWidth + 6);
        }
        int columnGaps = COLUMN_GAP * (1 + (config.showOrderCount ? 1 : 0) + (cumulative ? 1 : 0));
        int minimumSideWidth = priceWidth + quantityWidth + ordersWidth + minimumTotalWidth
            + columnGaps + INSET * 2;
        boolean stacked = minimumSideWidth * 2 + SIDE_GAP > availableWidth;
        int fittingSideWidth = !stacked
            ? (availableWidth - SIDE_GAP) / 2 : availableWidth;
        int preferredSideWidth = Math.min(
            stacked ? config.contentWidth : Math.max(1, (config.contentWidth - SIDE_GAP) / 2), fittingSideWidth);
        int minimumSideForFrame = !stacked
            ? (OrderBookWidgetConfig.MIN_CONTENT_WIDTH - SIDE_GAP + 1) / 2
            : OrderBookWidgetConfig.MIN_CONTENT_WIDTH;
        int sideWidth = Math.max(Math.max(minimumSideWidth, minimumSideForFrame), preferredSideWidth);
        int columnSpace = sideWidth - columnGaps - INSET * 2;
        int depthWidth = 0;
        switch (book.mode()) {
            case Hidden -> {
                // Keep 172:128:68 column proportions without clipping native text.
                if (config.showOrderCount) {
                    ordersWidth = Math.clamp(
                        columnSpace * HIDDEN_ORDERS_WEIGHT
                            / (HIDDEN_PRICE_WEIGHT + HIDDEN_QUANTITY_WEIGHT + HIDDEN_ORDERS_WEIGHT),
                        ordersWidth, columnSpace - priceWidth - quantityWidth);
                }
                int priceAndQuantitySpace = columnSpace - ordersWidth;
                priceWidth = Math.clamp(
                    priceAndQuantitySpace * HIDDEN_PRICE_WEIGHT / (HIDDEN_PRICE_WEIGHT + HIDDEN_QUANTITY_WEIGHT),
                    priceWidth, priceAndQuantitySpace - quantityWidth);
                quantityWidth = priceAndQuantitySpace - priceWidth;
            }
            case Cumulative -> depthWidth = columnSpace - priceWidth - quantityWidth - ordersWidth;
            case Relative -> quantityWidth = columnSpace - priceWidth - ordersWidth;
        }
        var columns = new Columns(sideWidth, priceWidth, quantityWidth,
            ordersWidth, depthWidth, book.mode() == DepthMode.Relative);
        int contentWidth = !stacked ? sideWidth * 2 + SIDE_GAP : sideWidth;
        return new Layout(columns, stacked, contentWidth);
    }

    void update(
        OrderBookDepth book,
        OrderBookWidgetConfig config,
        Consumer<OrderBookAction> actions,
        Layout layout,
        int availableHeight
    ) {
        var font = Minecraft.getInstance().font;
        var columns = layout.columns();
        this.stacked = layout.stacked();
        this.synchronizedScrolling = config.scrolling == ScrollMode.Together;
        int rowHeight = font.lineHeight * 2 + 5;
        int headerHeight = font.lineHeight * 2 + 11;
        int maximumViewport = (this.stacked ? (availableHeight - SIDE_GAP) / 2 : availableHeight)
            - headerHeight;
        int fittingRows = Math.max(1, (maximumViewport + WidgetLayoutTokens.LIST_GAP)
            / (rowHeight + WidgetLayoutTokens.LIST_GAP));
        int levelCount = Math.max(book.buy().size(), book.sell().size());
        int visibleRows = Math.min(Math.max(1, config.visibleRows), Math.max(1, levelCount));
        int viewportHeight = WidgetLayoutTokens.listViewportHeight(rowHeight, Math.min(visibleRows, fittingRows));
        int height = headerHeight + viewportHeight;

        this.buy.update(book.buy(), columns, book.maximum(), rowHeight, viewportHeight, headerHeight,
            this.synchronizedScrolling ? levelCount : book.buy().size(), actions);
        this.sell.update(book.sell(), columns, book.maximum(), rowHeight, viewportHeight, headerHeight,
            this.synchronizedScrolling ? levelCount : book.sell().size(), actions);
        int tableHeight = this.stacked ? height * 2 + SIDE_GAP : height;
        this.sizing(Sizing.fixed(layout.contentWidth()), Sizing.fixed(tableHeight));
        this.dirty = true;
        this.updateLayout();
    }

    @Override
    public List<UIComponent> children() {
        return this.children;
    }

    @Override
    public ParentUIComponent removeChild(UIComponent child) {
        throw new UnsupportedOperationException("Order book owns its sides");
    }

    @Override
    public void layout(Size space) {
        int x = this.x;
        int y = this.y;
        for (var child : this.children) {
            child.inflate(this.calculateChildSpace(space));
            child.mount(this, x, y);
            if (this.stacked) {
                y += child.height() + SIDE_GAP;
            } else {
                x += child.width() + SIDE_GAP;
            }
        }
        if (this.synchronizedScrolling) {
            var follower = this.scrollLeader == this.buy ? this.sell : this.buy;
            this.synchronizeScroll(this.scrollLeader, follower, this.scrollLeader.list.scrollOffset(), true);
        }
    }

    private void synchronizeScroll(Side source, Side follower, double offset, boolean instant) {
        if (this.synchronizing) {
            return;
        }
        this.scrollLeader = source;
        if (!this.synchronizedScrolling) {
            return;
        }
        this.synchronizing = true;
        try {
            follower.list.scrollToOffset(offset, instant);
        } finally {
            this.synchronizing = false;
        }
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        super.draw(graphics, mouseX, mouseY, partialTicks, delta);
        this.drawChildren(graphics, mouseX, mouseY, partialTicks, delta, this.children);
    }

    static int dividerColor() {
        return ARGB.multiplyAlpha(UiStyles.palette().border(), 0.65F);
    }

    static int barFillColor(OrderSide side) {
        return ARGB.srgbLerp(0.3F, ARGB.opaque(UiStyles.palette().panelBackground()), side.accentColor());
    }

    record Layout(Columns columns, boolean stacked, int contentWidth) {}

    record Columns(
        int width, int priceWidth, int quantityWidth, int ordersWidth, int totalWidth, boolean quantityBar
    ) {
        boolean hasTotal() {
            return this.totalWidth > 0;
        }

        boolean hasOrders() {
            return this.ordersWidth > 0;
        }

        boolean hasDepth() {
            return this.hasTotal() || this.quantityBar;
        }

        int priceX(OrderSide side) {
            return side == OrderSide.Buy ? INSET : this.width - INSET - this.priceWidth;
        }

        int priceTextX(OrderSide side, int textWidth) {
            return this.priceX(side) + this.priceWidth - textWidth;
        }

        int quantityX(OrderSide side) {
            if (this.quantityBar) {
                return side == OrderSide.Buy ? this.width - INSET - this.quantityWidth : INSET;
            }
            return side == OrderSide.Buy
                ? INSET + this.priceWidth + COLUMN_GAP
                : this.priceX(side) - COLUMN_GAP - this.quantityWidth;
        }

        int quantityTextX(OrderSide side, int textWidth) {
            if (this.quantityBar) {
                return this.quantityX(side) + 3
                    + (side == OrderSide.Buy ? this.quantityWidth - 6 - textWidth : 0);
            }
            return this.quantityX(side) + this.quantityWidth - textWidth;
        }

        int ordersX(OrderSide side) {
            if (this.quantityBar) {
                return side == OrderSide.Buy
                    ? this.priceX(side) + this.priceWidth + COLUMN_GAP
                    : this.priceX(side) - COLUMN_GAP - this.ordersWidth;
            }
            return side == OrderSide.Buy
                ? this.quantityX(side) + this.quantityWidth + COLUMN_GAP
                : this.quantityX(side) - COLUMN_GAP - this.ordersWidth;
        }

        int totalX(OrderSide side) {
            return side == OrderSide.Buy ? this.width - INSET - this.totalWidth : INSET;
        }

        int ordersTextX(OrderSide side, int textWidth) {
            return this.ordersX(side) + this.ordersWidth - textWidth;
        }

        int totalTextX(OrderSide side, int textWidth) {
            return this.totalX(side) + 3 + (side == OrderSide.Buy ? this.totalWidth - 6 - textWidth : 0);
        }

        int barX(OrderSide side) {
            return this.hasTotal() ? this.totalX(side) : this.quantityX(side);
        }

        int barWidth() {
            return this.hasTotal() ? this.totalWidth : this.quantityWidth;
        }
    }

    private record LevelKey(OrderSide side, long priceBits) {}

    private static final class Side extends BaseParentUIComponent {
        private final OrderSide side;
        private final Headers headers;
        private final WidgetScrollListComponent list = new WidgetScrollListComponent(
            1, WidgetLayoutTokens.LIST_GAP, true, ARGB.multiplyAlpha(UiStyles.palette().scrollbar(), 0.55F));
        private final RetainedRows<LevelKey, OrderBookDepthRow> retainedRows = new RetainedRows<>();
        private List<OrderBookDepthRow> rows = List.of();
        private final UIComponent empty;
        private final UIComponent scrollPadding = UIContainers.verticalFlow(Sizing.fill(100), Sizing.fixed(1));
        private final List<UIComponent> children;

        private Side(OrderSide side) {
            super(Sizing.fixed(1), Sizing.fixed(1));
            this.side = side;
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
            Columns columns,
            long maximum,
            int rowHeight,
            int viewportHeight,
            int headerHeight,
            int scrollLevelCount,
            Consumer<OrderBookAction> actions
        ) {
            this.rows = this.retainedRows.reconcile(levels,
                level -> new LevelKey(this.side, Double.doubleToLongBits(level.entry().price())),
                (level, _) -> new OrderBookDepthRow(level, this.list),
                (row, level, _) -> row.update(level, columns, maximum, rowHeight, actions));
            var listRows = new ArrayList<UIComponent>(this.rows);
            if (levels.isEmpty()) {
                this.empty.verticalSizing(Sizing.fixed(rowHeight));
                listRows.add(this.empty);
            }
            int paddingRows = scrollLevelCount - Math.max(1, levels.size());
            if (paddingRows > 0) {
                this.scrollPadding.verticalSizing(Sizing.fixed(
                    paddingRows * (rowHeight + WidgetLayoutTokens.LIST_GAP) - WidgetLayoutTokens.LIST_GAP));
                listRows.add(this.scrollPadding);
            }
            this.list.updateRows(listRows, viewportHeight, true);
            this.headers.update(columns, headerHeight);
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
            throw new UnsupportedOperationException("Order book side owns its rows");
        }

        @Override
        public void layout(Size space) {
            var childSpace = this.calculateChildSpace(space);
            this.headers.inflate(childSpace);
            this.headers.mount(this, this.x, this.y);
            UIComponent body = this.children.getLast();
            body.inflate(childSpace);
            body.mount(this, this.x, this.y + this.headers.height());
        }

        @Override
        public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
            super.draw(graphics, mouseX, mouseY, partialTicks, delta);
            boolean suppress = !this.list.isPointerInsideViewport(mouseX, mouseY)
                || this.list.scrollbarOwnsMouseCapture();
            for (var row : this.rows) {
                row.suppressHover(suppress);
            }
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
        private final FormattedCharSequence total = Component.literal("Total").getVisualOrderText();
        private Columns columns = new Columns(1, 1, 1, 0, 0, false);

        private Headers(OrderSide side) {
            this.side = side;
            this.title = Component.literal(side == OrderSide.Buy ? "Buy orders" : "Sell offers")
                .getVisualOrderText();
        }

        private void update(Columns columns, int height) {
            this.columns = columns;
            this.sizing(Sizing.fill(100), Sizing.fixed(height));
            this.tooltip(WidgetTooltips.wrapped(columns.quantityBar()
                ? "Quantity counts items at this exact price. Bars share one scale across all supplied levels."
                : this.side == OrderSide.Buy
                    ? "Total counts items at this price or higher, including this level. Supplied levels only."
                    : "Total counts items at this price or cheaper, including this level. Supplied levels only."));
        }

        @Override
        public boolean shouldDrawTooltip(double mouseX, double mouseY) {
            int start = this.x + this.columns.barX(this.side);
            int width = this.columns.barWidth();
            return this.columns.hasDepth() && this.isInBoundingBox(mouseX, mouseY)
                && mouseY >= this.y + Minecraft.getInstance().font.lineHeight + 5
                && mouseX >= start
                && mouseX < start + width;
        }

        @Override
        public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
            var font = Minecraft.getInstance().font;
            int titleX = this.side == OrderSide.Buy ? INSET : this.width - INSET - font.width(this.title);
            int headerY = this.y + font.lineHeight + 5;
            int priceX = this.columns.priceTextX(this.side, font.width(this.price));
            int quantityX = this.columns.quantityTextX(this.side, font.width(this.quantity));
            int ordersX = this.columns.ordersTextX(this.side, font.width(this.orders));
            int totalX = this.columns.totalTextX(this.side, font.width(this.total));
            graphics.fill(this.x + INSET, this.y + this.height - 3,
                this.x + this.width - INSET, this.y + this.height - 2, dividerColor());
            this.text.begin();
            this.text.draw(graphics, font, this.title, this.x + titleX, this.y,
                this.side.accentColor(), false);
            this.text.draw(graphics, font, this.price, this.x + priceX, headerY, UiStyles.palette().label(), false);
            this.text.draw(graphics, font, this.quantity, this.x + quantityX, headerY, UiStyles.palette().label(),
                false);
            if (this.columns.hasOrders()) {
                this.text.draw(graphics, font, this.orders, this.x + ordersX, headerY, UiStyles.palette().label(),
                    false);
            }
            if (this.columns.hasTotal()) {
                this.text.draw(graphics, font, this.total, this.x + totalX, headerY, UiStyles.palette().label(), false);
            }
        }
    }
}
