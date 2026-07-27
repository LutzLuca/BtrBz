package com.github.lutzluca.btrbz.core.widgets.config;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** A replacement-aware, manager-owned binding to one persisted widget config. */
public final class WidgetConfigBinding<C> {
    private final Supplier<C> currentConfig;
    private final Supplier<C> freshDefaults;
    private final Function<C, WidgetFrameConfig> frameConfig;
    private final WidgetPreferenceReset<C> preferenceReset;
    private final Runnable changed;

    public WidgetConfigBinding(
        Supplier<C> currentConfig,
        Supplier<C> freshDefaults,
        Function<C, WidgetFrameConfig> frameConfig,
        WidgetPreferenceReset<C> preferenceReset,
        Runnable changed
    ) {
        this.currentConfig = Objects.requireNonNull(currentConfig, "currentConfig");
        this.freshDefaults = Objects.requireNonNull(freshDefaults, "freshDefaults");
        this.frameConfig = Objects.requireNonNull(frameConfig, "frameConfig");
        this.preferenceReset = Objects.requireNonNull(preferenceReset, "preferenceReset");
        this.changed = Objects.requireNonNull(changed, "changed");
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

    public void mutate(Consumer<C> mutation) {
        Objects.requireNonNull(mutation, "mutation").accept(this.current());
        this.changed.run();
    }

    public void resetFrame() {
        copyFrame(this.defaultFrame(), this.frame());
        this.changed.run();
    }

    public void resetPreferences() {
        this.preferenceReset.reset(this.current(), this.defaults());
        this.changed.run();
    }

    public void resetAll() {
        var current = this.current();
        var defaults = this.defaults();
        copyFrame(this.frameConfig.apply(defaults), this.frameConfig.apply(current));
        this.preferenceReset.reset(current, defaults);
        this.changed.run();
    }

    public void markChanged() {
        this.changed.run();
    }

    public static void copyFrame(WidgetFrameConfig source, WidgetFrameConfig target) {
        target.enabled = source.enabled;
        target.placements.clear();
        target.placements.putAll(source.placements);
        target.scale = source.scale;
        target.background = source.background;
    }
}
