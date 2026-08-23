package com.github.lutzluca.btrbz.core.bazaariteminfo;

import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarStyles;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetSurfaces;
import com.github.lutzluca.btrbz.utils.Utils;
import com.github.lutzluca.btrbz.core.bazaariteminfo.PriceChartGeometry.BandSegment;
import com.github.lutzluca.btrbz.core.bazaariteminfo.PriceChartGeometry.Geometry;
import com.github.lutzluca.btrbz.core.bazaariteminfo.PriceChartGeometry.Series;
import com.github.lutzluca.coflnet.BazaarHistoryPoint;
import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Price plot with adaptive markers, shared hover selection, and a combined tooltip. */
public final class BazaarHistoryChartComponent extends BaseUIComponent {
    private static final int PLOT_PADDING = 8;
    private static final int GRID_LINES = 4;
    private static final int GRID_COLOR = 0x283A414D;
    private static final int BACKGROUND_COLOR = 0x440B0D12;
    private static final int BAND_ALPHA = 0x30;
    private static final int TOOLTIP_BACKGROUND = 0xEE11141A;
    private static final int TOOLTIP_HORIZONTAL_PADDING = 7;
    private static final int TOOLTIP_VERTICAL_PADDING = 6;
    private static final int TOOLTIP_LINE_GAP = 1;

    private final BazaarHistoryPanelController controller;
    private Geometry cachedGeometry;
    private long cachedGeometryRevision = -1;
    private int cachedGeometryX;
    private int cachedGeometryWidth;
    private int cachedPlotTop;
    private int cachedPlotHeight;
    private TooltipLayout cachedTooltip;
    private long cachedTooltipRevision = -1;
    private int cachedTooltipIndex = -1;
    private ZoneId cachedTooltipZone;

    public BazaarHistoryChartComponent(Sizing horizontal, Sizing vertical) {
        this(horizontal, vertical, new BazaarHistoryPanelController());
    }

    public BazaarHistoryChartComponent(
        Sizing horizontal,
        Sizing vertical,
        BazaarHistoryPanelController controller
    ) {
        this.controller = Objects.requireNonNull(controller, "controller");
        this.sizing(Objects.requireNonNull(horizontal, "horizontal"), Objects.requireNonNull(vertical, "vertical"));
        this.mouseLeave().subscribe(this.controller::clearSelection);
    }

    public BazaarHistoryChartComponent history(List<BazaarHistoryPoint> history) {
        this.controller.update(
            history, this.controller.range(), this.controller.showBuy(), this.controller.showSell(),
            this.controller.showBands(), this.controller.activityMode());
        return this;
    }

    public BazaarHistoryChartComponent visibility(boolean buy, boolean sell, boolean bands) {
        this.controller.update(
            this.controller.history(), this.controller.range(), buy, sell, bands, this.controller.activityMode());
        return this;
    }

    public List<BazaarHistoryPoint> history() {
        return this.controller.history();
    }

    public PriceChartGeometry.Visibility visibility() {
        return new PriceChartGeometry.Visibility(
            this.controller.showBuy(), this.controller.showSell(), this.controller.showBands());
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        graphics.fill(this.x, this.y, this.x + this.width, this.y + this.height, BACKGROUND_COLOR);
        if (this.isInBoundingBox(mouseX, mouseY)) {
            this.controller.select(mouseX, this.x, this.width);
        }
        if (this.width <= BazaarHistoryPanelController.AXIS_INSET || this.height <= 2 * PLOT_PADDING) {
            return;
        }
        if (!this.controller.showBuy() && !this.controller.showSell()) {
            this.drawCenteredMessage(graphics, "All series hidden");
            return;
        }
        int plotTop = this.y + PLOT_PADDING;
        int plotHeight = Math.max(1, this.height - 2 * PLOT_PADDING);
        Geometry geometry = this.geometry(plotTop, plotHeight);
        if (geometry.isEmpty()) {
            this.drawCenteredMessage(graphics, "No history data");
            return;
        }
        var time = geometry.time();
        this.drawGrid(graphics, time, plotTop, plotHeight);
        drawBand(graphics, geometry.buyBands(), withAlpha(BazaarStyles.BUY_ACCENT, BAND_ALPHA));
        drawBand(graphics, geometry.sellBands(), withAlpha(BazaarStyles.SELL_ACCENT, BAND_ALPHA));
        drawSeries(graphics, geometry.buy(), BazaarStyles.BUY_ACCENT);
        drawSeries(graphics, geometry.sell(), BazaarStyles.SELL_ACCENT);
        this.drawSelectedMarkers(graphics, geometry);
        this.drawLabels(graphics, geometry.values(), geometry.time(), plotTop, plotHeight);
        this.drawSelection(graphics, geometry, plotTop, plotHeight);
    }

    private Geometry geometry(int plotTop, int plotHeight) {
        long revision = this.controller.revision();
        if (this.cachedGeometry != null
            && this.cachedGeometryRevision == revision
            && this.cachedGeometryX == this.x
            && this.cachedGeometryWidth == this.width
            && this.cachedPlotTop == plotTop
            && this.cachedPlotHeight == plotHeight) {
            return this.cachedGeometry;
        }
        var time = this.controller.projection(this.x, this.width);
        this.cachedGeometry = PriceChartGeometry.layout(
            this.controller.history(),
            this.visibility(),
            time,
            plotTop,
            plotHeight,
            OptionalInt.empty());
        this.cachedGeometryRevision = revision;
        this.cachedGeometryX = this.x;
        this.cachedGeometryWidth = this.width;
        this.cachedPlotTop = plotTop;
        this.cachedPlotHeight = plotHeight;
        return this.cachedGeometry;
    }

    private void drawGrid(OwoUIGraphics graphics, TimeProjection time, int top, int height) {
        for (int index = 0; index <= GRID_LINES; index++) {
            int y = top + (int) Math.round((double) index / GRID_LINES * (height - 1));
            graphics.fill(time.left(), y, time.left() + time.width(), y + 1, GRID_COLOR);
        }
        graphics.drawRectOutline(time.left(), top, time.width(), height, BazaarStyles.PROGRESS_TRACK);
    }

    private void drawLabels(
        OwoUIGraphics graphics,
        ValueProjection values,
        TimeProjection time,
        int top,
        int height
    ) {
        var font = Minecraft.getInstance().font;
        var labels = axisLabels(values);
        int labelRight = time.left() - 6;
        drawAxisLabel(graphics, labels.get(0), labelRight, top);
        drawAxisLabel(graphics, labels.get(1), labelRight,
            top + Math.max(0, (height - font.lineHeight) / 2));
        drawAxisLabel(graphics, labels.get(2), labelRight,
            Math.max(top, top + height - font.lineHeight));
    }

    static List<String> axisLabels(ValueProjection values) {
        Objects.requireNonNull(values, "values");
        double midpoint = values.minimum() + (values.maximum() - values.minimum()) / 2;
        var raw = List.of(values.maximum(), midpoint, values.minimum());
        double largestAbsolute = raw.stream().mapToDouble(Math::abs).max().orElse(0);
        if (largestAbsolute < 100_000) {
            return raw.stream().map(BazaarWidgetViewData::formatPrice).toList();
        }
        int firstPlaces = largestAbsolute >= 1_000_000 ? 2 : 1;
        for (int places = firstPlaces; places <= 3; places++) {
            final int precision = places;
            var labels = raw.stream().map(value -> Utils.formatCompact(value, precision)).toList();
            if (new HashSet<>(labels).size() == labels.size()) {
                return labels;
            }
        }
        return raw.stream().map(BazaarWidgetViewData::formatPrice).toList();
    }

    private void drawAxisLabel(OwoUIGraphics graphics, String label, int labelRight, int y) {
        var font = Minecraft.getInstance().font;
        int labelX = Math.max(this.x + PLOT_PADDING, labelRight - font.width(label));
        graphics.text(font, label, labelX, y, BazaarStyles.MUTED_TEXT, false);
    }

    private void drawSelectedMarkers(OwoUIGraphics graphics, Geometry geometry) {
        var selected = this.controller.selection();
        if (selected.isEmpty()) {
            return;
        }
        var point = selected.orElseThrow().point();
        int x = geometry.time().x(point.timestamp());
        if (this.controller.showBuy() && Double.isFinite(point.buy())) {
            marker(graphics, new PriceChartGeometry.PixelPoint(x, geometry.values().y(point.buy())),
                BazaarStyles.BUY_ACCENT, 2);
        }
        if (this.controller.showSell() && Double.isFinite(point.sell())) {
            marker(graphics, new PriceChartGeometry.PixelPoint(x, geometry.values().y(point.sell())),
                BazaarStyles.SELL_ACCENT, 2);
        }
    }

    private void drawSelection(OwoUIGraphics graphics, Geometry geometry, int top, int height) {
        var selected = this.controller.selection();
        if (selected.isEmpty()) {
            return;
        }
        var time = geometry.time();
        int crosshair = time.x(selected.orElseThrow().point().timestamp());
        graphics.fill(crosshair, top, crosshair + 1, top + height, 0x90FFFFFF);
        this.drawTooltip(graphics, selected.orElseThrow(), crosshair, time, top, height);
    }

    private void drawTooltip(
        OwoUIGraphics graphics,
        HistorySelection.Selected selected,
        int crosshair,
        TimeProjection time,
        int top,
        int height
    ) {
        var tooltip = this.tooltip(selected);
        var font = Minecraft.getInstance().font;
        int boxWidth = tooltip.width();
        int boxHeight = tooltip.height();
        int preferredX = crosshair < time.left() + time.width() / 2
            ? crosshair + 7
            : crosshair - boxWidth - 7;
        int x = Math.max(time.left(), Math.min(time.left() + time.width() - boxWidth, preferredX));
        int y = Math.max(top, Math.min(top + height - boxHeight, top + 6));
        WidgetSurfaces.drawRoundedPanel(graphics, x, y, boxWidth, boxHeight, TOOLTIP_BACKGROUND, 4);
        int textY = y + TOOLTIP_VERTICAL_PADDING;
        for (var line : tooltip.content().lines()) {
            textY += line.gapBefore();
            graphics.text(font, line.text(), x + TOOLTIP_HORIZONTAL_PADDING, textY, line.color(), false);
            textY += font.lineHeight + TOOLTIP_LINE_GAP;
        }
    }

    private TooltipLayout tooltip(HistorySelection.Selected selected) {
        var zone = ZoneId.systemDefault();
        long revision = this.controller.revision();
        if (this.cachedTooltip != null
            && this.cachedTooltipRevision == revision
            && this.cachedTooltipIndex == selected.index()
            && zone.equals(this.cachedTooltipZone)) {
            return this.cachedTooltip;
        }
        var content = BazaarHistoryTooltip.create(
            selected.point(),
            this.controller.showBuy(),
            this.controller.showSell(),
            this.controller.showBands(),
            this.controller.activityMode(),
            zone);
        var font = Minecraft.getInstance().font;
        int textWidth = content.lines().stream().mapToInt(line -> font.width(line.text())).max().orElse(0);
        int contentHeight = 0;
        for (var line : content.lines()) {
            contentHeight += line.gapBefore() + font.lineHeight + TOOLTIP_LINE_GAP;
        }
        if (!content.lines().isEmpty()) {
            contentHeight -= TOOLTIP_LINE_GAP;
        }
        this.cachedTooltip = new TooltipLayout(
            content,
            textWidth + 2 * TOOLTIP_HORIZONTAL_PADDING,
            contentHeight + 2 * TOOLTIP_VERTICAL_PADDING);
        this.cachedTooltipRevision = revision;
        this.cachedTooltipIndex = selected.index();
        this.cachedTooltipZone = zone;
        return this.cachedTooltip;
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
            graphics.drawLine(segment.start().x(), segment.start().y(), segment.end().x(), segment.end().y(),
                1.5, owoColor);
        }
        for (var point : series.ordinaryMarkers()) {
            marker(graphics, point, color, 1);
        }
        for (var point : series.selectedMarkers()) {
            marker(graphics, point, color, 2);
        }
    }

    private static void marker(OwoUIGraphics graphics, PriceChartGeometry.PixelPoint point, int color, int radius) {
        graphics.fill(point.x() - radius, point.y() - radius,
            point.x() + radius + 1, point.y() + radius + 1, color);
    }

    private static void drawBand(OwoUIGraphics graphics, List<BandSegment> segments, int color) {
        for (var segment : segments) {
            int startX = Math.min(segment.start().x(), segment.end().x());
            int endX = Math.max(segment.start().x(), segment.end().x());
            int span = endX - startX;
            for (int x = startX; x <= endX; x++) {
                double progress = span == 0 ? 0 : (double) (x - startX) / span;
                int top = interpolate(segment.start().top(), segment.end().top(), progress);
                int bottom = interpolate(segment.start().bottom(), segment.end().bottom(), progress);
                graphics.fill(x, Math.min(top, bottom), x + 1, Math.max(top, bottom) + 1, color);
            }
        }
    }

    private static int interpolate(int start, int end, double progress) {
        return (int) Math.round(start + (end - start) * progress);
    }

    private static int withAlpha(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | (alpha << 24);
    }

    private record TooltipLayout(BazaarHistoryTooltip.Content content, int width, int height) {}
}
