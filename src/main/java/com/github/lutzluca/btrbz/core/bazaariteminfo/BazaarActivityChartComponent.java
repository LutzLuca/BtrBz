package com.github.lutzluca.btrbz.core.bazaariteminfo;

import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarStyles;
import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import java.util.Objects;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;

/** Optional interval-item activity plot sharing the History time projection and selection. */
public final class BazaarActivityChartComponent extends BaseUIComponent {
    private static final int BACKGROUND_COLOR = 0x440B0D12;
    private static final int CHART_PADDING = 8;

    private final BazaarHistoryPanelController controller;
    private ActivityChartGeometry.Geometry cachedGeometry;
    private long cachedGeometryRevision = -1;
    private int cachedX;
    private int cachedY;
    private int cachedWidth;
    private int cachedHeight;

    public BazaarActivityChartComponent(
        Sizing horizontal,
        Sizing vertical,
        BazaarHistoryPanelController controller
    ) {
        this.controller = Objects.requireNonNull(controller, "controller");
        this.sizing(horizontal, vertical);
        this.mouseLeave().subscribe(this.controller::clearSelection);
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        if (this.controller.activityMode() == BazaarItemInfoConfig.ActivityMode.Off || this.height <= 0) {
            return;
        }
        graphics.fill(this.x, this.y, this.x + this.width, this.y + this.height, BACKGROUND_COLOR);
        if (this.isInBoundingBox(mouseX, mouseY)) {
            this.controller.select(mouseX, this.x, this.width);
        }
        var geometry = this.geometry();
        if (geometry.isEmpty()) {
            var font = Minecraft.getInstance().font;
            graphics.text(font, "Market activity: items per interval",
                this.x + BazaarHistoryPanelController.AXIS_INSET, this.y + 4,
                BazaarStyles.MUTED_TEXT, false);
            return;
        }
        var time = geometry.time();
        drawSeries(graphics, geometry.buy(), BazaarStyles.BUY_ACCENT);
        drawSeries(graphics, geometry.sell(), BazaarStyles.SELL_ACCENT);
        this.drawSelectedMarkers(graphics, geometry);
        var font = Minecraft.getInstance().font;
        graphics.text(font, "Market activity: items per interval",
            time.left(), this.y + 4, BazaarStyles.SECONDARY_TEXT, false);
        int labelRight = time.left() - 6;
        this.drawAxisLabel(graphics, BazaarWidgetViewData.formatCompact(geometry.values().maximum()),
            labelRight, geometry.values().top());
        this.drawAxisLabel(graphics, "0", labelRight,
            geometry.values().top() + geometry.values().height() - font.lineHeight);
        if (this.controller.selection().isPresent()) {
            int crosshair = time.x(this.controller.selection().orElseThrow().point().timestamp());
            graphics.fill(crosshair, geometry.values().top(), crosshair + 1,
                geometry.values().top() + geometry.values().height(), 0x90FFFFFF);
        }
        graphics.drawRectOutline(time.left(), geometry.values().top(), time.width(), geometry.values().height(),
            BazaarStyles.PROGRESS_TRACK);
    }

    private ActivityChartGeometry.Geometry geometry() {
        long revision = this.controller.revision();
        if (this.cachedGeometry != null
            && this.cachedGeometryRevision == revision
            && this.cachedX == this.x
            && this.cachedY == this.y
            && this.cachedWidth == this.width
            && this.cachedHeight == this.height) {
            return this.cachedGeometry;
        }
        var time = this.controller.projection(this.x, this.width);
        int headerHeight = Minecraft.getInstance().font.lineHeight + CHART_PADDING;
        int plotTop = this.y + headerHeight;
        int plotHeight = Math.max(1, this.height - headerHeight - CHART_PADDING);
        this.cachedGeometry = ActivityChartGeometry.layout(
            this.controller.history(), this.controller.showBuy(), this.controller.showSell(), time,
            plotTop, plotHeight, OptionalInt.empty());
        this.cachedGeometryRevision = revision;
        this.cachedX = this.x;
        this.cachedY = this.y;
        this.cachedWidth = this.width;
        this.cachedHeight = this.height;
        return this.cachedGeometry;
    }

    private void drawSelectedMarkers(OwoUIGraphics graphics, ActivityChartGeometry.Geometry geometry) {
        var selected = this.controller.selection();
        if (selected.isEmpty()) {
            return;
        }
        var point = selected.orElseThrow().point();
        int x = geometry.time().x(point.timestamp());
        if (this.controller.showBuy() && point.buyVolume() >= 0) {
            selectedMarker(graphics, x, geometry.values().y(point.buyVolume()), BazaarStyles.BUY_ACCENT);
        }
        if (this.controller.showSell() && point.sellVolume() >= 0) {
            selectedMarker(graphics, x, geometry.values().y(point.sellVolume()), BazaarStyles.SELL_ACCENT);
        }
    }

    private void drawAxisLabel(OwoUIGraphics graphics, String label, int labelRight, int y) {
        var font = Minecraft.getInstance().font;
        int labelX = Math.max(this.x + 4, labelRight - font.width(label));
        graphics.text(font, label, labelX, y, BazaarStyles.MUTED_TEXT, false);
    }

    private static void selectedMarker(OwoUIGraphics graphics, int x, int y, int color) {
        graphics.fill(x - 2, y - 2, x + 3, y + 3, color);
    }

    private static void drawSeries(OwoUIGraphics graphics, ActivityChartGeometry.Series series, int color) {
        Color owoColor = BazaarStyles.color(color);
        for (var segment : series.segments()) {
            graphics.drawLine(segment.start().x(), segment.start().y(), segment.end().x(), segment.end().y(),
                1.5, owoColor);
        }
        for (var point : series.selectedMarkers()) {
            graphics.fill(point.x() - 2, point.y() - 2, point.x() + 3, point.y() + 3, color);
        }
    }
}
