package com.github.lutzluca.btrbz.core.bazaariteminfo;

import com.github.lutzluca.btrbz.core.bazaariteminfo.BazaarItemInfoConfig.ActivityMode;
import com.github.lutzluca.coflnet.BazaarHistoryPoint;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** Shared point selection and projection state for the two History plots. */
public final class BazaarHistoryPanelController {
    public static final int AXIS_INSET = 58;
    public static final int RIGHT_INSET = 10;

    private List<BazaarHistoryPoint> history = List.of();
    private BazaarItemInfoRange range = BazaarItemInfoRange.Day;
    private boolean showBuy = true;
    private boolean showSell = true;
    private boolean showBands = true;
    private ActivityMode activityMode = ActivityMode.IntervalItems;
    private Optional<HistorySelection.Selected> selection = Optional.empty();
    private List<BazaarHistoryPoint> sourceHistory = this.history;
    private long revision;
    private ProjectionCache projectionCache;
    private TickCache tickCache;
    private long selectionRevision = -1;
    private int selectionCursorX = Integer.MIN_VALUE;
    private int selectionComponentX;
    private int selectionComponentWidth;

    public void update(
        List<BazaarHistoryPoint> history,
        BazaarItemInfoRange range,
        boolean showBuy,
        boolean showSell,
        boolean showBands,
        ActivityMode activityMode
    ) {
        Objects.requireNonNull(history, "history");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(activityMode, "activityMode");
        boolean historyChanged = this.sourceHistory != history && !this.history.equals(history);
        boolean stateChanged = historyChanged
            || this.range != range
            || this.showBuy != showBuy
            || this.showSell != showSell
            || this.showBands != showBands
            || this.activityMode != activityMode;
        this.sourceHistory = history;
        if (historyChanged) {
            this.history = List.copyOf(history);
        }
        this.range = range;
        this.showBuy = showBuy;
        this.showSell = showSell;
        this.showBands = showBands;
        this.activityMode = activityMode;
        if (stateChanged) {
            this.revision++;
            this.projectionCache = null;
            this.tickCache = null;
            this.clearSelection();
        }
    }

    public TimeProjection projection(int componentX, int componentWidth) {
        if (this.projectionCache != null
            && this.projectionCache.revision() == this.revision
            && this.projectionCache.componentX() == componentX
            && this.projectionCache.componentWidth() == componentWidth) {
            return this.projectionCache.projection();
        }
        int left = componentX + AXIS_INSET;
        int width = Math.max(1, componentWidth - AXIS_INSET - RIGHT_INSET);
        long minimum = Long.MAX_VALUE;
        long maximum = Long.MIN_VALUE;
        for (var point : this.history) {
            if (point == null || point.timestamp() == null) {
                continue;
            }
            long epochMillis = point.timestamp().toEpochMilli();
            minimum = Math.min(minimum, epochMillis);
            maximum = Math.max(maximum, epochMillis);
        }
        var projection = minimum == Long.MAX_VALUE
            ? new TimeProjection(0, 0, left, width)
            : new TimeProjection(minimum, maximum, left, width);
        this.projectionCache = new ProjectionCache(this.revision, componentX, componentWidth, projection);
        return projection;
    }

    public void select(int cursorX, int componentX, int componentWidth) {
        if (!this.showBuy && !this.showSell) {
            this.selection = Optional.empty();
            return;
        }
        if (this.selectionRevision == this.revision
            && this.selectionCursorX == cursorX
            && this.selectionComponentX == componentX
            && this.selectionComponentWidth == componentWidth) {
            return;
        }
        this.selectionRevision = this.revision;
        this.selectionCursorX = cursorX;
        this.selectionComponentX = componentX;
        this.selectionComponentWidth = componentWidth;
        var next = HistorySelection.nearest(this.history, this.projection(componentX, componentWidth), cursorX);
        if (this.selection.map(HistorySelection.Selected::index)
            .equals(next.map(HistorySelection.Selected::index))) {
            return;
        }
        this.selection = next;
    }

    public void clearSelection() {
        this.selection = Optional.empty();
        this.selectionRevision = -1;
        this.selectionCursorX = Integer.MIN_VALUE;
    }

    public List<BazaarHistoryPoint> history() {
        return this.history;
    }

    public BazaarItemInfoRange range() {
        return this.range;
    }

    public boolean showBuy() {
        return this.showBuy;
    }

    public boolean showSell() {
        return this.showSell;
    }

    public boolean showBands() {
        return this.showBands;
    }

    public ActivityMode activityMode() {
        return this.activityMode;
    }

    public Optional<HistorySelection.Selected> selection() {
        return this.selection;
    }

    public OptionalInt selectedIndex() {
        return this.selection.isPresent()
            ? OptionalInt.of(this.selection.orElseThrow().index())
            : OptionalInt.empty();
    }

    public long revision() {
        return this.revision;
    }

    public List<TimeAxisTicks.Tick> ticks(int componentX, int componentWidth, ZoneId zone) {
        Objects.requireNonNull(zone, "zone");
        if (this.tickCache != null
            && this.tickCache.revision() == this.revision
            && this.tickCache.componentX() == componentX
            && this.tickCache.componentWidth() == componentWidth
            && this.tickCache.zone().equals(zone)) {
            return this.tickCache.ticks();
        }
        var ticks = TimeAxisTicks.generate(this.projection(componentX, componentWidth), this.range, zone);
        this.tickCache = new TickCache(this.revision, componentX, componentWidth, zone, ticks);
        return ticks;
    }

    private record ProjectionCache(
        long revision,
        int componentX,
        int componentWidth,
        TimeProjection projection
    ) {}

    private record TickCache(
        long revision,
        int componentX,
        int componentWidth,
        ZoneId zone,
        List<TimeAxisTicks.Tick> ticks
    ) {}
}
