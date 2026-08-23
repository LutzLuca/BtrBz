package com.github.lutzluca.btrbz.core.bazaariteminfo;

import com.github.lutzluca.btrbz.core.bazaariteminfo.BazaarItemInfoConfig.ActivityMode;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetDisplayOptions.NumberStyle;
import com.github.lutzluca.btrbz.data.LiveProductSnapshot;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.coflnet.BazaarHistoryPoint;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable presentation state for the unified Bazaar Item Info screen. */
public final class BazaarItemInfoViewData {
    private BazaarItemInfoViewData() {}

    public record ScreenState(
        ProductIdentity product,
        String itemTag,
        InitialMode activeMode,
        LiveProductSnapshot live,
        Map<BazaarItemInfoRange, HistoryRangeState> history,
        BazaarItemInfoRange selectedRange,
        LoadState<SevenDayComparison.Result> comparison,
        Preferences preferences,
        boolean historyOpened,
        boolean manualRefreshActive
    ) {
        public ScreenState {
            product = Objects.requireNonNull(product, "product");
            itemTag = Objects.requireNonNull(itemTag, "itemTag");
            activeMode = Objects.requireNonNull(activeMode, "activeMode");
            live = Objects.requireNonNull(live, "live");
            history = Map.copyOf(new EnumMap<>(Objects.requireNonNull(history, "history")));
            selectedRange = Objects.requireNonNull(selectedRange, "selectedRange");
            comparison = Objects.requireNonNull(comparison, "comparison");
            preferences = Objects.requireNonNull(preferences, "preferences");
        }

        public HistoryRangeState selectedHistory() {
            return this.history.get(this.selectedRange);
        }

        public boolean refreshEnabled() {
            return this.historyOpened
                && !this.manualRefreshActive
                && !this.selectedHistory().data().isLoading()
                && (this.selectedRange == BazaarItemInfoRange.Week
                    || !this.history.get(BazaarItemInfoRange.Week).data().isLoading());
        }
    }

    public record Preferences(
        boolean showBuy,
        boolean showSell,
        boolean showBands,
        ActivityMode activityMode,
        int visibleOrderBookRows,
        NumberStyle volumeNumberStyle,
        boolean showPerLevelOrderCount,
        boolean showCumulativeVolume,
        boolean showBazaarEntry
    ) {
        public Preferences {
            activityMode = Objects.requireNonNull(activityMode, "activityMode");
            volumeNumberStyle = Objects.requireNonNull(volumeNumberStyle, "volumeNumberStyle");
        }

        public static Preferences from(BazaarItemInfoConfig config) {
            return new Preferences(
                config.showBuy,
                config.showSell,
                config.showBands,
                config.activityMode,
                config.visibleOrderBookRows,
                config.volumeNumberStyle,
                config.showPerLevelOrderCount,
                config.showCumulativeVolume,
                config.showBazaarEntry);
        }
    }

    public record HistoryRangeState(LoadState<History> data, Optional<Instant> checkedAt) {
        public HistoryRangeState {
            data = Objects.requireNonNull(data, "data");
            checkedAt = Objects.requireNonNull(checkedAt, "checkedAt");
        }

        public static HistoryRangeState notRequested() {
            return new HistoryRangeState(new NotRequested<>(), Optional.empty());
        }
    }

    /** Chronological, immutable history prepared once before chart layout. */
    public record History(List<BazaarHistoryPoint> points) {
        public History {
            Objects.requireNonNull(points, "points");
            points = points.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(BazaarHistoryPoint::timestamp))
                .toList();
        }
    }

    public sealed interface LoadState<T> permits NotRequested, Loading, Empty, Success, Failure {
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

    public record NotRequested<T>() implements LoadState<T> {}

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
