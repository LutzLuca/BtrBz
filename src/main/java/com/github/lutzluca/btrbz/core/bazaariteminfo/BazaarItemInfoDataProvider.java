package com.github.lutzluca.btrbz.core.bazaariteminfo;

import com.github.lutzluca.btrbz.core.bazaariteminfo.BazaarItemInfoViewData.Empty;
import com.github.lutzluca.btrbz.core.bazaariteminfo.BazaarItemInfoViewData.Failure;
import com.github.lutzluca.btrbz.core.bazaariteminfo.BazaarItemInfoViewData.History;
import com.github.lutzluca.btrbz.core.bazaariteminfo.BazaarItemInfoViewData.HistoryRangeState;
import com.github.lutzluca.btrbz.core.bazaariteminfo.BazaarItemInfoViewData.LoadState;
import com.github.lutzluca.btrbz.core.bazaariteminfo.BazaarItemInfoViewData.Loading;
import com.github.lutzluca.btrbz.core.bazaariteminfo.BazaarItemInfoViewData.NotRequested;
import com.github.lutzluca.btrbz.core.bazaariteminfo.BazaarItemInfoViewData.Preferences;
import com.github.lutzluca.btrbz.core.bazaariteminfo.BazaarItemInfoViewData.ScreenState;
import com.github.lutzluca.btrbz.core.bazaariteminfo.BazaarItemInfoViewData.Success;
import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.BazaarData.MarketSnapshot;
import com.github.lutzluca.btrbz.data.LiveProductSnapshot;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.coflnet.BazaarHistoryPoint;
import com.github.lutzluca.coflnet.CoflnetBazaarClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Owns live Hypixel state, lazy Coflnet range requests, and persisted screen preferences. */
public final class BazaarItemInfoDataProvider implements AutoCloseable {
    private static final Duration WEEK_STALE_AFTER = Duration.ofMinutes(60);

    private final BazaarData bazaarData;
    private final CoflnetBazaarClient client;
    private final Clock clock;
    private final Executor uiExecutor;
    private final Consumer<ScreenState> stateListener;
    private final PreferenceStore preferenceStore;
    private final Consumer<MarketSnapshot> marketListener = this::onMarketUpdate;
    private final EnumMap<BazaarItemInfoRange, Long> generations = new EnumMap<>(BazaarItemInfoRange.class);
    private final Set<BazaarItemInfoRange> activeRanges = new HashSet<>();
    private final Set<BazaarItemInfoRange> manualRanges = new HashSet<>();

    private ScreenState state;
    private boolean disposed;

    public BazaarItemInfoDataProvider(
        BazaarData bazaarData,
        CoflnetBazaarClient client,
        ProductIdentity product,
        String itemTag,
        InitialMode initialMode,
        Executor uiExecutor,
        Consumer<ScreenState> stateListener
    ) {
        this(
            bazaarData, client, product, itemTag, initialMode, Clock.systemUTC(), uiExecutor,
            PreferenceStore.global(), stateListener);
    }

    BazaarItemInfoDataProvider(
        BazaarData bazaarData,
        CoflnetBazaarClient client,
        ProductIdentity product,
        String itemTag,
        InitialMode initialMode,
        Clock clock,
        Executor uiExecutor,
        BazaarItemInfoConfig config,
        Consumer<ScreenState> stateListener
    ) {
        this(
            bazaarData, client, product, itemTag, initialMode, clock, uiExecutor,
            PreferenceStore.local(config), stateListener);
    }

    private BazaarItemInfoDataProvider(
        BazaarData bazaarData,
        CoflnetBazaarClient client,
        ProductIdentity product,
        String itemTag,
        InitialMode initialMode,
        Clock clock,
        Executor uiExecutor,
        PreferenceStore preferenceStore,
        Consumer<ScreenState> stateListener
    ) {
        this.bazaarData = Objects.requireNonNull(bazaarData, "bazaarData");
        this.client = Objects.requireNonNull(client, "client");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.uiExecutor = Objects.requireNonNull(uiExecutor, "uiExecutor");
        this.preferenceStore = Objects.requireNonNull(preferenceStore, "preferenceStore");
        this.stateListener = Objects.requireNonNull(stateListener, "stateListener");
        String normalizedTag = Objects.requireNonNull(itemTag, "itemTag").trim();
        if (normalizedTag.isEmpty()) {
            throw new IllegalArgumentException("itemTag must not be blank");
        }
        for (var range : BazaarItemInfoRange.values()) {
            this.generations.put(range, 0L);
        }
        var histories = new EnumMap<BazaarItemInfoRange, HistoryRangeState>(BazaarItemInfoRange.class);
        for (var range : BazaarItemInfoRange.values()) {
            histories.put(range, HistoryRangeState.notRequested());
        }
        var config = preferenceStore.config();
        var selected = config.selectedRange == null ? BazaarItemInfoRange.Day : config.selectedRange;
        this.state = new ScreenState(
            Objects.requireNonNull(product, "product"), normalizedTag,
            Objects.requireNonNull(initialMode, "initialMode"), bazaarData.liveProductSnapshot(product),
            histories, selected, new NotRequested<>(), Preferences.from(config), false, false);
        this.bazaarData.addListener(this.marketListener);
        if (initialMode == InitialMode.History) {
            this.enterHistory();
        }
    }

    public synchronized ScreenState state() {
        return this.state;
    }

    public void setMode(InitialMode mode) {
        Objects.requireNonNull(mode, "mode");
        this.update(current -> copy(current, mode, current.history(), current.selectedRange(),
            current.comparison(), current.preferences(), current.historyOpened()));
        if (mode == InitialMode.History) {
            this.enterHistory();
        }
    }

    public void enterHistory() {
        boolean firstEntry;
        BazaarItemInfoRange selected;
        synchronized (this) {
            if (this.disposed) {
                return;
            }
            firstEntry = !this.state.historyOpened();
            selected = this.state.selectedRange();
            if (firstEntry) {
                this.state = copy(this.state, this.state.activeMode(), this.state.history(), selected,
                    this.state.comparison(), this.state.preferences(), true);
            }
        }
        if (!firstEntry) {
            return;
        }
        this.notifyState();
        this.request(selected, false);
        if (selected != BazaarItemInfoRange.Week) {
            this.request(BazaarItemInfoRange.Week, false);
        }
    }

    public void selectRange(BazaarItemInfoRange range) {
        Objects.requireNonNull(range, "range");
        this.preferenceStore.update(config -> {
            if (config.selectedRange == range) {
                return false;
            }
            config.selectedRange = range;
            return true;
        });
        boolean shouldRequest;
        synchronized (this) {
            if (this.disposed || this.state.selectedRange() == range) {
                return;
            }
            this.state = copy(this.state, this.state.activeMode(), this.state.history(), range,
                this.state.comparison(), Preferences.from(this.preferenceStore.config()), this.state.historyOpened());
            shouldRequest = this.state.historyOpened();
        }
        this.notifyState();
        if (shouldRequest) {
            this.request(range, false);
        }
    }

    public void refresh() {
        ScreenState current = this.state();
        if (!current.refreshEnabled()) {
            return;
        }
        var selected = current.selectedRange();
        this.request(selected, true);
        if (selected != BazaarItemInfoRange.Week && this.weekNeedsRefresh(current)) {
            this.request(BazaarItemInfoRange.Week, true);
        }
    }

    public void showBuy(boolean value) {
        this.updatePreference(config -> setBoolean(config.showBuy, value, next -> config.showBuy = next));
    }

    public void showSell(boolean value) {
        this.updatePreference(config -> setBoolean(config.showSell, value, next -> config.showSell = next));
    }

    public void showBands(boolean value) {
        this.updatePreference(config -> setBoolean(config.showBands, value, next -> config.showBands = next));
    }

    public void showBuyAndSell() {
        this.updatePreference(config -> {
            boolean changed = !config.showBuy || !config.showSell;
            config.showBuy = true;
            config.showSell = true;
            return changed;
        });
    }

    public void activityMode(BazaarItemInfoConfig.ActivityMode value) {
        Objects.requireNonNull(value, "value");
        this.updatePreference(config -> {
            if (config.activityMode == value) {
                return false;
            }
            config.activityMode = value;
            return true;
        });
    }

    private void updatePreference(Predicate<BazaarItemInfoConfig> change) {
        if (!this.preferenceStore.update(change)) {
            return;
        }
        this.update(current -> copy(current, current.activeMode(), current.history(), current.selectedRange(),
            current.comparison(), Preferences.from(this.preferenceStore.config()), current.historyOpened()));
    }

    private void request(BazaarItemInfoRange range, boolean manual) {
        final long generation;
        final String itemTag;
        synchronized (this) {
            if (this.disposed || this.activeRanges.contains(range)) {
                return;
            }
            generation = this.generations.compute(range, (_, value) -> value + 1);
            itemTag = this.state.itemTag();
            this.activeRanges.add(range);
            if (manual) {
                this.manualRanges.add(range);
            }
            var histories = histories(this.state.history());
            var previous = histories.get(range);
            histories.put(range, new HistoryRangeState(
                new Loading<>(previous.data().retainedValue()), previous.checkedAt()));
            this.state = copy(this.state, this.state.activeMode(), histories, this.state.selectedRange(),
                comparison(histories.get(BazaarItemInfoRange.Week), this.state.live()),
                this.state.preferences(), this.state.historyOpened());
        }
        this.notifyState();

        CompletionStage<List<BazaarHistoryPoint>> stage;
        try {
            stage = manual
                ? this.client.refreshHistory(itemTag, range.sdkRange())
                : this.client.history(itemTag, range.sdkRange());
        } catch (RuntimeException failure) {
            this.uiExecutor.execute(() -> this.complete(range, generation, null, failure));
            return;
        }
        stage.whenComplete(
            (points, failure) -> this.uiExecutor.execute(() -> this.complete(range, generation, points, failure)));
    }

    private void complete(
        BazaarItemInfoRange range,
        long generation,
        List<BazaarHistoryPoint> points,
        Throwable failure
    ) {
        synchronized (this) {
            if (this.disposed || generation != this.generations.get(range)) {
                return;
            }
            this.activeRanges.remove(range);
            this.manualRanges.remove(range);
            var histories = histories(this.state.history());
            var previous = histories.get(range);
            LoadState<History> next;
            Optional<Instant> checkedAt = previous.checkedAt();
            if (failure != null) {
                next = new Failure<>(errorMessage(failure), previous.data().retainedValue());
            } else {
                var history = new History(points == null ? List.of() : points);
                next = history.points().isEmpty() ? new Empty<>() : new Success<>(history);
                checkedAt = Optional.of(this.clock.instant());
            }
            histories.put(range, new HistoryRangeState(next, checkedAt));
            this.state = copy(this.state, this.state.activeMode(), histories, this.state.selectedRange(),
                comparison(histories.get(BazaarItemInfoRange.Week), this.state.live()),
                this.state.preferences(), this.state.historyOpened());
        }
        this.notifyState();
    }

    private void onMarketUpdate(MarketSnapshot snapshot) {
        this.uiExecutor.execute(() -> this.update(current -> {
            var live = snapshot.liveProductSnapshot(current.product());
            return new ScreenState(
                current.product(), current.itemTag(), current.activeMode(), live, current.history(),
                current.selectedRange(), comparison(current.history().get(BazaarItemInfoRange.Week), live),
                current.preferences(), current.historyOpened(), !this.manualRanges.isEmpty());
        }));
    }

    private static LoadState<SevenDayComparison.Result> comparison(
        HistoryRangeState week,
        LiveProductSnapshot live
    ) {
        var retained = week.data().retainedValue().map(value -> SevenDayComparison.calculate(value.points(), live));
        if (week.data() instanceof NotRequested<?>) {
            return new NotRequested<>();
        }
        if (week.data() instanceof Loading<?>) {
            return new Loading<>(retained);
        }
        if (week.data() instanceof Failure<?> failure) {
            return new Failure<>(failure.message(), retained);
        }
        if (week.data() instanceof Empty<?>) {
            return new Empty<>();
        }
        return new Success<>(retained.orElseThrow());
    }

    private boolean weekNeedsRefresh(ScreenState current) {
        var week = current.history().get(BazaarItemInfoRange.Week);
        if (week.data().retainedValue().isEmpty() || week.checkedAt().isEmpty()) {
            return true;
        }
        return Duration.between(week.checkedAt().orElseThrow(), this.clock.instant())
            .compareTo(WEEK_STALE_AFTER) >= 0;
    }

    private void update(java.util.function.UnaryOperator<ScreenState> operation) {
        synchronized (this) {
            if (this.disposed) {
                return;
            }
            this.state = operation.apply(this.state);
        }
        this.notifyState();
    }

    private void notifyState() {
        ScreenState snapshot;
        synchronized (this) {
            if (this.disposed) {
                return;
            }
            snapshot = this.state;
        }
        this.uiExecutor.execute(() -> {
            synchronized (this) {
                if (this.disposed) {
                    return;
                }
            }
            this.stateListener.accept(snapshot);
        });
    }

    @Override
    public void close() {
        synchronized (this) {
            if (this.disposed) {
                return;
            }
            this.disposed = true;
            this.activeRanges.clear();
            this.manualRanges.clear();
        }
        this.bazaarData.removeListener(this.marketListener);
    }

    public void dispose() {
        this.close();
    }

    static String errorMessage(Throwable error) {
        Throwable current = Objects.requireNonNull(error, "error");
        while ((current instanceof CompletionException || current instanceof ExecutionException)
            && current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private ScreenState copy(
        ScreenState current,
        InitialMode mode,
        Map<BazaarItemInfoRange, HistoryRangeState> history,
        BazaarItemInfoRange selectedRange,
        LoadState<SevenDayComparison.Result> comparison,
        Preferences preferences,
        boolean historyOpened
    ) {
        return new ScreenState(
            current.product(), current.itemTag(), mode, current.live(), history, selectedRange,
            comparison, preferences, historyOpened, !this.manualRanges.isEmpty());
    }

    private static EnumMap<BazaarItemInfoRange, HistoryRangeState> histories(
        Map<BazaarItemInfoRange, HistoryRangeState> source
    ) {
        var copy = new EnumMap<BazaarItemInfoRange, HistoryRangeState>(BazaarItemInfoRange.class);
        copy.putAll(source);
        return copy;
    }

    private static boolean setBoolean(boolean current, boolean value, Consumer<Boolean> setter) {
        if (current == value) {
            return false;
        }
        setter.accept(value);
        return true;
    }

    private interface PreferenceStore {
        BazaarItemInfoConfig config();

        boolean update(Predicate<BazaarItemInfoConfig> updater);

        static PreferenceStore global() {
            return new PreferenceStore() {
                @Override
                public BazaarItemInfoConfig config() {
                    return ConfigManager.get().bazaarItemInfo;
                }

                @Override
                public boolean update(Predicate<BazaarItemInfoConfig> updater) {
                    return ConfigManager.updateIfChanged(config -> updater.test(config.bazaarItemInfo));
                }
            };
        }

        static PreferenceStore local(BazaarItemInfoConfig config) {
            Objects.requireNonNull(config, "config");
            return new PreferenceStore() {
                @Override
                public BazaarItemInfoConfig config() {
                    return config;
                }

                @Override
                public boolean update(Predicate<BazaarItemInfoConfig> updater) {
                    return updater.test(config);
                }
            };
        }
    }
}
