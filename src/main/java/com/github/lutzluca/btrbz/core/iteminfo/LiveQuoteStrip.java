package com.github.lutzluca.btrbz.core.iteminfo;

import com.github.lutzluca.btrbz.core.iteminfo.charts.HistoryAnalysis;
import com.github.lutzluca.btrbz.core.ui.UiStyles;
import com.github.lutzluca.btrbz.core.widgets.ui.RetainedTextRow;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetTooltips;
import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/** Compact quote groups with stable widths and whole-group reflow. */
final class LiveQuoteStrip extends BaseUIComponent {
    private static final int GROUP_GAP = 22;
    private static final int LABEL_GAP = 4;
    private static final int PERCENT_GAP = 4;
    private static final int ROW_GAP = 10;
    private static final int AGE_GAP = 4;
    private final RetainedTextRow text = new RetainedTextRow();
    private final List<FormattedCharSequence> titles = List.of(Component.literal("Buy Price").getVisualOrderText(),
        Component.literal("Spread").getVisualOrderText(), Component.literal("Sell Price").getVisualOrderText());
    private final int[] groupWidths = new int[3];
    private final int[] groupLeft = new int[3];
    private final int[] groupTop = new int[3];
    private final int reservedAgeWidth;
    private List<FormattedCharSequence> prices = List.of();
    private List<FormattedCharSequence> percentages = List.of();
    private List<List<ClientTooltipComponent>> details = List.of();
    private FormattedCharSequence age = Component.literal("Hypixel unavailable").getVisualOrderText();
    private int availableWidth = 700;
    private int ageTop;

    LiveQuoteStrip() {
        var font = Minecraft.getInstance().font;
        // Leave room for price and percentage digit changes before any live value is available.
        int priceWidth = font.width("999,999,999.9");
        for (int index = 0; index < this.groupWidths.length; index++) {
            int percentWidth = font.width(index == 1 ? "(100.0%)" : "(+999.9%)");
            this.groupWidths[index] = Math.max(font.width(this.titles.get(index)),
                priceWidth + PERCENT_GAP + percentWidth);
        }
        this.reservedAgeWidth = font.width("Hypixel unavailable");
        this.sizing(Sizing.fill(100), Sizing.fixed(font.lineHeight * 2 + LABEL_GAP));
        this.layoutFor(this.availableWidth);
    }

    void layoutFor(int width) {
        this.availableWidth = Math.max(1, width);
        var font = Minecraft.getInstance().font;
        int groupHeight = font.lineHeight * 2 + LABEL_GAP;
        int row = 0;
        int right = 0;
        for (int index = 0; index < this.groupWidths.length; index++) {
            int left = index == 0 || right == 0 ? 0 : right + GROUP_GAP;
            if (left > 0 && left + this.groupWidths[index] > this.availableWidth) {
                row++;
                left = 0;
            }
            this.groupLeft[index] = left;
            this.groupTop[index] = row * (groupHeight + ROW_GAP);
            right = left + this.groupWidths[index];
        }
        boolean inlineAge = row == 0 && right + GROUP_GAP + this.reservedAgeWidth <= this.availableWidth;
        this.ageTop = inlineAge ? font.lineHeight + LABEL_GAP : this.groupTop[2] + groupHeight + AGE_GAP;
        int height = inlineAge ? groupHeight : this.ageTop + font.lineHeight;
        if (this.verticalSizing().get().value != height) {
            this.verticalSizing(Sizing.fixed(height));
        }
    }

    void update(
        @Nullable Double buy,
        @Nullable Double sell,
        @Nullable Double buyAverage,
        @Nullable Double sellAverage,
        String age,
        List<Component> buyDetail,
        List<Component> sellDetail
    ) {
        Double spread = buy == null || sell == null ? null : buy - sell;
        Double spreadPercent = spread == null || buy <= 0 ? null : spread / buy * 100;
        this.prices = List.of(Component.literal(HistoryAnalysis.exact(buy)).getVisualOrderText(),
            Component.literal(HistoryAnalysis.exact(spread)).getVisualOrderText(),
            Component.literal(HistoryAnalysis.exact(sell)).getVisualOrderText());
        this.percentages = List.of(this.percent(comparisonPercent(buy, buyAverage), true),
            this.percent(spreadPercent, false), this.percent(comparisonPercent(sell, sellAverage), true));
        this.age = Component.literal(age).getVisualOrderText();
        var palette = UiStyles.palette();
        List<Component> spreadDetail = List.of(
            Component.literal("Spread").withColor(palette.primary()),
            Component.literal("Buy Price minus Sell Price.").withColor(palette.label()),
            Component
                .literal("Spread: " + HistoryAnalysis.exact(spread)
                    + (spread == null || !Double.isFinite(spread) ? "" : " coins"))
                .withColor(palette.label()),
            Component.literal("Percentage: spread / current Buy Price x 100.").withColor(palette.muted()));
        this.details = List.of(WidgetTooltips.wrapped(buyDetail), WidgetTooltips.wrapped(spreadDetail),
            WidgetTooltips.wrapped(sellDetail));
        for (int index = 0; index < this.groupWidths.length; index++) {
            // Wider exceptional values can grow the reservation, but subsequent smaller values never shrink it.
            this.groupWidths[index] = Math.max(this.groupWidths[index], this.valueWidth(index));
        }
        this.layoutFor(this.availableWidth);
    }

    static @Nullable Double comparisonPercent(@Nullable Double price, @Nullable Double average) {
        if (price == null || average == null || !Double.isFinite(price) || !Double.isFinite(average) || average <= 0) {
            return null;
        }
        double percent = (price - average) / average * 100;
        return Double.isFinite(percent) ? percent : null;
    }

    private FormattedCharSequence percent(@Nullable Double value, boolean signed) {
        return Component.literal(value == null || !Double.isFinite(value)
            ? ""
            : String.format(Locale.ROOT, signed ? "(%+.1f%%)" : "(%.1f%%)", value)).getVisualOrderText();
    }

    private int valueWidth(int index) {
        var font = Minecraft.getInstance().font;
        int percentWidth = font.width(this.percentages.get(index));
        return font.width(this.prices.get(index)) + (percentWidth == 0 ? 0 : PERCENT_GAP + percentWidth);
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        if (this.prices.isEmpty()) {
            return;
        }
        this.text.begin();
        this.tooltip(List.<ClientTooltipComponent>of());
        var font = Minecraft.getInstance().font;
        for (int index = 0; index < 3; index++) {
            int left = this.x + this.groupLeft[index];
            int top = this.y + this.groupTop[index];
            int valueTop = top + font.lineHeight + LABEL_GAP;
            this.text.draw(graphics, font, this.titles.get(index), left, top, UiStyles.palette().muted(), false);
            this.text.draw(graphics, font, this.prices.get(index), left, valueTop, index == 0
                ? UiStyles.palette().buy() : index == 2 ? UiStyles.palette().sell() : UiStyles.palette().label(),
                false);
            if (font.width(this.percentages.get(index)) > 0) {
                this.text.draw(graphics, font, this.percentages.get(index),
                    left + font.width(this.prices.get(index)) + PERCENT_GAP,
                    valueTop, UiStyles.palette().muted(), false);
            }
            if (mouseX >= left && mouseX < left + this.valueWidth(index)
                && mouseY >= valueTop
                && mouseY < valueTop + font.lineHeight) {
                this.tooltip(this.details.get(index));
            }
        }
        this.text.draw(graphics, font, this.age, this.x + this.width - font.width(this.age), this.y + this.ageTop,
            UiStyles.palette().muted(), false);
    }
}
