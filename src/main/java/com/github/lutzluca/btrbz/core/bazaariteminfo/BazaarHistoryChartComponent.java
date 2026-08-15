package com.github.lutzluca.btrbz.core.bazaariteminfo;

import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarStyles;
import com.github.lutzluca.btrbz.core.bazaariteminfo.BazaarHistoryChartGeometry.BandSegment;
import com.github.lutzluca.btrbz.core.bazaariteminfo.BazaarHistoryChartGeometry.Geometry;
import com.github.lutzluca.btrbz.core.bazaariteminfo.BazaarHistoryChartGeometry.Series;
import com.github.lutzluca.btrbz.core.bazaariteminfo.BazaarHistoryChartGeometry.Visibility;
import com.github.lutzluca.coflnet.BazaarHistoryPoint;
import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** owo component that renders Coflnet buy/sell history and its min-max envelopes. */
public final class BazaarHistoryChartComponent extends BaseUIComponent {
    private static final int LABEL_GAP = 4;
    private static final int PLOT_PADDING = 2;
    private static final int GRID_LINES = 4;
    private static final int GRID_COLOR = 0x283A414D;
    private static final int BACKGROUND_COLOR = 0x440B0D12;
    private static final int BAND_ALPHA = 0x30;

    private List<BazaarHistoryPoint> history = List.of();
    private Visibility visibility = new Visibility(true, true, true);

    public BazaarHistoryChartComponent(Sizing horizontal, Sizing vertical) {
        this.sizing(
            Objects.requireNonNull(horizontal, "horizontal"),
            Objects.requireNonNull(vertical, "vertical"));
    }

    public BazaarHistoryChartComponent history(List<BazaarHistoryPoint> history) {
        Objects.requireNonNull(history, "history");
        this.history = history.stream()
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing(BazaarHistoryPoint::timestamp))
            .toList();
        return this;
    }

    public BazaarHistoryChartComponent visibility(boolean buy, boolean sell, boolean bands) {
        this.visibility = new Visibility(buy, sell, bands);
        return this;
    }

    public List<BazaarHistoryPoint> history() {
        return this.history;
    }

    public Visibility visibility() {
        return this.visibility;
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        graphics.fill(this.x, this.y, this.x + this.width, this.y + this.height, BACKGROUND_COLOR);

        if (this.width <= 2 * PLOT_PADDING || this.height <= 2 * PLOT_PADDING) {
            return;
        }

        if (!this.visibility.anyVisible()) {
            this.drawCenteredMessage(graphics, "All series hidden");
            return;
        }

        Geometry initial = BazaarHistoryChartGeometry.layout(
            this.history,
            this.visibility,
            0,
            0,
            Math.max(1, this.width),
            Math.max(1, this.height));
        if (initial.isEmpty()) {
            this.drawCenteredMessage(graphics, "No history data");
            return;
        }

        var font = Minecraft.getInstance().font;
        String maximum = BazaarWidgetViewData.formatPrice(initial.bounds().maxValue());
        String minimum = BazaarWidgetViewData.formatPrice(initial.bounds().minValue());
        int labelWidth = Math.max(font.width(maximum), font.width(minimum));
        int plotLeft = this.x + PLOT_PADDING + labelWidth + LABEL_GAP;
        int plotTop = this.y + PLOT_PADDING;
        int plotWidth = Math.max(1, this.x + this.width - PLOT_PADDING - plotLeft);
        int plotHeight = Math.max(1, this.height - 2 * PLOT_PADDING);

        Geometry geometry = BazaarHistoryChartGeometry.layout(
            this.history,
            this.visibility,
            plotLeft,
            plotTop,
            plotWidth,
            plotHeight);

        for (int index = 0; index <= GRID_LINES; index++) {
            int y = plotTop + (int) Math.round((double) index / GRID_LINES * (plotHeight - 1));
            graphics.fill(plotLeft, y, plotLeft + plotWidth, y + 1, GRID_COLOR);
        }

        drawBand(graphics, geometry.buyBand(), withAlpha(BazaarStyles.BUY_ACCENT, BAND_ALPHA));
        drawBand(graphics, geometry.sellBand(), withAlpha(BazaarStyles.SELL_ACCENT, BAND_ALPHA));
        drawSeries(graphics, geometry.buy(), BazaarStyles.BUY_ACCENT);
        drawSeries(graphics, geometry.sell(), BazaarStyles.SELL_ACCENT);

        graphics.text(font, maximum, this.x + PLOT_PADDING, plotTop, BazaarStyles.MUTED_TEXT, false);
        graphics.text(
            font,
            minimum,
            this.x + PLOT_PADDING,
            Math.max(plotTop, plotTop + plotHeight - font.lineHeight),
            BazaarStyles.MUTED_TEXT,
            false);
        graphics.drawRectOutline(plotLeft, plotTop, plotWidth, plotHeight, BazaarStyles.PROGRESS_TRACK);
    }

    private void drawCenteredMessage(OwoUIGraphics graphics, String message) {
        var font = Minecraft.getInstance().font;
        int textX = this.x + Math.max(0, (this.width - font.width(message)) / 2);
        int textY = this.y + Math.max(0, (this.height - font.lineHeight) / 2);
        graphics.text(font, Component.literal(message), textX, textY, BazaarStyles.MUTED_TEXT, false);
    }

    private static void drawSeries(OwoUIGraphics graphics, Series series, int color) {
        Color owoColor = BazaarStyles.color(color);
        for (var segment : series.segments()) {
            graphics.drawLine(
                segment.start().x(),
                segment.start().y(),
                segment.end().x(),
                segment.end().y(),
                1.5,
                owoColor);
        }

        // A two-pixel marker keeps isolated points and coincident timestamps visible.
        for (var point : series.points()) {
            graphics.fill(point.x() - 1, point.y() - 1, point.x() + 2, point.y() + 2, color);
        }
    }

    private static void drawBand(OwoUIGraphics graphics, List<BandSegment> segments, int color) {
        for (var segment : segments) {
            int startX = Math.min(segment.start().x(), segment.end().x());
            int endX = Math.max(segment.start().x(), segment.end().x());
            int span = endX - startX;

            if (span == 0) {
                int top = Math.min(segment.start().top(), segment.start().bottom());
                int bottom = Math.max(segment.start().top(), segment.start().bottom());
                graphics.fill(startX - 1, top, startX + 2, Math.max(top + 1, bottom + 1), color);
                continue;
            }

            for (int x = startX; x <= endX; x++) {
                double progress = (double) (x - startX) / span;
                int top = interpolate(segment.start().top(), segment.end().top(), progress);
                int bottom = interpolate(segment.start().bottom(), segment.end().bottom(), progress);
                if (top > bottom) {
                    int swap = top;
                    top = bottom;
                    bottom = swap;
                }
                graphics.fill(x, top, x + 1, Math.max(top + 1, bottom + 1), color);
            }
        }
    }

    private static int interpolate(int start, int end, double progress) {
        return (int) Math.round(start + (end - start) * progress);
    }

    private static int withAlpha(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | (alpha << 24);
    }
}
