package com.github.lutzluca.btrbz.core.bazaariteminfo;

import com.github.lutzluca.btrbz.core.bazaariteminfo.BazaarItemInfoOrderColumns.Column;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarStyles;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetLayoutTokens;
import com.mojang.blaze3d.platform.InputConstants;
import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import java.util.Objects;
import java.util.function.DoubleConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;

/** Fixed-column Order Book row whose price cell is the only interactive region. */
final class BazaarItemInfoOrderRowComponent extends BaseUIComponent {
    private static final int ALTERNATE_ROW = 0x0CFFFFFF;

    private Row row;

    BazaarItemInfoOrderRowComponent(Row row, int height) {
        this.row = Objects.requireNonNull(row, "row");
        this.sizing(Sizing.fill(100), Sizing.fixed(height));
    }

    void update(Row row, int height) {
        this.row = Objects.requireNonNull(row, "row");
        this.verticalSizing(Sizing.fixed(height));
    }

    @Override
    public boolean canFocus(FocusSource source) {
        return !this.row.empty() && source == FocusSource.MOUSE_CLICK;
    }

    @Override
    public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
        if (click.button() != InputConstants.MOUSE_BUTTON_LEFT || this.row.empty()) {
            return super.onMouseDown(click, doubled);
        }
        double absoluteX = this.x + click.x();
        if (absoluteX >= this.x && absoluteX < this.priceCellEnd()) {
            this.row.copy().accept(this.row.price());
            return true;
        }
        return super.onMouseDown(click, doubled);
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        var font = Minecraft.getInstance().font;
        int textY = this.y + Math.max(0,
            (this.height - BazaarItemInfoOrderText.lineHeight(font.lineHeight)) / 2);
        int padding = WidgetLayoutTokens.ROW_HORIZONTAL_PADDING;
        if (this.row.empty()) {
            BazaarItemInfoOrderText.draw(
                graphics, font, this.row.itemsText(), this.x + padding, textY, BazaarStyles.MUTED_TEXT);
            return;
        }
        if (this.row.depth() % 2 == 0) {
            graphics.fill(this.x, this.y, this.x + this.width, this.y + this.height, ALTERNATE_ROW);
        }
        var columns = BazaarItemInfoOrderColumns.layout(
            this.width, this.row.showCumulative(), this.row.showOrders());
        if (mouseX >= this.x && mouseX < this.priceCellEnd()
            && mouseY >= this.y
            && mouseY < this.y + this.height) {
            graphics.fill(this.x, this.y, this.priceCellEnd(), this.y + this.height, BazaarStyles.ROW_HOVER);
        }
        drawCell(graphics, columns.price(), this.row.priceText(), textY, this.row.accent(), false);
        drawCell(graphics, columns.items(), this.row.itemsText(), textY, BazaarStyles.SECONDARY_TEXT, true);
        if (columns.cumulative().visible()) {
            drawCell(graphics, columns.cumulative(), this.row.cumulativeText(), textY,
                BazaarStyles.SECONDARY_TEXT, true);
        }
        if (columns.orders().visible()) {
            drawCell(graphics, columns.orders(), this.row.ordersText(), textY, BazaarStyles.MUTED_TEXT, true);
        }
    }

    int priceCellEnd() {
        return this.x + BazaarItemInfoOrderColumns.layout(
            this.width, this.row.showCumulative(), this.row.showOrders()).price().end();
    }

    private void drawCell(
        OwoUIGraphics graphics,
        Column column,
        String text,
        int textY,
        int color,
        boolean rightAligned
    ) {
        var font = Minecraft.getInstance().font;
        int left = this.x + column.start();
        int right = this.x + column.end();
        graphics.enableScissor(left, this.y, right, this.y + this.height);
        int textX = rightAligned
            ? this.x + column.textRight() - BazaarItemInfoOrderText.width(font, text)
            : this.x + column.textLeft();
        BazaarItemInfoOrderText.draw(graphics, font, text, textX, textY, color);
        graphics.disableScissor();
    }

    record Row(
        String id,
        double price,
        String priceText,
        String itemsText,
        String cumulativeText,
        String ordersText,
        int accent,
        int depth,
        boolean showCumulative,
        boolean showOrders,
        boolean empty,
        DoubleConsumer copy
    ) {
        Row {
            id = Objects.requireNonNull(id, "id");
            priceText = Objects.requireNonNull(priceText, "priceText");
            itemsText = Objects.requireNonNull(itemsText, "itemsText");
            cumulativeText = Objects.requireNonNull(cumulativeText, "cumulativeText");
            ordersText = Objects.requireNonNull(ordersText, "ordersText");
            copy = Objects.requireNonNull(copy, "copy");
        }
    }
}
