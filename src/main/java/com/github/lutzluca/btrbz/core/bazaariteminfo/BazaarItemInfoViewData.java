package com.github.lutzluca.btrbz.core.bazaariteminfo;

import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import com.github.lutzluca.coflnet.BazaarHistoryPoint;
import com.github.lutzluca.coflnet.BazaarSnapshot;
import com.github.lutzluca.coflnet.HistoryRange;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable presentation state for the Bazaar Item Info screen. */
public final class BazaarItemInfoViewData {
    private BazaarItemInfoViewData() {}

    public record ScreenState(
        String itemTag,
        HistoryRange range,
        LoadState<CurrentPrices> currentPrices,
        LoadState<History> history
    ) {
        public ScreenState {
            itemTag = Objects.requireNonNull(itemTag, "itemTag");
            range = Objects.requireNonNull(range, "range");
            currentPrices = Objects.requireNonNull(currentPrices, "currentPrices");
            history = Objects.requireNonNull(history, "history");
        }
    }

    public record CurrentPrices(
        double buy,
        double sell,
        String buyText,
        String sellText,
        Instant timestamp
    ) {
        public CurrentPrices {
            buyText = Objects.requireNonNull(buyText, "buyText");
            sellText = Objects.requireNonNull(sellText, "sellText");
            timestamp = Objects.requireNonNull(timestamp, "timestamp");
        }

        public static CurrentPrices from(BazaarSnapshot snapshot) {
            Objects.requireNonNull(snapshot, "snapshot");
            return new CurrentPrices(
                snapshot.buyPrice(),
                snapshot.sellPrice(),
                BazaarWidgetViewData.formatPrice(snapshot.buyPrice()),
                BazaarWidgetViewData.formatPrice(snapshot.sellPrice()),
                snapshot.timeStamp());
        }
    }

    /** Chronological, immutable history prepared for chart presentation. */
    public record History(List<BazaarHistoryPoint> points) {
        public History {
            Objects.requireNonNull(points, "points");
            points = points.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(BazaarHistoryPoint::timestamp))
                .toList();
        }
    }

    public sealed interface LoadState<T>
        permits Loading, Empty, Success, Failure {

        default Optional<T> retainedValue() {
            if (this instanceof Success<?> success) {
                @SuppressWarnings("unchecked")
                T value = (T) success.value();
                return Optional.of(value);
            }

            if (this instanceof Loading<?> loading) {
                @SuppressWarnings("unchecked")
                Optional<T> previous = (Optional<T>) loading.previous();
                return previous;
            }

            if (this instanceof Failure<?> failure) {
                @SuppressWarnings("unchecked")
                Optional<T> previous = (Optional<T>) failure.previous();
                return previous;
            }

            return Optional.empty();
        }

        default boolean isLoading() {
            return this instanceof Loading<?>;
        }
    }

    public record Loading<T>(Optional<T> previous) implements LoadState<T> {
        public Loading {
            previous = Objects.requireNonNull(previous, "previous");
        }
    }

    public record Empty<T>() implements LoadState<T> {}

    public record Success<T>(T value) implements LoadState<T> {
        public Success {
            value = Objects.requireNonNull(value, "value");
        }
    }

    public record Failure<T>(String message, Optional<T> previous) implements LoadState<T> {
        public Failure {
            message = Objects.requireNonNull(message, "message");
            previous = Objects.requireNonNull(previous, "previous");
        }
    }
}
