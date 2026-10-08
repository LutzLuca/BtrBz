package com.github.lutzluca.btrbz.core.iteminfo.charts;

import com.github.lutzluca.btrbz.core.ui.UiStyles;
import com.github.lutzluca.coflnet.HistoryPoint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Vector2i;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Measures real text columns, keeping exact returned values aligned without padding strings. */
final class HistoryInspectionTooltip implements ClientTooltipComponent {
    static final ClientTooltipPositioner POSITIONER = (screenWidth, screenHeight, mouseX, mouseY, width, height) -> {
        int x;
        int y = mouseY - 8;
        if (mouseX + 16 + width + 8 <= screenWidth) {
            x = mouseX + 16;
        } else if (mouseX - 16 - width >= 8) {
            x = mouseX - 16 - width;
        } else {
            x = mouseX - width / 2;
            y = mouseY - height - 16 >= 8 ? mouseY - height - 16 : mouseY + 16;
        }
        return new Vector2i(Math.clamp(x, 8, Math.max(8, screenWidth - width - 8)),
            Math.clamp(y, 8, Math.max(8, screenHeight - height - 8)));
    };
    private static final int GAP = 12;
    private final List<Row> rows;
    private final int labelWidth;
    private final int priceWidth;
    private final int quantityWidth;
    private final int width;
    private final int maximumWidth;
    private final boolean stackedLabels;

    HistoryInspectionTooltip(HistoryPoint point, HistoryPoint pinned, ChartOptions options) {
        var font = Minecraft.getInstance().font;
        this.maximumWidth = Math.max(80, Minecraft.getInstance().getWindow().getGuiScaledWidth() - 24);
        var content = new ArrayList<Row>();
        this.heading(content, HistoryTime.detailed(point.timestamp()), UiStyles.palette().primary());
        this.heading(content, options.metric().label(), UiStyles.palette().muted());
        int headerWidth = font.width("Buy") + font.width("Price (coins)") + font.width("Quantity (items)") + GAP * 2;
        boolean compactHeaders = headerWidth > this.maximumWidth;
        content.add(new Row(this.text("", UiStyles.palette().muted()),
            this.text(compactHeaders ? "Coins" : "Price (coins)", UiStyles.palette().muted()),
            this.text(compactHeaders ? "Items" : "Quantity (items)", UiStyles.palette().muted())));
        content.add(this.values("Buy", HistoryAnalysis.exact(point.buy()),
            this.quantity(options.metric().value(point, true)), UiStyles.palette().buy()));
        content.add(this.values("Sell", HistoryAnalysis.exact(point.sell()),
            this.quantity(options.metric().value(point, false)), UiStyles.palette().sell()));
        if (pinned != null && !point.timestamp().equals(pinned.timestamp())) {
            this.heading(content, "Compared to " + HistoryTime.detailed(pinned.timestamp()),
                UiStyles.palette().muted());
            content.add(this.values("Buy", this.change(point.buy(), pinned.buy()), null, UiStyles.palette().buy()));
            content.add(this.values("Sell", this.change(point.sell(), pinned.sell()), null, UiStyles.palette().sell()));
        }
        this.rows = List.copyOf(content);
        int label = 0;
        int price = 0;
        int quantity = 0;
        int spanning = 0;
        int heading = 0;
        for (var row : this.rows) {
            if (row.price() == null) {
                heading = Math.max(heading, font.width(row.label()));
            } else {
                label = Math.max(label, font.width(row.label()));
                if (row.quantity() == null) {
                    spanning = Math.max(spanning, font.width(row.price()));
                } else {
                    price = Math.max(price, font.width(row.price()));
                    quantity = Math.max(quantity, font.width(row.quantity()));
                }
            }
        }
        this.labelWidth = label;
        this.priceWidth = price;
        this.quantityWidth = quantity;
        int valueWidth = Math.max(spanning, price + quantity + GAP);
        this.stackedLabels = label + GAP + valueWidth > this.maximumWidth;
        this.width = Math.max(heading, valueWidth + (this.stackedLabels ? 0 : label + GAP));
    }

    @Override
    public int getHeight(Font font) {
        int height = 0;
        for (var row : this.rows) {
            height += this.stackedLabels && row.price() != null && font.width(row.label()) > 0 ? 23 : 13;
        }
        return height;
    }

    @Override
    public int getWidth(Font font) {
        return this.width;
    }

    @Override
    public void extractText(GuiGraphicsExtractor graphics, Font font, int x, int y) {
        for (var row : this.rows) {
            graphics.text(font, row.label(), x, y, 0xFFFFFFFF, false);
            if (row.price() == null) {
                y += 13;
                continue;
            }
            boolean stacked = this.stackedLabels && font.width(row.label()) > 0;
            if (stacked) {
                y += 10;
            }
            int valuesStart = this.stackedLabels ? 0 : this.labelWidth + GAP;
            if (row.quantity() == null) {
                graphics.text(font, row.price(), x + this.width - font.width(row.price()), y, 0xFFFFFFFF, false);
            } else {
                graphics.text(font, row.price(), x + valuesStart + this.priceWidth - font.width(row.price()),
                    y, 0xFFFFFFFF, false);
                graphics.text(font, row.quantity(),
                    x + valuesStart + this.priceWidth + GAP + this.quantityWidth - font.width(row.quantity()),
                    y, 0xFFFFFFFF, false);
            }
            y += 13;
        }
    }

    private void heading(List<Row> rows, String label, int color) {
        var component = Component.literal(label).withStyle(UiStyles.color(color));
        for (var line : Minecraft.getInstance().font.split(component, this.maximumWidth)) {
            rows.add(new Row(line, null, null));
        }
    }

    private Row values(String label, String price, String quantity, int color) {
        return new Row(this.text(label, color), this.text(price, color),
            quantity == null ? null : this.text(quantity, color));
    }

    private FormattedCharSequence text(String value, int color) {
        return Component.literal(value).withStyle(UiStyles.color(color)).getVisualOrderText();
    }

    private String quantity(Long value) {
        return value == null ? "Unavailable" : String.format(Locale.ROOT, "%,d", value);
    }

    private String change(Double value, Double pinned) {
        if (value == null || pinned == null || !Double.isFinite(value) || !Double.isFinite(pinned)) {
            return "Unavailable";
        }
        String coins = String.format(Locale.ROOT, "%+,.1f coins", value - pinned);
        return coins + (pinned == 0
            ? " (unavailable %)"
            : String.format(Locale.ROOT, " (%+.2f%%)", (value - pinned) / pinned * 100));
    }

    private record Row(FormattedCharSequence label, FormattedCharSequence price, FormattedCharSequence quantity) {}
}
