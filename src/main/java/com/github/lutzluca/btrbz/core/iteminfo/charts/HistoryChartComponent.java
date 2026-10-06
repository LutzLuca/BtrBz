package com.github.lutzluca.btrbz.core.iteminfo.charts;

import com.github.lutzluca.btrbz.core.widgets.ui.RetainedTextRow;
import com.github.lutzluca.btrbz.core.ui.UiStyles;
import com.github.lutzluca.coflnet.HistoryPoint;
import com.mojang.blaze3d.platform.InputConstants;
import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.CursorStyle;
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
    static final int LEFT = 56;
    static final int RIGHT = 6;
    private static final int TOP = 7;
    private static final int BOTTOM = 16;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MMM d HH:mm", Locale.ROOT)
        .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter AXIS_TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
        .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter AXIS_DATE = DateTimeFormatter.ofPattern("MMM d", Locale.ROOT)
        .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter AXIS_SECONDS = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT)
        .withZone(ZoneOffset.UTC);

    protected final HistoryViewport viewport;
    private final Supplier<ChartOptions> options;
    private final boolean quantity;
    private final RetainedTextRow text = new RetainedTextRow();
    private List<Segment> segments = List.of();
    private List<Band> bands = List.of();
    private List<FormattedCharSequence> labels = List.of();
    private List<YTick> yTicks = List.of();
    private List<TimeTick> timeTicks = List.of();
    private boolean timeAxisVisible = true;
    private ChartOptions cachedOptions;
    private long cachedRevision = -1;
    private int cachedWidth = -1;
    private int cachedHeight = -1;
    private double minimum;
    private double maximum;
    private boolean unavailable;
    private Instant tooltipPoint;
    private Instant tooltipPin;
    private long tooltipRevision = -1;
    private int tooltipScreenWidth = -1;
    private ChartOptions tooltipOptions;
    private boolean pressed;
    private boolean dragged;
    private double pressX;
    private double pressY;
    private double lastDragX;

    HistoryChartComponent(
        HistoryViewport viewport,
        Supplier<ChartOptions> options,
        boolean quantity,
        int height
    ) {
        this.viewport = viewport;
        this.options = options;
        this.quantity = quantity;
        this.sizing(Sizing.fill(100), Sizing.fixed(height));
        this.cursorStyle(CursorStyle.POINTER);
    }

    public void timeAxisVisible(boolean visible) {
        this.timeAxisVisible = visible;
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
        if (this.cachedRevision != this.viewport.revision() || !current.equals(this.cachedOptions)
            || this.cachedWidth != this.width
            || this.cachedHeight != this.height) {
            this.rebuild(current);
        }
        boolean hovered = this.plotHovered(mouseX, mouseY);
        graphics.fill(this.x + LEFT, this.y + TOP, this.x + this.width - RIGHT,
            this.y + this.height - BOTTOM, 0xFF1C2026);
        this.text.begin();
        var font = Minecraft.getInstance().font;
        for (var tick : this.yTicks) {
            graphics.fill(this.x + LEFT, this.y + tick.position(), this.x + this.width - RIGHT,
                this.y + tick.position() + 1, 0x24514D45);
            this.text.draw(graphics, font, tick.text(), this.x + LEFT - 5 - font.width(tick.text()),
                this.y + tick.position() - 3, UiStyles.palette().label(), false);
        }
        if (this.timeAxisVisible) {
            for (var tick : this.timeTicks) {
                graphics.fill(this.x + tick.position(), this.y + this.height - BOTTOM,
                    this.x + tick.position() + 1, this.y + this.height - BOTTOM + 3, 0x55514D45);
                this.text.draw(graphics, font, tick.text(), this.x + tick.labelX(),
                    this.y + this.height - 11, UiStyles.palette().muted(), false);
            }
        }
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
            this.text.draw(graphics, font, this.labels.getFirst(), this.x + LEFT + 8,
                this.y + TOP + 8, 0xFFAAA59B, false);
        }
        if (hovered) {
            this.updateTooltip(current);
        }
    }

    private void rebuild(ChartOptions options) {
        this.cachedOptions = options;
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
        } else {
            double padding = (this.maximum - this.minimum) * .04;
            if (!this.quantity) {
                this.minimum = Math.max(0, this.minimum - padding);
            }
            this.maximum += padding;
        }
        this.yTicks = this.buildYTicks();
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
        this.labels = List.of(this.label(options.buy() || options.sell()
            ? "No returned values in this view" : "Select Buy or Sell"));
        this.timeTicks = this.buildTimeTicks();
    }

    private List<YTick> buildYTicks() {
        double desiredStep = (this.maximum - this.minimum) / Math.max(2, Math.min(4, this.plotHeight() / 28));
        double scale = Math.pow(10, Math.floor(Math.log10(desiredStep)));
        double fraction = desiredStep / scale;
        double step = (fraction <= 1 ? 1 : fraction <= 2 ? 2 : fraction <= 5 ? 5 : 10) * scale;
        if (this.quantity) {
            step = Math.max(1, step);
        }
        var ticks = new ArrayList<YTick>();
        double first = Math.ceil(this.minimum / step) * step;
        for (int index = 0; index < 8; index++) {
            double value = first + index * step;
            if (value > this.maximum) {
                break;
            }
            ticks.add(new YTick(this.localY(value), this.label(this.compact(value, step))));
        }
        return List.copyOf(ticks);
    }

    private List<TimeTick> buildTimeTicks() {
        var font = Minecraft.getInstance().font;
        long start = this.viewport.start().toEpochMilli();
        long span = this.viewport.end().toEpochMilli() - start;
        var intermediateFormat = span < 60_000 ? AXIS_SECONDS : span <= 86_400_000 ? AXIS_TIME : AXIS_DATE;
        for (int count = 6; count >= 2; count--) {
            var ticks = new ArrayList<TimeTick>();
            int previousEnd = Integer.MIN_VALUE;
            boolean fits = true;
            for (int index = 0; index < count; index++) {
                Instant time = Instant.ofEpochMilli(start + Math.round((double) span * index / (count - 1)));
                var format = index == 0 || index == count - 1 ? TIME : intermediateFormat;
                var sequence = this.label(format.format(time) + (index == 0 ? " UTC" : ""));
                int width = font.width(sequence);
                int position = this.localX(time);
                int labelX = index == 0
                    ? LEFT : index == count - 1
                        ? LEFT + this.plotWidth() - width
                        : position - width / 2;
                if (labelX < previousEnd + 8) {
                    fits = false;
                    break;
                }
                ticks.add(new TimeTick(position, labelX, sequence));
                previousEnd = labelX + width;
            }
            if (fits) {
                return List.copyOf(ticks);
            }
        }
        var first = this.label(intermediateFormat.format(this.viewport.start()) + " UTC");
        var last = this.label(intermediateFormat.format(this.viewport.end()));
        if (font.width(first) + font.width(last) + 8 > this.plotWidth() && span <= 86_400_000) {
            first = this.label(AXIS_TIME.format(this.viewport.start()) + " UTC");
            last = this.label(AXIS_TIME.format(this.viewport.end()));
        }
        return List.of(new TimeTick(LEFT, LEFT, first),
            new TimeTick(LEFT + this.plotWidth(), LEFT + this.plotWidth() - font.width(last), last));
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

    private void drawCrosshair(OwoUIGraphics graphics, Instant time, int color) {
        if (time == null || !this.visible(time)) {
            return;
        }
        int x = this.x + this.localX(time);
        graphics.fill(x, this.y + TOP, x + 1, this.y + this.height - BOTTOM, color);
    }

    private void updateTooltip(ChartOptions options) {
        var inspected = this.viewport.inspected().orElse(null);
        Instant timestamp = inspected == null ? null : inspected.timestamp();
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        if (Objects.equals(timestamp, this.tooltipPoint) && Objects.equals(this.viewport.pin(), this.tooltipPin)
            && this.tooltipRevision == this.viewport.revision()
            && options.equals(this.tooltipOptions)
            && this.tooltipScreenWidth == screenWidth) {
            return;
        }
        this.tooltipPoint = timestamp;
        this.tooltipPin = this.viewport.pin();
        this.tooltipRevision = this.viewport.revision();
        this.tooltipScreenWidth = screenWidth;
        this.tooltipOptions = options;
        if (inspected == null) {
            this.tooltip(List.<Component>of());
            return;
        }
        this.tooltip(
            List.of(new HistoryInspectionTooltip(inspected, this.viewport.pinnedPoint().orElse(null), options)));
    }

    @Override
    public void drawTooltip(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        if (this.shouldDrawTooltip(mouseX, mouseY)) {
            graphics.tooltip(Minecraft.getInstance().font, this.tooltip(), mouseX, mouseY,
                HistoryInspectionTooltip.POSITIONER, null);
        }
    }

    @Override
    public boolean onMouseScroll(double mouseX, double mouseY, double amount) {
        var window = Minecraft.getInstance().getWindow();
        boolean control = InputConstants.isKeyDown(window, InputConstants.KEY_LCONTROL)
            || InputConstants.isKeyDown(window, InputConstants.KEY_RCONTROL);
        if (!control || !this.plotHovered(this.x + mouseX, this.y + mouseY)) {
            return super.onMouseScroll(mouseX, mouseY, amount);
        }
        this.viewport.zoom(Math.pow(1.25, amount), (mouseX - LEFT) / this.plotWidth());
        this.viewport.hoverAt(this, this.timeAt(mouseX));
        return true;
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

    private String compact(double value, double step) {
        double absolute = Math.abs(value);
        double divisor = absolute >= 1_000_000_000
            ? 1_000_000_000 : absolute >= 1_000_000
                ? 1_000_000
                : absolute >= 1_000 ? 1_000 : 1;
        if (-Math.log10(step / divisor) > 3) {
            divisor = 1;
        }
        String suffix = divisor == 1_000_000_000 ? "B" : divisor == 1_000_000 ? "M" : divisor == 1_000 ? "k" : "";
        int decimals = Math.clamp((int) Math.ceil(-Math.log10(step / divisor)), 0, 6);
        return String.format(Locale.ROOT, "%." + decimals + "f", value / divisor) + suffix;
    }

    private record Segment(int x1, int y1, int x2, int y2, int color) {}

    private record Band(int x, int top, int bottom, int color) {}

    private record TimeTick(int position, int labelX, FormattedCharSequence text) {}

    private record YTick(int position, FormattedCharSequence text) {}
}
