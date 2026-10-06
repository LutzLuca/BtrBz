package com.github.lutzluca.btrbz.core.iteminfo;

import com.github.lutzluca.btrbz.core.iteminfo.charts.HistoryAnalysis;
import com.github.lutzluca.btrbz.core.ui.UiStyles;
import com.github.lutzluca.btrbz.core.widgets.ui.RetainedTextRow;
import com.github.lutzluca.btrbz.utils.Utils;
import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/** Live quotes and their source age share one compact, stable strip. */
final class LiveQuoteStrip extends BaseUIComponent {
    private final RetainedTextRow text = new RetainedTextRow();
    private final List<FormattedCharSequence> titles = List.of(Component.literal("Buy Price").getVisualOrderText(),
        Component.literal("Spread").getVisualOrderText(), Component.literal("Sell Price").getVisualOrderText());
    private final FormattedCharSequence referenceTitle = Component.literal("vs 7d average").getVisualOrderText();
    private List<FormattedCharSequence> prices = List.of();
    private List<Component> references = List.of();
    private List<FormattedCharSequence> wideReferences = List.of();
    private List<Component> details = List.of();
    private FormattedCharSequence age = Component.literal("Hypixel unavailable").getVisualOrderText();
    private FormattedCharSequence spreadPercent = Component.literal("Unavailable").getVisualOrderText();
    private boolean compact;

    LiveQuoteStrip() {
        this.sizing(Sizing.fill(100), Sizing.fixed(28));
    }

    void layoutFor(int width) {
        boolean nextCompact = width < 520;
        if (this.compact != nextCompact) {
            this.compact = nextCompact;
            this.verticalSizing(Sizing.fixed(nextCompact ? 46 : 28));
        }
    }

    void update(
        @Nullable Double buy,
        @Nullable Double sell,
        @Nullable Double buyAverage,
        @Nullable Double sellAverage,
        String age,
        String buyDetail,
        String sellDetail
    ) {
        Double spread = buy == null || sell == null ? null : buy - sell;
        this.prices = List.of(Component.literal(HistoryAnalysis.exact(buy)).getVisualOrderText(),
            Component.literal(HistoryAnalysis.exact(spread)).getVisualOrderText(),
            Component.literal(HistoryAnalysis.exact(sell)).getVisualOrderText());
        this.references = List.of(this.reference(buy, buyAverage), this.reference(sell, sellAverage));
        this.wideReferences = this.references.stream()
            .map(value -> Component.literal("vs 7d average ").append(value).getVisualOrderText()).toList();
        String percent = spread == null || buy == 0
            ? "Unavailable"
            : String.format(Locale.ROOT, "%.1f%%", spread / buy * 100);
        this.spreadPercent = Component.literal(percent).getVisualOrderText();
        this.age = Component.literal(age).getVisualOrderText();
        this.details = List.of(Component.literal(buyDetail), Component.literal("Spread: "
            + HistoryAnalysis.exact(spread) + " (" + percent + ")"), Component.literal(sellDetail));
    }

    private Component reference(@Nullable Double price, @Nullable Double average) {
        String percent = price == null || average == null || average == 0
            ? ""
            : String.format(Locale.ROOT, " (%+.1f%%)", (price - average) / average * 100);
        return Component.literal((average == null ? "Unavailable" : Utils.formatCompact(average)) + percent);
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        if (this.prices.isEmpty()) {
            return;
        }
        this.text.begin();
        var font = Minecraft.getInstance().font;
        int quotesWidth = this.compact ? this.width : this.width - 112;
        int columnWidth = quotesWidth / 3;
        for (int index = 0; index < 3; index++) {
            int left = this.x + index * columnWidth;
            this.text.draw(graphics, font, this.titles.get(index), left,
                this.y, UiStyles.palette().muted(), false);
            this.text.draw(graphics, font, this.prices.get(index), left,
                this.y + 10, index == 0
                    ? UiStyles.palette().buy()
                    : index == 2 ? UiStyles.palette().sell() : UiStyles.palette().label(),
                false);
            if (index == 1) {
                this.small(graphics, this.spreadPercent, left, this.y + 21);
            } else {
                int reference = index == 0 ? 0 : 1;
                if (this.compact) {
                    this.small(graphics, this.referenceTitle, left, this.y + 21);
                    this.small(graphics, this.references.get(reference), left, this.y + 29);
                } else {
                    this.small(graphics, this.wideReferences.get(reference), left, this.y + 21);
                }
            }
        }
        this.small(graphics, this.age, this.compact ? this.x : this.x + quotesWidth + 8,
            this.compact ? this.y + 38 : this.y + 9);
        if (this.isInBoundingBox(mouseX, mouseY)) {
            int index = (mouseX - this.x) / Math.max(1, columnWidth);
            this.tooltip(index >= 0 && index < 3 ? List.of(this.details.get(index)) : List.of());
        }
    }

    private void small(OwoUIGraphics graphics, Component value, int x, int y) {
        this.small(graphics, value.getVisualOrderText(), x, y);
    }

    private void small(OwoUIGraphics graphics, FormattedCharSequence value, int x, int y) {
        graphics.push();
        try {
            graphics.translate(x, y);
            graphics.scale(0.8f, 0.8f);
            this.text.draw(graphics, Minecraft.getInstance().font, value, 0, 0, UiStyles.palette().muted(), false);
        } finally {
            graphics.pop();
        }
    }
}
