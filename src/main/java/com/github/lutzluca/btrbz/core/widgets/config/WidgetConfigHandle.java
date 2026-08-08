package com.github.lutzluca.btrbz.core.widgets.config;

import com.github.lutzluca.btrbz.core.widgets.WidgetId;
import com.github.lutzluca.btrbz.core.widgets.cache.CacheToken;
import com.github.lutzluca.btrbz.core.widgets.cache.InvalidationReason;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Owns one widget's persisted content configuration and its change token. */
public final class WidgetConfigHandle<C> {
    private final WidgetId id;
    private final Supplier<C> currentConfig;
    private final Supplier<C> freshDefaults;
    private final Function<C, WidgetFrameConfig> frameConfig;
    private final WidgetPreferenceReset<C> preferenceReset;
    private final CacheToken contentChanges;

    public WidgetConfigHandle(
        WidgetId id,
        Supplier<C> currentConfig,
        Supplier<C> freshDefaults,
        Function<C, WidgetFrameConfig> frameConfig,
        WidgetPreferenceReset<C> preferenceReset
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.currentConfig = Objects.requireNonNull(currentConfig, "currentConfig");
        this.freshDefaults = Objects.requireNonNull(freshDefaults, "freshDefaults");
        this.frameConfig = Objects.requireNonNull(frameConfig, "frameConfig");
        this.preferenceReset = Objects.requireNonNull(preferenceReset, "preferenceReset");
        this.contentChanges = CacheToken.named("config.widget." + id);
    }

    public WidgetId id() {
        return this.id;
    }

    public C current() {
        return Objects.requireNonNull(this.currentConfig.get(), "current widget config");
    }

    public C defaults() {
        return Objects.requireNonNull(this.freshDefaults.get(), "fresh widget defaults");
    }

    public WidgetFrameConfig frame() {
        return this.frameConfig.apply(this.current());
    }

    public WidgetFrameConfig defaultFrame() {
        return this.frameConfig.apply(this.defaults());
    }

    public CacheToken contentChanges() {
        return this.contentChanges;
    }

    public void mutate(String reason, Consumer<C> mutation) {
        Objects.requireNonNull(mutation, "mutation").accept(this.current());
        this.invalidate(reason);
    }

    public void invalidate(String reason) {
        this.contentChanges.invalidate(InvalidationReason.of(reason));
    }

    public void resetPreferences(String reason) {
        this.preferenceReset.reset(this.current(), this.defaults());
        this.contentChanges.invalidate(InvalidationReason.of(reason));
    }

    Function<C, WidgetFrameConfig> frameConfig() {
        return this.frameConfig;
    }

    WidgetPreferenceReset<C> preferenceReset() {
        return this.preferenceReset;
    }
}
