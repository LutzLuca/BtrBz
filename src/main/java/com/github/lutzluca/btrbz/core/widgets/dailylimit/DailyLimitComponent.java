package com.github.lutzluca.btrbz.core.widgets.dailylimit;

import com.github.lutzluca.btrbz.core.config.ConfigManager;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Durable UTC daily accounting, deliberately independent from widget enablement. */
public final class DailyLimitComponent {
    private final Supplier<DailyLimitWidgetConfig> configSupplier;
    private final Runnable saveAction;
    private final LongSupplier utcEpochDay;

    public DailyLimitComponent() {
        this(
            () -> ConfigManager.get().widgets.orderLimit,
            ConfigManager::save,
            () -> LocalDate.now(ZoneOffset.UTC).toEpochDay()
        );
    }

    DailyLimitComponent(
        Supplier<DailyLimitWidgetConfig> configSupplier,
        Runnable saveAction,
        LongSupplier utcEpochDay
    ) {
        this.configSupplier = Objects.requireNonNull(configSupplier, "configSupplier");
        this.saveAction = Objects.requireNonNull(saveAction, "saveAction");
        this.utcEpochDay = Objects.requireNonNull(utcEpochDay, "utcEpochDay");
        this.resetForCurrentUtcDay();
    }

    public void onTransaction(double amount) {
        this.resetForCurrentUtcDay();
        if (!Double.isFinite(amount) || amount <= 0) return;
        this.config().usedToday += amount;
        this.saveAction.run();
    }

    public Usage currentUsage() {
        this.resetForCurrentUtcDay();
        var config = this.config();
        return new Usage(config.usedToday, config.dailyLimit, config.lastResetEpochDay);
    }

    public boolean resetForCurrentUtcDay() {
        return this.resetForDay(this.utcEpochDay.getAsLong());
    }

    public boolean resetForDay(long epochDay) {
        var config = this.config();
        boolean changed = resetForDay(config, epochDay);
        if (changed) this.saveAction.run();
        return changed;
    }

    private DailyLimitWidgetConfig config() {
        return this.configSupplier.get();
    }

    public static boolean resetForDay(DailyLimitWidgetConfig config, long epochDay) {
        if (config.lastResetEpochDay == epochDay) return false;
        config.usedToday = 0;
        config.lastResetEpochDay = epochDay;
        return true;
    }

    public record Usage(double used, double limit, long lastResetEpochDay) {}
}
