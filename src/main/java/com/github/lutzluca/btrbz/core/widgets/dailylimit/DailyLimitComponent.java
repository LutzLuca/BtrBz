package com.github.lutzluca.btrbz.core.widgets.dailylimit;

import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.widgets.cache.CacheToken;
import com.github.lutzluca.btrbz.core.widgets.cache.InvalidationReason;
import com.github.lutzluca.btrbz.core.widgets.cache.UtcDayTracker;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Durable UTC daily accounting, deliberately independent from widget enablement. */
public final class DailyLimitComponent {
    private final Supplier<DailyLimitWidgetConfig> configSupplier;
    private final Runnable saveAction;
    private final UtcDayTracker utcDayTracker;
    private final CacheToken dataChanges = CacheToken.named("daily-limit.data");

    public DailyLimitComponent() {
        this(defaultTracker());
    }

    public DailyLimitComponent(UtcDayTracker utcDayTracker) {
        this(() -> ConfigManager.get().widgets.orderLimit, ConfigManager::save, utcDayTracker);
    }

    DailyLimitComponent(
        Supplier<DailyLimitWidgetConfig> configSupplier,
        Runnable saveAction,
        LongSupplier utcEpochDay
    ) {
        this(configSupplier, saveAction, initializedTracker(utcEpochDay));
    }

    DailyLimitComponent(
        Supplier<DailyLimitWidgetConfig> configSupplier,
        Runnable saveAction,
        UtcDayTracker utcDayTracker
    ) {
        this.configSupplier = Objects.requireNonNull(configSupplier, "configSupplier");
        this.saveAction = Objects.requireNonNull(saveAction, "saveAction");
        this.utcDayTracker = Objects.requireNonNull(utcDayTracker, "utcDayTracker");
        this.resetForCurrentUtcDay();
    }

    public void onTransaction(double amount) {
        this.resetForCurrentUtcDay();

        if (!Double.isFinite(amount) || amount <= 0) {
            return;
        }

        this.config().usedToday += amount;
        this.dataChanges.invalidate(InvalidationReason.of("daily Bazaar usage changed"));
        this.saveAction.run();
    }

    public Usage currentUsage() {
        this.resetForCurrentUtcDay();
        var config = this.config();

        return new Usage(config.usedToday, config.dailyLimit, config.lastResetEpochDay);
    }

    public boolean resetForCurrentUtcDay() {
        return this.resetForDay(this.utcDayTracker.currentDay());
    }

    public boolean resetForDay(long epochDay) {
        var config = this.config();
        boolean changed = resetForDay(config, epochDay);

        if (changed) {
            this.dataChanges.invalidate(InvalidationReason.of("daily Bazaar usage reset"));
            this.saveAction.run();
        }

        return changed;
    }

    public CacheToken dataChanges() {
        return this.dataChanges;
    }

    public UtcDayTracker utcDayTracker() {
        return this.utcDayTracker;
    }

    private DailyLimitWidgetConfig config() {
        return this.configSupplier.get();
    }

    public static boolean resetForDay(DailyLimitWidgetConfig config, long epochDay) {
        if (config.lastResetEpochDay == epochDay) {
            return false;
        }

        config.usedToday = 0;
        config.lastResetEpochDay = epochDay;

        return true;
    }

    public record Usage(double used, double limit, long lastResetEpochDay) {}

    private static UtcDayTracker defaultTracker() {
        return initializedTracker(() -> LocalDate.now(ZoneOffset.UTC).toEpochDay());
    }

    private static UtcDayTracker initializedTracker(LongSupplier supplier) {
        var tracker = new UtcDayTracker(supplier);
        tracker.initialize();

        return tracker;
    }
}
