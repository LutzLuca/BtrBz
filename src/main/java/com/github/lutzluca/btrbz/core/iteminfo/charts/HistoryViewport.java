package com.github.lutzluca.btrbz.core.iteminfo.charts;

import com.github.lutzluca.coflnet.HistoryPoint;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Owns the time window and selection shared by both history plots. */
public final class HistoryViewport {
    private List<HistoryPoint> history = List.of();
    private Instant loadedStart = Instant.EPOCH;
    private Instant loadedEnd = Instant.EPOCH.plusSeconds(1);
    private Instant start = this.loadedStart;
    private Instant end = this.loadedEnd;
    private Instant hover;
    private Object hoverOwner;
    private Instant pin;
    private long revision;
    private long maximumConnectedGap = Long.MAX_VALUE;

    public void update(List<HistoryPoint> points, Instant requestedStart, Instant requestedEnd, boolean resetView) {
        boolean fullRange = this.start.equals(this.loadedStart) && this.end.equals(this.loadedEnd);
        this.history = points.stream().sorted(Comparator.comparing(HistoryPoint::timestamp)).toList();
        long[] intervals = java.util.stream.IntStream.range(1, this.history.size())
            .mapToLong(index -> this.history.get(index).timestamp().toEpochMilli()
                - this.history.get(index - 1).timestamp().toEpochMilli())
            .filter(interval -> interval > 0).sorted().toArray();
        this.maximumConnectedGap = intervals.length < 2
            ? Long.MAX_VALUE
            : intervals[(intervals.length - 1) / 2] * 3;
        this.loadedStart = requestedStart;
        this.loadedEnd = requestedEnd.isAfter(requestedStart) ? requestedEnd : requestedStart.plusMillis(1);
        if (resetView || fullRange) {
            this.start = this.loadedStart;
            this.end = this.loadedEnd;
        } else {
            this.clamp(this.start.toEpochMilli(), this.end.toEpochMilli());
        }
        this.revision++;
    }

    public List<HistoryPoint> history() {
        return this.history;
    }

    public Instant start() {
        return this.start;
    }

    public Instant end() {
        return this.end;
    }

    public Instant hover() {
        return this.hover;
    }

    public Instant pin() {
        return this.pin;
    }

    public long revision() {
        return this.revision;
    }

    public Optional<HistoryPoint> inspected() {
        return this.hover != null ? this.nearest(this.hover) : this.pinnedPoint();
    }

    public Optional<HistoryPoint> pinnedPoint() {
        return this.nearest(this.pin).filter(point -> point.timestamp().equals(this.pin));
    }

    public void hoverAt(Instant time) {
        this.hoverOwner = null;
        this.hover = this.nearest(time).map(HistoryPoint::timestamp).orElse(null);
    }

    void hoverAt(Object owner, Instant time) {
        this.hoverAt(time);
        this.hoverOwner = owner;
    }

    void clearHover(Object owner) {
        if (this.hoverOwner == owner) {
            this.clearHover();
        }
    }

    /** Unusually large gaps in the returned cadence are left blank, rather than interpolated. */
    boolean connects(HistoryPoint before, HistoryPoint after) {
        return after.timestamp().toEpochMilli() - before.timestamp().toEpochMilli() <= this.maximumConnectedGap;
    }

    public void clearHover() {
        this.hover = null;
        this.hoverOwner = null;
    }

    public void pinAt(Instant time) {
        this.pin = this.nearest(time).map(HistoryPoint::timestamp).orElse(null);
    }

    public void clearPin() {
        this.pin = null;
    }

    public void zoom(double factor, double anchorFraction) {
        if (!Double.isFinite(factor) || factor <= 0) {
            return;
        }
        double span = this.end.toEpochMilli() - this.start.toEpochMilli();
        double anchor = this.start.toEpochMilli() + span * Math.clamp(anchorFraction, 0, 1);
        double newSpan = Math.max(1, span / factor);
        this.clamp(anchor - newSpan * Math.clamp(anchorFraction, 0, 1),
            anchor + newSpan * (1 - Math.clamp(anchorFraction, 0, 1)));
        this.revision++;
    }

    public void pan(double fraction) {
        if (!Double.isFinite(fraction)) {
            return;
        }
        double offset = (this.end.toEpochMilli() - this.start.toEpochMilli()) * fraction;
        this.clamp(this.start.toEpochMilli() + offset, this.end.toEpochMilli() + offset);
        this.revision++;
    }

    public void reset() {
        this.start = this.loadedStart;
        this.end = this.loadedEnd;
        this.revision++;
    }

    private void clamp(double from, double to) {
        long low = this.loadedStart.toEpochMilli();
        long high = this.loadedEnd.toEpochMilli();
        long span = Math.max(1, Math.min(high - low, Math.round(to - from)));
        long left = Math.max(low, Math.min(high - span, Math.round(from)));
        this.start = Instant.ofEpochMilli(left);
        this.end = Instant.ofEpochMilli(left + span);
    }

    private Optional<HistoryPoint> nearest(Instant time) {
        if (time == null || this.history.isEmpty()) {
            return Optional.empty();
        }
        int low = 0;
        int high = this.history.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (this.history.get(middle).timestamp().isBefore(time)) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        if (low == 0) {
            return Optional.of(this.history.getFirst());
        }
        if (low == this.history.size()) {
            return Optional.of(this.history.getLast());
        }
        var before = this.history.get(low - 1);
        var after = this.history.get(low);
        return Optional.of(time.toEpochMilli() - before.timestamp().toEpochMilli() <= after.timestamp().toEpochMilli()
            - time.toEpochMilli() ? before : after);
    }
}
