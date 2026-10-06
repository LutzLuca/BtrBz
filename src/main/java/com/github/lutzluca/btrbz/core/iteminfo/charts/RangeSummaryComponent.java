package com.github.lutzluca.btrbz.core.iteminfo.charts;

import com.github.lutzluca.btrbz.core.ui.UiStyles;
import com.github.lutzluca.btrbz.core.widgets.ui.RetainedTextRow;
import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/** Exact sample statistics, with native-size text at constrained widths. */
public final class RangeSummaryComponent extends BaseUIComponent {
    private final RetainedTextRow text = new RetainedTextRow();
    private final List<Row> rows;
    private final boolean stacked;

    public RangeSummaryComponent(@Nullable HistoryAnalysis.Stats buy, @Nullable HistoryAnalysis.Stats sell) {
        this.rows = List.of(new Row("First-to-last change", change(buy), change(sell)),
            new Row("Observed low", value(buy, 0), value(sell, 0)),
            new Row("Observed high", value(buy, 1), value(sell, 1)),
            new Row("Sample average", value(buy, 2), value(sell, 2)));
        this.stacked = Minecraft.getInstance().getWindow().getGuiScaledWidth() - 72 < 280;
        this.sizing(Sizing.fill(100), Sizing.fixed(this.stacked ? 216 : 76));
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        this.text.begin();
        var font = Minecraft.getInstance().font;
        if (this.stacked) {
            for (int side = 0; side < 2; side++) {
                int top = this.y + side * 108;
                this.drawText(graphics, side == 0 ? "Buy" : "Sell", this.x, top,
                    side == 0 ? UiStyles.palette().buy() : UiStyles.palette().sell());
                for (int index = 0; index < this.rows.size(); index++) {
                    var row = this.rows.get(index);
                    String value = side == 0 ? row.buy() : row.sell();
                    this.drawText(graphics, row.label(), this.x, top + 15 + index * 22,
                        UiStyles.palette().muted());
                    this.drawText(graphics, value, this.x + this.width - font.width(value), top + 25 + index * 22,
                        UiStyles.palette().label());
                }
            }
            return;
        }
        int column = Math.max(64, this.rows.stream()
            .mapToInt(row -> Math.max(font.width(row.buy()), font.width(row.sell()))).max().orElse(64));
        int buyRight = this.x + this.width - column - 12;
        int sellRight = this.x + this.width;
        this.drawText(graphics, "Buy", buyRight - font.width("Buy"), this.y, UiStyles.palette().buy());
        this.drawText(graphics, "Sell", sellRight - font.width("Sell"), this.y, UiStyles.palette().sell());
        for (int index = 0; index < this.rows.size(); index++) {
            var row = this.rows.get(index);
            int top = this.y + 18 + index * 14;
            this.drawText(graphics, row.label(), this.x, top, UiStyles.palette().muted());
            this.drawText(graphics, row.buy(), buyRight - font.width(row.buy()), top, UiStyles.palette().label());
            this.drawText(graphics, row.sell(), sellRight - font.width(row.sell()), top, UiStyles.palette().label());
        }
    }

    private void drawText(OwoUIGraphics graphics, String value, int left, int top, int color) {
        FormattedCharSequence sequence = Component.literal(value).getVisualOrderText();
        this.text.draw(graphics, Minecraft.getInstance().font, sequence, left, top, color, false);
    }

    private static String change(@Nullable HistoryAnalysis.Stats stats) {
        return stats == null || stats.percent() == null
            ? "Unavailable"
            : String.format(Locale.ROOT, "%+.1f%%", stats.percent());
    }

    private static String value(@Nullable HistoryAnalysis.Stats stats, int field) {
        return stats == null ? "Unavailable" : HistoryAnalysis.exact(switch (field) {
            case 0 -> stats.low();
            case 1 -> stats.high();
            default -> stats.average();
        });
    }

    private record Row(String label, String buy, String sell) {}
}
