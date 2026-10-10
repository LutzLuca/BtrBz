package com.github.lutzluca.btrbz.core.widgets.orderbook;

import com.github.lutzluca.btrbz.core.ui.RetainedTextRow;
import com.github.lutzluca.btrbz.core.ui.UiStyles;
import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData.OrderSide;
import com.github.lutzluca.btrbz.core.widgets.orderbook.OrderBookDepthTable.Columns;
import com.github.lutzluca.btrbz.core.widgets.orderbook.OrderBookDepth.Level;
import com.github.lutzluca.btrbz.core.widgets.orderbook.OrderBookWidgetConfig.DepthMode;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetTooltips;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetScrollListComponent;
import com.mojang.blaze3d.platform.InputConstants;
import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import java.util.ArrayList;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Retained price, quantity, optional order count and depth for one exact level. */
final class OrderBookDepthRow extends BaseUIComponent {
    private final RetainedTextRow text = new RetainedTextRow();
    private final WidgetScrollListComponent viewport;
    private Level level;
    private Columns columns = new Columns(1, 1, 1, 0, 0, false);
    private Consumer<OrderBookAction> actions = _ -> {};
    private FormattedCharSequence price = FormattedCharSequence.EMPTY;
    private FormattedCharSequence quantity = FormattedCharSequence.EMPTY;
    private FormattedCharSequence orders = FormattedCharSequence.EMPTY;
    private FormattedCharSequence depth = FormattedCharSequence.EMPTY;
    private long maximum;
    private boolean hoverSuppressed;

    OrderBookDepthRow(Level level, WidgetScrollListComponent viewport) {
        this.level = level;
        this.viewport = viewport;
    }

    void update(
        Level level,
        Columns columns,
        long maximum,
        int height,
        Consumer<OrderBookAction> actions
    ) {
        this.level = level;
        this.columns = columns;
        this.maximum = maximum;
        this.actions = actions;
        this.price = Component.literal(level.entry().priceText())
            .withStyle(UiStyles.money()).getVisualOrderText();
        this.quantity = Component.literal(level.entry().quantityText()).getVisualOrderText();
        this.orders = Component.literal(level.orderCountText()).getVisualOrderText();
        this.depth = Component.literal(level.depthText()).getVisualOrderText();
        var tooltip = new ArrayList<Component>();
        tooltip.add(Component.literal(level.boundText(DepthMode.Relative)).withStyle(UiStyles.quantity()));
        tooltip.add(Component.literal(level.ordersText()).withStyle(UiStyles.muted()));
        if (columns.hasTotal()) {
            tooltip.add(Component.literal("Total: " + level.boundText(DepthMode.Cumulative))
                .withStyle(UiStyles.quantity()));
        }
        this.tooltip(WidgetTooltips.wrapped(tooltip));
        this.sizing(Sizing.fill(100), Sizing.fixed(height));
    }

    void suppressHover(boolean suppressed) {
        this.hoverSuppressed = suppressed;
    }

    private boolean overPrice(double x) {
        int start = this.columns.priceX(this.level.entry().side());
        return x >= start && x < start + this.columns.priceWidth();
    }

    @Override
    public boolean canFocus(FocusSource source) {
        return source == FocusSource.MOUSE_CLICK;
    }

    @Override
    public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
        if (click.button() == InputConstants.MOUSE_BUTTON_LEFT
            && this.viewport.isPointerInsideViewport(this.x + click.x(), this.y + click.y())
            && !this.viewport.scrollbarOwnsMouseCapture()
            && click.y() >= 0
            && click.y() < this.height
            && this.overPrice(click.x())) {
            this.actions.accept(new OrderBookAction.SelectPrice(this.level.entry().price(), click.hasControlDown()));
            return true;
        }
        return super.onMouseDown(click, doubled);
    }

    @Override
    public boolean shouldDrawTooltip(double mouseX, double mouseY) {
        return !this.hoverSuppressed && this.isInBoundingBox(mouseX, mouseY);
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        var side = this.level.entry().side();
        int laneWidth = this.columns.barWidth();
        if (this.columns.hasDepth()) {
            int laneX = this.x + this.columns.barX(side);
            int barY = this.y + 3;
            int barBottom = this.y + this.height - 3;
            graphics.fill(laneX, barY, laneX + laneWidth, barBottom, UiStyles.palette().progressTrack());
            int fill = (int) Math.round(laneWidth * this.level.fillFraction(this.maximum));
            int fillX = side == OrderSide.Buy ? laneX + laneWidth - fill : laneX;
            graphics.fill(fillX, barY, fillX + fill, barBottom, OrderBookDepthTable.barFillColor(side));
        }

        var font = Minecraft.getInstance().font;
        int priceX = this.columns.priceTextX(side, font.width(this.price));
        int quantityX = this.columns.quantityTextX(side, font.width(this.quantity));
        int ordersX = this.columns.ordersTextX(side, font.width(this.orders));
        int depthX = this.columns.totalTextX(side, font.width(this.depth));
        int middleY = this.y + (this.height - font.lineHeight) / 2;
        this.text.begin();
        this.text.draw(graphics, font, this.price, this.x + priceX, middleY, UiStyles.palette().money(), false);
        if (!this.hoverSuppressed && this.isInBoundingBox(mouseX, mouseY) && this.overPrice(mouseX - this.x)) {
            graphics.fill(this.x + priceX, middleY + font.lineHeight,
                this.x + priceX + font.width(this.price), middleY + font.lineHeight + 1,
                UiStyles.palette().money());
        }
        this.text.draw(graphics, font, this.quantity, this.x + quantityX, middleY,
            UiStyles.palette().quantity(), this.columns.quantityBar());
        if (this.columns.hasOrders()) {
            this.text.draw(graphics, font, this.orders, this.x + ordersX, middleY,
                UiStyles.palette().muted(), false);
        }
        if (this.columns.hasTotal()) {
            this.text.draw(graphics, font, this.depth, this.x + depthX, middleY, UiStyles.palette().quantity(), true);
        }
    }
}
