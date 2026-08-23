package com.github.lutzluca.btrbz.core.bazaariteminfo;

import com.github.lutzluca.coflnet.BazaarHistoryPoint;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Resolves the history sample nearest to a cursor X coordinate. */
public final class HistorySelection {
    private HistorySelection() {}

    public static Optional<Selected> nearest(
        List<BazaarHistoryPoint> points,
        TimeProjection projection,
        int cursorX
    ) {
        Objects.requireNonNull(points, "points");
        Objects.requireNonNull(projection, "projection");
        Selected best = null;
        long bestDistance = Long.MAX_VALUE;
        for (int index = 0; index < points.size(); index++) {
            var point = points.get(index);
            if (point == null) {
                continue;
            }
            int x = projection.x(point.timestamp());
            long distance = Math.abs((long) cursorX - x);
            if (distance < bestDistance) {
                best = new Selected(index, point, x);
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    public record Selected(int index, BazaarHistoryPoint point, int x) {
        public Selected {
            point = Objects.requireNonNull(point, "point");
        }
    }
}
