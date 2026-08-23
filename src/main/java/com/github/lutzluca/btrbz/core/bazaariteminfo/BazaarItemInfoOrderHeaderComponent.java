package com.github.lutzluca.btrbz.core.bazaariteminfo;

import com.github.lutzluca.btrbz.core.bazaariteminfo.BazaarItemInfoOrderColumns.Column;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarStyles;
import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.Minecraft;

/** Order Book headings drawn against the same column boundaries as every data row. */
final class BazaarItemInfoOrderHeaderComponent extends BaseUIComponent {
    private static final int DIVIDER_COLOR = 0x403A414D;

    private boolean showCumulative;
    private boolean showOrders;

    BazaarItemInfoOrderHeaderComponent(int height) {
        this.sizing(Sizing.fill(100), Sizing.fixed(height));
    }

    void update(boolean showCumulative, boolean showOrders, int height) {
        this.showCumulative = showCumulative;
        this.showOrders = showOrders;
        this.verticalSizing(Sizing.fixed(height));
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        var columns = BazaarItemInfoOrderColumns.layout(this.width, this.showCumulative, this.showOrders);
        int textY = this.y + Math.max(0,
            (this.height - BazaarItemInfoOrderText.lineHeight(Minecraft.getInstance().font.lineHeight)) / 2);
        drawCell(graphics, columns.price(), "Price", textY, false);
        drawCell(graphics, columns.items(), "Items", textY, true);
        if (columns.cumulative().visible()) {
            drawCell(graphics, columns.cumulative(), "Cumulative", textY, true);
        }
        if (columns.orders().visible()) {
            drawCell(graphics, columns.orders(), "Orders", textY, true);
        }
        graphics.fill(this.x, this.y + this.height - 1, this.x + this.width, this.y + this.height, DIVIDER_COLOR);
    }

    private void drawCell(OwoUIGraphics graphics, Column column, String text, int textY, boolean rightAligned) {
        var font = Minecraft.getInstance().font;
        int left = this.x + column.start();
        int right = this.x + column.end();
        graphics.enableScissor(left, this.y, right, this.y + this.height);
        int textX = rightAligned
            ? this.x + column.textRight() - BazaarItemInfoOrderText.width(font, text)
            : this.x + column.textLeft();
        BazaarItemInfoOrderText.draw(graphics, font, text, textX, textY, BazaarStyles.SECONDARY_TEXT);
        graphics.disableScissor();
    }
}
