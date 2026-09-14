package com.github.lutzluca.btrbz.core.widgets.dailylimit;

import com.github.lutzluca.btrbz.cache.CacheToken;
import com.github.lutzluca.btrbz.core.widgets.cache.UtcDayTracker;

import java.util.Objects;
import java.util.function.Supplier;
import lombok.Getter;
import lombok.experimental.Accessors;

/** Durable UTC daily accounting, deliberately independent from widget enablement. */
public final class DailyLimitComponent {
    private final Supplier<DailyLimitWidgetConfig> configSupplier;
    private final Runnable saveAction;
    @Getter
    @Accessors(fluent = true)
    private final UtcDayTracker utcDayTracker;
    @Getter
    @Accessors(fluent = true)
    private final CacheToken dataChanges = CacheToken.named("daily-limit.data");

    public DailyLimitComponent(
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
        this.dataChanges.invalidate("daily Bazaar usage changed");
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
            this.dataChanges.invalidate("daily Bazaar usage reset");
            this.saveAction.run();
        }

        return changed;
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
}
