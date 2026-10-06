package com.github.lutzluca.btrbz.core.iteminfo;

import com.github.lutzluca.btrbz.core.iteminfo.charts.HistoryAnalysis;
import com.github.lutzluca.btrbz.core.ui.UiStyles;
import com.github.lutzluca.btrbz.core.widgets.ui.RetainedTextRow;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetTooltips;
import com.github.lutzluca.btrbz.utils.Utils;
import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/** Balanced quotes, readable references and a separate quiet freshness line. */
final class LiveQuoteStrip extends BaseUIComponent {
    private final RetainedTextRow text = new RetainedTextRow();
    private final List<FormattedCharSequence> titles = List.of(Component.literal("Buy Price").getVisualOrderText(),
        Component.literal("Spread").getVisualOrderText(), Component.literal("Sell Price").getVisualOrderText());
    private final FormattedCharSequence referenceTitle = Component.literal("vs 7d average").getVisualOrderText();
    private List<FormattedCharSequence> prices = List.of();
    private List<FormattedCharSequence> references = List.of();
    private List<FormattedCharSequence> wideReferences = List.of();
    private List<List<ClientTooltipComponent>> details = List.of();
    private FormattedCharSequence age = Component.literal("Hypixel unavailable").getVisualOrderText();
    private FormattedCharSequence spreadPercent = Component.literal("Unavailable").getVisualOrderText();
    private boolean compact;

    LiveQuoteStrip() {
        this.sizing(Sizing.fill(100), Sizing.fixed(43));
    }

    void layoutFor(int width) {
        // A stable breakpoint keeps arriving reference data from moving the book.
        boolean nextCompact = width < 600;
        if (this.compact != nextCompact) {
            this.compact = nextCompact;
            this.verticalSizing(Sizing.fixed(nextCompact ? 54 : 43));
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
        this.prices = List.of(Component.literal(HistoryAnalysis.exact(buy)).getVisualOrderText(),
            Component.literal(HistoryAnalysis.exact(spread)).getVisualOrderText(),
            Component.literal(HistoryAnalysis.exact(sell)).getVisualOrderText());
        var references = List.of(this.reference(buy, buyAverage), this.reference(sell, sellAverage));
        this.references = references.stream().map(Component::getVisualOrderText).toList();
        this.wideReferences = references.stream()
            .map(value -> Component.literal("vs 7d average ").append(value).getVisualOrderText()).toList();
        String percent = spread == null || buy == 0
            ? "Unavailable"
            : String.format(Locale.ROOT, "%.1f%%", spread / buy * 100);
        this.spreadPercent = Component.literal(percent).getVisualOrderText();
        this.age = Component.literal(age).getVisualOrderText();
        this.details = List.of(WidgetTooltips.wrapped(buyDetail), WidgetTooltips.wrapped(sellDetail));
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
        this.tooltip(List.<ClientTooltipComponent>of());
        var font = Minecraft.getInstance().font;
        for (int index = 0; index < 3; index++) {
            this.drawText(graphics, font, this.titles.get(index), index, 0, UiStyles.palette().muted());
            this.drawText(graphics, font, this.prices.get(index), index, 11, index == 0
                ? UiStyles.palette().buy() : index == 2 ? UiStyles.palette().sell() : UiStyles.palette().label());
            if (index == 1) {
                this.drawText(graphics, font, this.spreadPercent, index, 22, UiStyles.palette().muted());
                continue;
            }
            int reference = index == 0 ? 0 : 1;
            if (this.compact) {
                this.drawReference(graphics, font, this.referenceTitle, index, 22, reference, mouseX, mouseY);
                this.drawReference(graphics, font, this.references.get(reference), index, 33, reference, mouseX,
                    mouseY);
            } else {
                this.drawReference(graphics, font, this.wideReferences.get(reference), index, 22, reference, mouseX,
                    mouseY);
            }
        }
        this.drawText(graphics, font, this.age, 2, this.compact ? 45 : 34, UiStyles.palette().muted());
    }

    private int alignedX(Font font, FormattedCharSequence value, int column) {
        return this.x
            + (column == 0 ? 0 : column == 1 ? (this.width - font.width(value)) / 2 : this.width - font.width(value));
    }

    private void drawText(
        OwoUIGraphics graphics,
        Font font,
        FormattedCharSequence value,
        int column,
        int offset,
        int color
    ) {
        this.text.draw(graphics, font, value, this.alignedX(font, value, column), this.y + offset, color, false);
    }

    private void drawReference(
        OwoUIGraphics graphics,
        Font font,
        FormattedCharSequence value,
        int column,
        int offset,
        int reference,
        int mouseX,
        int mouseY
    ) {
        this.drawText(graphics, font, value, column, offset, UiStyles.palette().muted());
        int left = this.alignedX(font, value, column);
        if (mouseX >= left && mouseX < left + font.width(value)
            && mouseY >= this.y + offset
            && mouseY < this.y + offset + font.lineHeight) {
            this.tooltip(this.details.get(reference));
        }
    }
}
