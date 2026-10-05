package com.github.lutzluca.btrbz.core.iteminfo.charts;

import com.github.lutzluca.btrbz.core.widgets.ui.RetainedTextRow;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetTooltips;
import com.github.lutzluca.btrbz.core.ui.UiStyles;
import com.github.lutzluca.coflnet.HistoryPoint;
import com.github.lutzluca.coflnet.MayorTerm;
import com.mojang.blaze3d.platform.InputConstants;
import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

abstract class HistoryChartComponent extends BaseUIComponent {
    private static final int LEFT = 43;
    private static final int RIGHT = 6;
    private static final int TOP = 7;
    private static final int BOTTOM = 16;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MMM d HH:mm", Locale.ROOT)
        .withZone(ZoneOffset.UTC);

    protected final HistoryViewport viewport;
    private final Supplier<ChartOptions> options;
    private final Supplier<List<MayorTerm>> mayors;
    private final boolean quantity;
    private final RetainedTextRow text = new RetainedTextRow();
    private List<Segment> segments = List.of();
    private List<Band> bands = List.of();
    private List<MayorRegion> mayorRegions = List.of();
    private List<FormattedCharSequence> labels = List.of();
    private ChartOptions cachedOptions;
    private List<MayorTerm> cachedMayors = List.of();
    private long cachedRevision = -1;
    private int cachedWidth = -1;
    private int cachedHeight = -1;
    private double minimum;
    private double maximum;
    private boolean unavailable;
    private Instant tooltipPoint;
    private Instant tooltipPin;
    private long tooltipRevision = -1;
    private ChartOptions tooltipOptions;
    private List<MayorTerm> tooltipMayors = List.of();
    private boolean pressed;
    private boolean dragged;
    private double pressX;
    private double pressY;
    private double lastDragX;

    HistoryChartComponent(
        HistoryViewport viewport,
        Supplier<ChartOptions> options,
        Supplier<List<MayorTerm>> mayors,
        boolean quantity,
        int height
    ) {
        this.viewport = viewport;
        this.options = options;
        this.mayors = mayors;
        this.quantity = quantity;
        this.sizing(Sizing.fill(100), Sizing.fixed(height));
    }

    @Override
    public void update(float delta, int mouseX, int mouseY) {
        super.update(delta, mouseX, mouseY);
        if (this.plotHovered(mouseX, mouseY) && !this.dragged) {
            this.viewport.hoverAt(this, this.timeAt(mouseX - this.x));
        } else {
            this.viewport.clearHover(this);
        }
    }

    private boolean plotHovered(double mouseX, double mouseY) {
        var root = this.root();
        return root != null && root.childAt((int) mouseX, (int) mouseY) == this
            && mouseX >= this.x + LEFT
            && mouseX < this.x + this.width - RIGHT
            && mouseY >= this.y + TOP
            && mouseY < this.y + this.height - BOTTOM;
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        var current = this.options.get();
        var terms = this.mayors.get();
        if (this.cachedRevision != this.viewport.revision() || !current.equals(this.cachedOptions)
            || this.cachedWidth != this.width
            || this.cachedHeight != this.height
            || !terms.equals(this.cachedMayors)) {
            this.rebuild(current, terms);
        }
        boolean hovered = this.plotHovered(mouseX, mouseY);
        graphics.fill(this.x + LEFT, this.y + TOP, this.x + this.width - RIGHT,
            this.y + this.height - BOTTOM, 0x44302E2B);
        this.text.begin();
        if (!this.quantity) {
            this.drawMayors(graphics);
        }
        var font = Minecraft.getInstance().font;
        for (int i = 0; i < 3; i++) {
            int lineY = TOP + i * this.plotHeight() / 2;
            graphics.fill(this.x + LEFT, this.y + lineY, this.x + this.width - RIGHT,
                this.y + lineY + 1, 0x24514D45);
            this.text.draw(graphics, font, this.labels.get(i), this.x + 2,
                this.y + lineY - 3, 0xFFAAA59B, false);
        }
        this.text.draw(graphics, font, this.labels.get(3), this.x + LEFT,
            this.y + this.height - 11, 0xFFAAA59B, false);
        int endWidth = font.width(this.labels.get(4));
        this.text.draw(graphics, font, this.labels.get(4), this.x + this.width - RIGHT - endWidth,
            this.y + this.height - 11, 0xFFAAA59B, false);
        for (var band : this.bands) {
            graphics.fill(this.x + band.x(), this.y + band.top(), this.x + band.x() + 1,
                this.y + band.bottom() + 1, band.color());
        }
        for (var segment : this.segments) {
            graphics.drawLine(this.x + segment.x1(), this.y + segment.y1(), this.x + segment.x2(),
                this.y + segment.y2(), 1, Color.ofArgb(segment.color()));
        }
        this.drawCrosshair(graphics, this.viewport.pin(), 0x99C8BFAE);
        this.drawCrosshair(graphics, this.viewport.hover(), 0x779C978D);
        if (this.unavailable) {
            this.text.draw(graphics, font, this.labels.get(5), this.x + LEFT + 8,
                this.y + TOP + 8, 0xFFAAA59B, false);
        }
        if (hovered) {
            this.updateTooltip(current, terms);
        }
    }

    private void rebuild(ChartOptions options, List<MayorTerm> terms) {
        this.cachedOptions = options;
        this.cachedMayors = List.copyOf(terms);
        this.cachedRevision = this.viewport.revision();
        this.cachedWidth = this.width;
        this.cachedHeight = this.height;
        this.minimum = Double.POSITIVE_INFINITY;
        this.maximum = Double.NEGATIVE_INFINITY;
        for (var point : this.viewport.history()) {
            if (!this.visible(point.timestamp())) {
                continue;
            }
            if (options.buy()) {
                this.include(this.value(point, true, options));
            }
            if (options.sell()) {
                this.include(this.value(point, false, options));
            }
            if (!this.quantity && options.bands()) {
                if (options.buy()) {
                    this.include(point.minBuy());
                    this.include(point.maxBuy());
                }
                if (options.sell()) {
                    this.include(point.minSell());
                    this.include(point.maxSell());
                }
            }
        }
        this.unavailable = !Double.isFinite(this.minimum);
        if (this.unavailable) {
            this.minimum = 0;
            this.maximum = 1;
        } else if (this.quantity) {
            this.minimum = 0;
        }
        if (this.minimum == this.maximum) {
            double padding = Math.max(1, Math.abs(this.minimum) * .02);
            this.minimum = Math.max(0, this.minimum - padding);
            this.maximum += padding;
        }
        var lines = new ArrayList<Segment>();
        var ranges = new ArrayList<Band>();
        if (options.buy()) {
            this.buildSeries(true, UiStyles.palette().buy(), options, lines, ranges);
        }
        if (options.sell()) {
            this.buildSeries(false, UiStyles.palette().sell(), options, lines, ranges);
        }
        this.segments = List.copyOf(lines);
        this.bands = List.copyOf(ranges);
        this.labels = List.of(this.label(this.compact(this.maximum)),
            this.label(this.compact((this.maximum + this.minimum) / 2)), this.label(this.compact(this.minimum)),
            this.label(TIME.format(this.viewport.start())), this.label(TIME.format(this.viewport.end())),
            this.label(options.buy() || options.sell() ? "No returned values in this view" : "Select Buy or Sell"));
        if (!this.quantity) {
            var regions = new ArrayList<MayorRegion>();
            var font = Minecraft.getInstance().font;
            for (var term : terms) {
                if (term.end().isBefore(this.viewport.start()) || term.start().isAfter(this.viewport.end())) {
                    continue;
                }
                int left = this
                    .localX(term.start().isBefore(this.viewport.start()) ? this.viewport.start() : term.start());
                int right = this.localX(term.end().isAfter(this.viewport.end()) ? this.viewport.end() : term.end());
                var name = this.label(term.name());
                regions.add(new MayorRegion(left, right, font.width(name) + 8 <= right - left ? name : null));
            }
            this.mayorRegions = List.copyOf(regions);
        }
    }

    /** Per pixel, keep first, minimum, maximum and last returned values in timestamp order. */
    private void buildSeries(boolean buy, int color, ChartOptions options, List<Segment> lines, List<Band> ranges) {
        var bucket = new ArrayList<HistoryPoint>();
        HistoryPoint previous = null;
        HistoryPoint previousReturned = null;
        int column = -1;
        for (var point : this.viewport.history()) {
            if (!this.visible(point.timestamp())) {
                continue;
            }
            Double value = this.value(point, buy, options);
            int x = this.localX(point.timestamp());
            boolean gap = previousReturned != null && !this.viewport.connects(previousReturned, point);
            if (value == null || !Double.isFinite(value) || gap || (column != x && !bucket.isEmpty())) {
                previous = this.flush(bucket, previous, buy, color, options, lines);
                bucket.clear();
            }
            if (gap) {
                previous = null;
            }
            if (value == null || !Double.isFinite(value)) {
                previous = null;
                previousReturned = null;
                continue;
            }
            column = x;
            bucket.add(point);
            previousReturned = point;
        }
        this.flush(bucket, previous, buy, color, options, lines);
        if (!this.quantity && options.bands()) {
            this.buildBands(buy, color, ranges);
        }
    }

    private HistoryPoint flush(
        List<HistoryPoint> bucket,
        HistoryPoint previous,
        boolean buy,
        int color,
        ChartOptions options,
        List<Segment> lines
    ) {
        if (bucket.isEmpty()) {
            return previous;
        }
        int low = 0;
        int high = 0;
        for (int i = 0; i < bucket.size(); i++) {
            var point = bucket.get(i);
            if (this.value(point, buy, options) < this.value(bucket.get(low), buy, options)) {
                low = i;
            }
            if (this.value(point, buy, options) > this.value(bucket.get(high), buy, options)) {
                high = i;
            }
        }
        int[] selected = {0, Math.min(low, high), Math.max(low, high), bucket.size() - 1};
        int lastIndex = -1;
        for (int index : selected) {
            if (index == lastIndex) {
                continue;
            }
            var point = bucket.get(index);
            int x = this.localX(point.timestamp());
            int y = this.localY(this.value(point, buy, options));
            if (previous != null) {
                lines
                    .add(new Segment(this.localX(previous.timestamp()), this.localY(this.value(previous, buy, options)),
                        x, y, color));
            } else {
                lines.add(new Segment(x, y, x, y + 1, color));
            }
            previous = point;
            lastIndex = index;
        }
        return previous;
    }

    private void buildBands(boolean buy, int color, List<Band> ranges) {
        int[] tops = new int[this.plotWidth() + 1];
        int[] bottoms = new int[this.plotWidth() + 1];
        java.util.Arrays.fill(tops, Integer.MAX_VALUE);
        HistoryPoint previous = null;
        for (var point : this.viewport.history()) {
            if (!this.visible(point.timestamp())) {
                continue;
            }
            Double lower = buy ? point.minBuy() : point.minSell();
            Double upper = buy ? point.maxBuy() : point.maxSell();
            if (lower == null || upper == null
                || !Double.isFinite(lower)
                || !Double.isFinite(upper)
                || upper < lower
                || this.value(point, buy, this.cachedOptions) == null
                || !Double.isFinite(this.value(point, buy, this.cachedOptions))) {
                previous = null;
                continue;
            }
            int end = this.localX(point.timestamp()) - LEFT;
            if (previous != null && !this.viewport.connects(previous, point)) {
                previous = null;
            }
            int start = previous == null ? end : this.localX(previous.timestamp()) - LEFT;
            double previousLow = previous == null ? lower : (buy ? previous.minBuy() : previous.minSell());
            double previousHigh = previous == null ? upper : (buy ? previous.maxBuy() : previous.maxSell());
            for (int column = start; column <= end; column++) {
                double fraction = end == start ? 1 : (double) (column - start) / (end - start);
                tops[column] = Math.min(tops[column], this.localY(previousHigh + (upper - previousHigh) * fraction));
                bottoms[column] = Math.max(bottoms[column],
                    this.localY(previousLow + (lower - previousLow) * fraction));
            }
            previous = point;
        }
        for (int column = 0; column < tops.length; column++) {
            if (tops[column] != Integer.MAX_VALUE) {
                ranges.add(new Band(LEFT + column, tops[column], bottoms[column], (color & 0x00FFFFFF) | 0x22000000));
            }
        }
    }

    private void drawMayors(OwoUIGraphics graphics) {
        for (var region : this.mayorRegions) {
            graphics.fill(this.x + region.left(), this.y + TOP, this.x + region.right(),
                this.y + this.height - BOTTOM, 0x18514D45);
            graphics.fill(this.x + region.left(), this.y + TOP, this.x + region.left() + 1,
                this.y + this.height - BOTTOM, 0x55514D45);
            if (region.name() != null) {
                this.text.draw(graphics, Minecraft.getInstance().font, region.name(), this.x + region.left() + 4,
                    this.y + TOP + 3, 0xFF928A7E, false);
            }
        }
    }

    private void drawCrosshair(OwoUIGraphics graphics, Instant time, int color) {
        if (time == null || !this.visible(time)) {
            return;
        }
        int x = this.x + this.localX(time);
        graphics.fill(x, this.y + TOP, x + 1, this.y + this.height - BOTTOM, color);
    }

    private void updateTooltip(ChartOptions options, List<MayorTerm> terms) {
        var inspected = this.viewport.inspected().orElse(null);
        Instant timestamp = inspected == null ? null : inspected.timestamp();
        if (Objects.equals(timestamp, this.tooltipPoint) && Objects.equals(this.viewport.pin(), this.tooltipPin)
            && this.tooltipRevision == this.viewport.revision()
            && options.equals(this.tooltipOptions)
            && terms.equals(this.tooltipMayors)) {
            return;
        }
        this.tooltipPoint = timestamp;
        this.tooltipPin = this.viewport.pin();
        this.tooltipRevision = this.viewport.revision();
        this.tooltipOptions = options;
        this.tooltipMayors = List.copyOf(terms);
        if (inspected == null) {
            this.tooltip(List.<Component>of());
            return;
        }
        var lines = new ArrayList<Component>();
        lines.add(Component.literal(TIME.format(timestamp) + " UTC"));
        var pinned = this.viewport.pinnedPoint().orElse(null);
        if (options.buy()) {
            this.inspection(lines, "Buy", inspected, pinned, true, options);
        }
        if (options.sell()) {
            this.inspection(lines, "Sell", inspected, pinned, false, options);
        }
        if (pinned != null && !timestamp.equals(pinned.timestamp())) {
            lines.add(Component.literal("Compared with " + TIME.format(pinned.timestamp()) + " UTC"));
        }
        if (!this.quantity) {
            for (var term : terms) {
                if (timestamp.isBefore(term.start()) || !timestamp.isBefore(term.end())) {
                    continue;
                }
                lines.add(Component.literal("Recorded mayor: " + term.name()));
                for (var perk : term.perks()) {
                    lines
                        .add(Component.literal(perk.name() + (perk.description() == null || perk.description().isBlank()
                            ? "" : ": " + perk.description())));
                }
            }
        }
        this.tooltip(WidgetTooltips.wrapped(lines));
    }

    private void inspection(
        List<Component> lines,
        String label,
        HistoryPoint point,
        HistoryPoint pinned,
        boolean buy,
        ChartOptions options
    ) {
        Double price = buy ? point.buy() : point.sell();
        lines.add(Component.literal(label + ": " + HistoryAnalysis.exact(price) + " coins"));
        Long quantityValue = options.metric().value(point, buy);
        lines.add(Component.literal(label + " " + options.metric().label() + ": "
            + (quantityValue == null ? "Unavailable" : String.format(Locale.ROOT, "%,d", quantityValue))));
        if (pinned != null && !point.timestamp().equals(pinned.timestamp())) {
            lines.add(Component
                .literal(label + " change: " + HistoryAnalysis.comparison(price, buy ? pinned.buy() : pinned.sell())));
        }
        if (!this.quantity && options.bands()) {
            lines.add(Component
                .literal(label + " returned range: " + HistoryAnalysis.exact(buy ? point.minBuy() : point.minSell())
                    + " to " + HistoryAnalysis.exact(buy ? point.maxBuy() : point.maxSell())));
        }
    }

    @Override
    public boolean canFocus(FocusSource source) {
        return source == FocusSource.MOUSE_CLICK;
    }

    @Override
    public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
        if (!this.plotHovered(this.x + click.x(), this.y + click.y())) {
            return super.onMouseDown(click, doubled);
        }
        if (click.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            this.viewport.clearPin();
            return true;
        }
        if (click.button() != InputConstants.MOUSE_BUTTON_LEFT || click.x() < LEFT
            || click.x() > this.width - RIGHT) {
            return super.onMouseDown(click, doubled);
        }
        this.pressed = true;
        this.dragged = false;
        this.pressX = click.x();
        this.pressY = click.y();
        this.lastDragX = click.x();
        return true;
    }

    @Override
    public boolean onMouseDrag(MouseButtonEvent click, double deltaX, double deltaY) {
        if (!this.pressed) {
            return super.onMouseDrag(click, deltaX, deltaY);
        }
        if (Math.abs(click.x() - this.pressX) > 3 || Math.abs(click.y() - this.pressY) > 3) {
            this.dragged = true;
        }
        if (this.dragged) {
            this.viewport.clearHover();
            this.viewport.pan((this.lastDragX - click.x()) / this.plotWidth());
            this.lastDragX = click.x();
        }
        return true;
    }

    @Override
    public boolean onMouseUp(MouseButtonEvent click) {
        if (!this.pressed || click.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.onMouseUp(click);
        }
        if (!this.dragged && this.plotHovered(this.x + click.x(), this.y + click.y())) {
            this.viewport.pinAt(this.timeAt(click.x()));
        }
        this.pressed = false;
        this.dragged = false;
        return true;
    }

    @Override
    public boolean shouldDrawTooltip(double mouseX, double mouseY) {
        return !this.dragged && this.plotHovered(mouseX, mouseY) && super.shouldDrawTooltip(mouseX, mouseY);
    }

    private boolean visible(Instant time) {
        return !time.isBefore(this.viewport.start()) && !time.isAfter(this.viewport.end());
    }

    private Double value(HistoryPoint point, boolean buy, ChartOptions options) {
        if (!this.quantity) {
            return buy ? point.buy() : point.sell();
        }
        Long value = options.metric().value(point, buy);
        return value == null ? null : value.doubleValue();
    }

    private void include(Double value) {
        if (value != null && Double.isFinite(value)) {
            this.minimum = Math.min(this.minimum, value);
            this.maximum = Math.max(this.maximum, value);
        }
    }

    private int plotWidth() {
        return Math.max(1, this.width - LEFT - RIGHT);
    }

    private int plotHeight() {
        return Math.max(1, this.height - TOP - BOTTOM);
    }

    private int localX(Instant time) {
        double fraction = (double) (time.toEpochMilli() - this.viewport.start().toEpochMilli())
            / (this.viewport.end().toEpochMilli() - this.viewport.start().toEpochMilli());
        return LEFT + (int) Math.round(Math.clamp(fraction, 0, 1) * this.plotWidth());
    }

    private int localY(double value) {
        return TOP + (int) Math.round((1 - Math.clamp((value - this.minimum) / (this.maximum - this.minimum), 0, 1))
            * this.plotHeight());
    }

    private Instant timeAt(double localX) {
        double fraction = Math.clamp((localX - LEFT) / this.plotWidth(), 0, 1);
        return Instant.ofEpochMilli(this.viewport.start().toEpochMilli() + Math.round(fraction
            * (this.viewport.end().toEpochMilli() - this.viewport.start().toEpochMilli())));
    }

    private FormattedCharSequence label(String value) {
        return Component.literal(value).getVisualOrderText();
    }

    private String compact(double value) {
        double absolute = Math.abs(value);
        if (absolute >= 1_000_000_000) {
            return String.format(Locale.ROOT, "%.1fB", value / 1_000_000_000);
        }
        if (absolute >= 1_000_000) {
            return String.format(Locale.ROOT, "%.1fM", value / 1_000_000);
        }
        if (absolute >= 1_000) {
            return String.format(Locale.ROOT, "%.1fk", value / 1_000);
        }
        return String.format(Locale.ROOT, this.quantity ? "%.0f" : "%.1f", value);
    }

    private record Segment(int x1, int y1, int x2, int y2, int color) {}

    private record Band(int x, int top, int bottom, int color) {}

    private record MayorRegion(int left, int right, FormattedCharSequence name) {}
}
