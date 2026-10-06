package com.github.lutzluca.btrbz.core.iteminfo.charts;

import com.github.lutzluca.coflnet.HistoryPoint;

import java.time.Instant;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.BiPredicate;

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
    private long[] inferredConnectionLimits = new long[0];

    public void update(List<HistoryPoint> points, Instant requestedStart, Instant requestedEnd, boolean resetView) {
        this.update(points, requestedStart, requestedEnd, null, resetView);
    }

    public void update(
        List<HistoryPoint> points,
        Instant requestedStart,
        Instant requestedEnd,
        Duration expectedCadence,
        boolean resetView
    ) {
        boolean fullRange = this.start.equals(this.loadedStart) && this.end.equals(this.loadedEnd);
        this.history = points.stream().sorted(Comparator.comparing(HistoryPoint::timestamp)).toList();
        long cadence = expectedCadence == null ? 0 : expectedCadence.toMillis();
        this.inferredConnectionLimits = new long[0];
        if (cadence <= 0) {
            long[] rawIntervals = java.util.stream.IntStream.range(1, this.history.size())
                .mapToLong(index -> this.history.get(index).timestamp().toEpochMilli()
                    - this.history.get(index - 1).timestamp().toEpochMilli())
                .toArray();
            long[] intervals = java.util.Arrays.stream(rawIntervals).filter(interval -> interval > 0).sorted()
                .toArray();
            cadence = intervals.length < 2 ? 0 : intervals[(intervals.length - 1) / 4];
            this.inferredConnectionLimits = new long[rawIntervals.length];
            for (int index = 0; index < rawIntervals.length; index++) {
                long largest = 0;
                long secondLargest = 0;
                int evidence = 0;
                // Exclude this interval, so an isolated gap cannot justify itself. Two other
                // repeated longer intervals support sparse cadence among short sample doublets.
                for (int neighbor = Math.max(0, index - 4); neighbor < Math.min(rawIntervals.length,
                    index + 5); neighbor++) {
                    long interval = rawIntervals[neighbor];
                    if (neighbor == index || interval <= 0) {
                        continue;
                    }
                    evidence++;
                    if (interval >= largest) {
                        secondLargest = largest;
                        largest = interval;
                    } else if (interval > secondLargest) {
                        secondLargest = interval;
                    }
                }
                long localCadence = evidence >= 3 ? Math.max(cadence, secondLargest) : cadence;
                this.inferredConnectionLimits[index] = localCadence > Long.MAX_VALUE / 3
                    ? Long.MAX_VALUE : localCadence * 3;
            }
        }
        this.maximumConnectedGap = cadence > Long.MAX_VALUE / 3 ? Long.MAX_VALUE : cadence * 3;
        this.clearHover();
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
        this.hoverAt(null, time, 0, point -> true);
    }

    void hoverAt(Object owner, Instant time) {
        this.hoverAt(owner, time, 0, point -> true);
    }

    public void hoverAt(Object owner, Instant time, long toleranceMillis, Predicate<HistoryPoint> availability) {
        this.hoverAt(owner, time, toleranceMillis, availability, (before, after) -> true);
    }

    public void hoverAt(
        Object owner,
        Instant time,
        long toleranceMillis,
        Predicate<HistoryPoint> availability,
        BiPredicate<HistoryPoint, HistoryPoint> commonSeries
    ) {
        this.hover = this.select(time, toleranceMillis, availability, commonSeries).map(HistoryPoint::timestamp)
            .orElse(null);
        this.hoverOwner = owner;
    }

    void clearHover(Object owner) {
        if (this.hoverOwner == owner) {
            this.clearHover();
        }
    }

    /** Only adjacent raw samples should be passed here, before render downsampling. */
    public boolean connects(HistoryPoint before, HistoryPoint after) {
        long interval = after.timestamp().toEpochMilli() - before.timestamp().toEpochMilli();
        long allowance = this.maximumConnectedGap;
        if (this.inferredConnectionLimits.length > 0) {
            int index = this.lowerBound(after.timestamp()) - 1;
            if (index < 0 || index >= this.inferredConnectionLimits.length
                || !this.history.get(index).timestamp().equals(before.timestamp())) {
                return false;
            }
            allowance = this.inferredConnectionLimits[index];
        }
        return interval > 0 && interval <= allowance;
    }

    /** Includes neighboring raw samples needed to draw legitimate segments through viewport edges. */
    public List<HistoryPoint> drawingSamples() {
        int from = this.lowerBound(this.start);
        int to = this.lowerBound(this.end);
        while (to < this.history.size() && !this.history.get(to).timestamp().isAfter(this.end)) {
            to++;
        }
        return this.history.subList(Math.max(0, from - 1), Math.min(this.history.size(), to + 1));
    }

    public Optional<Connection> clippedConnection(HistoryPoint before, HistoryPoint after, Double from, Double to) {
        if (from == null || to == null
            || !Double.isFinite(from)
            || !Double.isFinite(to)
            || !this.connects(before, after)
            || after.timestamp().isBefore(this.start)
            || before.timestamp().isAfter(this.end)) {
            return Optional.empty();
        }
        Instant start = before.timestamp().isBefore(this.start) ? this.start : before.timestamp();
        Instant end = after.timestamp().isAfter(this.end) ? this.end : after.timestamp();
        if (!end.isAfter(start)) {
            return Optional.empty();
        }
        double duration = after.timestamp().toEpochMilli() - before.timestamp().toEpochMilli();
        double startFraction = (start.toEpochMilli() - before.timestamp().toEpochMilli()) / duration;
        double endFraction = (end.toEpochMilli() - before.timestamp().toEpochMilli()) / duration;
        return Optional.of(new Connection(start, from + (to - from) * startFraction,
            end, from + (to - from) * endFraction));
    }

    public record Connection(Instant start, double startValue, Instant end, double endValue) {}

    public void clearHover() {
        this.hover = null;
        this.hoverOwner = null;
    }

    public void pinAt(Instant time) {
        this.pinAt(time, 0, point -> true);
    }

    public void pinAt(Instant time, long toleranceMillis, Predicate<HistoryPoint> availability) {
        this.pinAt(time, toleranceMillis, availability, (before, after) -> true);
    }

    public void pinAt(
        Instant time,
        long toleranceMillis,
        Predicate<HistoryPoint> availability,
        BiPredicate<HistoryPoint, HistoryPoint> commonSeries
    ) {
        this.select(time, toleranceMillis, availability, commonSeries)
            .ifPresent(point -> this.pin = point.timestamp());
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

    private Optional<HistoryPoint> select(
        Instant time,
        long toleranceMillis,
        Predicate<HistoryPoint> availability,
        BiPredicate<HistoryPoint, HistoryPoint> commonSeries
    ) {
        if (time == null || time.isBefore(this.start) || time.isAfter(this.end) || this.history.isEmpty()) {
            return Optional.empty();
        }
        int next = this.lowerBound(time);
        HistoryPoint before = next == 0 ? null : this.history.get(next - 1);
        HistoryPoint after = next == this.history.size() ? null : this.history.get(next);
        long tolerance = Math.max(0, toleranceMillis);
        HistoryPoint nearby = null;
        if (before != null && availability.test(before)
            && time.toEpochMilli() - before.timestamp().toEpochMilli() <= tolerance) {
            nearby = before;
        }
        if (after != null && availability.test(after)
            && after.timestamp().toEpochMilli() - time.toEpochMilli() <= tolerance
            && (nearby == null || after.timestamp().toEpochMilli() - time.toEpochMilli() < time.toEpochMilli()
                - nearby.timestamp().toEpochMilli())) {
            nearby = after;
        }
        if (nearby != null) {
            return Optional.of(nearby);
        }
        if (before == null || after == null
            || !availability.test(before)
            || !availability.test(after)
            || !commonSeries.test(before, after)
            || !this.connects(before, after)) {
            return Optional.empty();
        }
        return Optional.of(time.toEpochMilli() - before.timestamp().toEpochMilli() <= after.timestamp().toEpochMilli()
            - time.toEpochMilli() ? before : after);
    }

    private Optional<HistoryPoint> nearest(Instant time) {
        if (time == null || this.history.isEmpty()) {
            return Optional.empty();
        }
        int next = this.lowerBound(time);
        if (next == 0) {
            return Optional.of(this.history.getFirst());
        }
        if (next == this.history.size()) {
            return Optional.of(this.history.getLast());
        }
        var before = this.history.get(next - 1);
        var after = this.history.get(next);
        return Optional.of(time.toEpochMilli() - before.timestamp().toEpochMilli() <= after.timestamp().toEpochMilli()
            - time.toEpochMilli() ? before : after);
    }

    private int lowerBound(Instant time) {
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
        return low;
    }
}
