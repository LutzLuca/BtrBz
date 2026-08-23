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
            return;
        }
        var time = geometry.time();
        drawSeries(graphics, geometry.buy(), BazaarStyles.BUY_ACCENT);
        drawSeries(graphics, geometry.sell(), BazaarStyles.SELL_ACCENT);
        this.drawSelectedMarkers(graphics, geometry);
        var font = Minecraft.getInstance().font;
        graphics.text(font, BazaarWidgetViewData.formatCompact(geometry.values().maximum()),
            this.x + 2, this.y + 2, BazaarStyles.MUTED_TEXT, false);
        if (this.controller.selection().isPresent()) {
            int crosshair = time.x(this.controller.selection().orElseThrow().point().timestamp());
            graphics.fill(crosshair, this.y + 2, crosshair + 1, this.y + this.height - 2, 0x90FFFFFF);
        }
        graphics.drawRectOutline(time.left(), this.y + 2, time.width(), Math.max(1, this.height - 4),
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
        this.cachedGeometry = ActivityChartGeometry.layout(
            this.controller.history(), this.controller.showBuy(), this.controller.showSell(), time,
            this.y + 2, Math.max(1, this.height - 4), OptionalInt.empty());
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
