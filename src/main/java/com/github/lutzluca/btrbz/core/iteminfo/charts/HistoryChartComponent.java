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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.function.BooleanSupplier;
import com.github.lutzluca.coflnet.MayorTerm;

abstract class HistoryChartComponent extends BaseUIComponent {
    static final int LEFT = 56;
    static final int RIGHT = 6;
    private static final int TOP = 7;
    private static final int BOTTOM = 16;

    protected final HistoryViewport viewport;
    private final Supplier<ChartOptions> options;
    private final boolean quantity;
    private final RetainedTextRow text = new RetainedTextRow();
    private List<Segment> segments = List.of();
    private List<Band> bands = List.of();
    private List<Dot> dots = List.of();
    private MayorGuide mayorGuide;
    private BooleanSupplier mayorsVisible = () -> false;
    private boolean cachedMayors;
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
        if (this.timeAxisVisible != visible) {
            this.timeAxisVisible = visible;
            this.cachedRevision = -1;
        }
    }

    public void mayors(Supplier<List<MayorTerm>> mayors, BooleanSupplier visible) {
        this.mayorGuide = new MayorGuide(this.viewport, mayors);
        this.mayorsVisible = visible;
    }

    public int verticalOverhead(boolean timeAxis) {
        return this.top() + (timeAxis ? BOTTOM : 3);
    }

    @Override
    public void update(float delta, int mouseX, int mouseY) {
        super.update(delta, mouseX, mouseY);
        if (this.plotHovered(mouseX, mouseY) && !this.dragged) {
            this.inspect(this.timeAt(mouseX - this.x));
        } else {
            this.viewport.clearHover(this);
        }
    }

    private boolean plotHovered(double mouseX, double mouseY) {
        var root = this.root();
        return root != null && root.childAt((int) mouseX, (int) mouseY) == this
            && mouseX >= this.x + LEFT
            && mouseX < this.x + this.width - RIGHT
            && mouseY >= this.y + this.top()
            && mouseY < this.y + this.height - this.bottom();
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        var current = this.options.get();
        if (this.cachedRevision != this.viewport.revision() || !current.equals(this.cachedOptions)
            || this.cachedWidth != this.width
            || this.cachedHeight != this.height
            || this.cachedMayors != this.mayorsVisible.getAsBoolean()) {
            this.rebuild(current);
        }
        boolean hovered = this.plotHovered(mouseX, mouseY);
        graphics.fill(this.x + LEFT, this.y + this.top(), this.x + this.width - RIGHT,
            this.y + this.height - this.bottom(), 0xFF1C2026);
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
                graphics.fill(this.x + tick.position(), this.y + this.height - this.bottom(),
                    this.x + tick.position() + 1, this.y + this.height - this.bottom() + 3, 0x55514D45);
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
        for (var dot : this.dots) {
            graphics.fill(this.x + dot.x() - 1, this.y + dot.y() - 1,
                this.x + dot.x() + 2, this.y + dot.y() + 2, dot.color());
        }
        if (this.mayorGuide != null && this.mayorsVisible.getAsBoolean()) {
            this.mayorGuide.draw(graphics, this.x + LEFT, this.y + 2, this.plotWidth(),
                this.y + this.height - this.bottom(), mouseX, mouseY, this.unobstructed(mouseX, mouseY));
        }
        this.drawCrosshair(graphics, this.viewport.pin(), 0x99C8BFAE);
        this.drawCrosshair(graphics, this.viewport.hover(), 0x779C978D);
        if (this.unavailable) {
            this.text.draw(graphics, font, this.labels.getFirst(), this.x + LEFT + 8,
                this.y + this.top() + 8, 0xFFAAA59B, false);
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
        this.cachedMayors = this.mayorsVisible.getAsBoolean();
        var paths = new ArrayList<RenderPath>();
        var shading = new ArrayList<Shading>();
        if (options.buy()) {
            paths.addAll(this.paths(true, UiStyles.palette().buy(), options));
            if (!this.quantity && options.bands()) {
                shading.addAll(this.shading(true, UiStyles.palette().buy()));
            }
        }
        if (options.sell()) {
            paths.addAll(this.paths(false, UiStyles.palette().sell(), options));
            if (!this.quantity && options.bands()) {
                shading.addAll(this.shading(false, UiStyles.palette().sell()));
            }
        }
        for (var path : paths) {
            path.points().forEach(point -> this.include(point.value()));
        }
        for (var band : shading) {
            this.include(band.low().startValue());
            this.include(band.low().endValue());
            this.include(band.high().startValue());
            this.include(band.high().endValue());
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
        var points = new ArrayList<Dot>();
        for (var path : paths) {
            this.buildSeries(path, lines, points);
        }
        this.segments = List.copyOf(lines);
        this.dots = List.copyOf(points);
        this.bands = this.buildBands(shading);
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
        var span = Duration.between(this.viewport.start(), this.viewport.end());
        for (int count = Math.min(9, Math.max(2, this.plotWidth() / 60)); count >= 2; count--) {
            var ticks = new ArrayList<TimeTick>();
            int previousEnd = Integer.MIN_VALUE;
            boolean fits = true;
            for (var time : HistoryTime.ticks(this.viewport.start(), this.viewport.end(), count)) {
                var sequence = this.label(HistoryTime.axis(time, span));
                int width = font.width(sequence);
                int position = this.localX(time);
                int labelX = Math.clamp(position - width / 2, LEFT,
                    Math.max(LEFT, LEFT + this.plotWidth() - width));
                if (labelX < previousEnd + 8) {
                    fits = false;
                    break;
                }
                ticks.add(new TimeTick(position, labelX, sequence));
                previousEnd = labelX + width;
            }
            if (fits && !ticks.isEmpty()) {
                return List.copyOf(ticks);
            }
        }
        return List.of(new TimeTick(LEFT, LEFT, this.label(HistoryTime.axis(this.viewport.start(), span))));
    }

    /** Clip raw connections first, then reduce dense paths without losing their extrema. */
    private List<RenderPath> paths(boolean buy, int color, ChartOptions options) {
        var result = new ArrayList<RenderPath>();
        var points = new ArrayList<PlotPoint>();
        HistoryPoint previous = null;
        for (var point : this.viewport.drawingSamples()) {
            Double value = this.value(point, buy, options);
            var connection = previous == null
                ? java.util.Optional.<HistoryViewport.Connection>empty()
                : this.viewport.clippedConnection(previous, point, this.value(previous, buy, options), value);
            if (connection.isPresent()) {
                var clipped = connection.orElseThrow();
                this.append(points, clipped.start(), clipped.startValue());
                this.append(points, clipped.end(), clipped.endValue());
            } else {
                this.finishPath(result, points, color);
                if (finite(value) && this.visible(point.timestamp())) {
                    this.append(points, point.timestamp(), value);
                }
            }
            previous = finite(value) ? point : null;
        }
        this.finishPath(result, points, color);
        return List.copyOf(result);
    }

    private void append(List<PlotPoint> points, Instant time, double value) {
        if (points.isEmpty() || !points.getLast().time().equals(time)) {
            points.add(new PlotPoint(time, value));
        }
    }

    private void finishPath(List<RenderPath> paths, List<PlotPoint> points, int color) {
        if (!points.isEmpty()) {
            paths.add(new RenderPath(List.copyOf(points), color));
            points.clear();
        }
    }

    private void buildSeries(RenderPath path, List<Segment> lines, List<Dot> points) {
        if (path.points().size() == 1) {
            var point = path.points().getFirst();
            points.add(new Dot(this.localX(point.time()), this.localY(point.value()), path.color()));
            return;
        }
        var bucket = new ArrayList<PlotPoint>();
        PlotPoint previous = null;
        int column = -1;
        for (var point : path.points()) {
            int x = this.localX(point.time());
            if (column != x) {
                previous = this.flush(bucket, previous, path.color(), lines);
                bucket.clear();
            }
            column = x;
            bucket.add(point);
        }
        this.flush(bucket, previous, path.color(), lines);
    }

    private PlotPoint flush(List<PlotPoint> bucket, PlotPoint previous, int color, List<Segment> lines) {
        if (bucket.isEmpty()) {
            return previous;
        }
        int low = 0;
        int high = 0;
        for (int index = 0; index < bucket.size(); index++) {
            if (bucket.get(index).value() < bucket.get(low).value()) {
                low = index;
            }
            if (bucket.get(index).value() > bucket.get(high).value()) {
                high = index;
            }
        }
        int lastIndex = -1;
        for (int index : new int[]{0, Math.min(low, high), Math.max(low, high), bucket.size() - 1}) {
            if (index == lastIndex) {
                continue;
            }
            var point = bucket.get(index);
            if (previous != null) {
                lines.add(new Segment(this.localX(previous.time()), this.localY(previous.value()),
                    this.localX(point.time()), this.localY(point.value()), color));
            }
            previous = point;
            lastIndex = index;
        }
        return previous;
    }

    private List<Shading> shading(boolean buy, int color) {
        var result = new ArrayList<Shading>();
        HistoryPoint previous = null;
        for (var point : this.viewport.drawingSamples()) {
            Double low = buy ? point.minBuy() : point.minSell();
            Double high = buy ? point.maxBuy() : point.maxSell();
            if (!finite(low) || !finite(high) || high < low || !finite(buy ? point.buy() : point.sell())) {
                previous = null;
                continue;
            }
            var lower = previous == null
                ? java.util.Optional.<HistoryViewport.Connection>empty()
                : this.viewport.clippedConnection(previous, point, buy ? previous.minBuy() : previous.minSell(), low);
            var upper = previous == null
                ? java.util.Optional.<HistoryViewport.Connection>empty()
                : this.viewport.clippedConnection(previous, point, buy ? previous.maxBuy() : previous.maxSell(), high);
            if (lower.isPresent() && upper.isPresent()) {
                result.add(new Shading(lower.orElseThrow(), upper.orElseThrow(), color));
            } else if (this.visible(point.timestamp())) {
                result.add(new Shading(new HistoryViewport.Connection(point.timestamp(), low, point.timestamp(), low),
                    new HistoryViewport.Connection(point.timestamp(), high, point.timestamp(), high), color));
            }
            previous = point;
        }
        return List.copyOf(result);
    }

    private List<Band> buildBands(List<Shading> shading) {
        var result = new ArrayList<Band>();
        for (int color : new int[]{UiStyles.palette().buy(), UiStyles.palette().sell()}) {
            int[] tops = new int[this.plotWidth() + 1];
            int[] bottoms = new int[this.plotWidth() + 1];
            java.util.Arrays.fill(tops, Integer.MAX_VALUE);
            for (var band : shading) {
                if (band.color() != color) {
                    continue;
                }
                int start = this.localX(band.low().start()) - LEFT;
                int end = this.localX(band.low().end()) - LEFT;
                for (int column = start; column <= end; column++) {
                    double fraction = end == start ? 1 : (double) (column - start) / (end - start);
                    double upper = band.high().startValue()
                        + (band.high().endValue() - band.high().startValue()) * fraction;
                    double lower = band.low().startValue()
                        + (band.low().endValue() - band.low().startValue()) * fraction;
                    tops[column] = Math.min(tops[column], this.localY(upper));
                    bottoms[column] = Math.max(bottoms[column], this.localY(lower));
                }
            }
            for (int column = 0; column < tops.length; column++) {
                if (tops[column] != Integer.MAX_VALUE) {
                    result.add(new Band(LEFT + column, tops[column], bottoms[column],
                        (color & 0x00FFFFFF) | 0x22000000));
                }
            }
        }
        return List.copyOf(result);
    }

    private void drawCrosshair(OwoUIGraphics graphics, Instant time, int color) {
        if (time == null || !this.visible(time)) {
            return;
        }
        int x = this.x + this.localX(time);
        graphics.fill(x, this.y + this.top(), x + 1, this.y + this.height - this.bottom(), color);
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
        if (this.guideHovered(mouseX, mouseY) && !this.mayorGuide.tooltip().isEmpty()) {
            graphics.tooltip(Minecraft.getInstance().font, this.mayorGuide.tooltip(), mouseX, mouseY,
                HistoryInspectionTooltip.POSITIONER, null);
        } else if (this.shouldDrawTooltip(mouseX, mouseY)) {
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
        this.inspect(this.timeAt(mouseX));
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
            var options = this.options.get();
            this.viewport.pinAt(this.timeAt(click.x()), this.pickTolerance(), point -> this.available(point, options),
                (before, after) -> this.commonSeries(before, after, options));
        }
        this.pressed = false;
        this.dragged = false;
        return true;
    }

    @Override
    public boolean shouldDrawTooltip(double mouseX, double mouseY) {
        return !this.dragged && (this.guideHovered(mouseX, mouseY) && !this.mayorGuide.tooltip().isEmpty()
            || this.plotHovered(mouseX, mouseY) && this.viewport.hover() != null
                && super.shouldDrawTooltip(mouseX, mouseY));
    }

    private void inspect(Instant time) {
        var options = this.options.get();
        this.viewport.hoverAt(this, time, this.pickTolerance(), point -> this.available(point, options),
            (before, after) -> this.commonSeries(before, after, options));
    }

    private boolean available(HistoryPoint point, ChartOptions options) {
        return options.buy() && finite(this.value(point, true, options))
            || options.sell() && finite(this.value(point, false, options));
    }

    private boolean commonSeries(HistoryPoint before, HistoryPoint after, ChartOptions options) {
        return options.buy() && finite(this.value(before, true, options)) && finite(this.value(after, true, options))
            || options.sell() && finite(this.value(before, false, options))
                && finite(this.value(after, false, options));
    }

    private long pickTolerance() {
        return Math.max(1, Math.round(3.0 * Duration.between(this.viewport.start(), this.viewport.end()).toMillis()
            / this.plotWidth()));
    }

    private boolean unobstructed(double mouseX, double mouseY) {
        var root = this.root();
        return root != null && root.childAt((int) mouseX, (int) mouseY) == this;
    }

    private boolean guideHovered(double mouseX, double mouseY) {
        return this.mayorGuide != null && this.mayorsVisible.getAsBoolean()
            && this.unobstructed(mouseX, mouseY)
            && mouseX >= this.x + LEFT
            && mouseX < this.x + this.width - RIGHT
            && mouseY >= this.y + 2
            && mouseY < this.y + 2 + MayorGuide.HEIGHT;
    }

    private int top() {
        return TOP + (this.mayorsVisible.getAsBoolean() ? MayorGuide.HEIGHT : 0);
    }

    private int bottom() {
        return this.timeAxisVisible ? BOTTOM : 3;
    }

    private static boolean finite(Double value) {
        return value != null && Double.isFinite(value);
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
        return Math.max(1, this.height - this.top() - this.bottom());
    }

    private int localX(Instant time) {
        double fraction = (double) (time.toEpochMilli() - this.viewport.start().toEpochMilli())
            / (this.viewport.end().toEpochMilli() - this.viewport.start().toEpochMilli());
        return LEFT + (int) Math.round(Math.clamp(fraction, 0, 1) * this.plotWidth());
    }

    private int localY(double value) {
        return this.top()
            + (int) Math.round((1 - Math.clamp((value - this.minimum) / (this.maximum - this.minimum), 0, 1))
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

    private record PlotPoint(Instant time, double value) {}

    private record RenderPath(List<PlotPoint> points, int color) {}

    private record Shading(HistoryViewport.Connection low, HistoryViewport.Connection high, int color) {}

    private record Dot(int x, int y, int color) {}

    private record Segment(int x1, int y1, int x2, int y2, int color) {}

    private record Band(int x, int top, int bottom, int color) {}

    private record TimeTick(int position, int labelX, FormattedCharSequence text) {}

    private record YTick(int position, FormattedCharSequence text) {}
}
