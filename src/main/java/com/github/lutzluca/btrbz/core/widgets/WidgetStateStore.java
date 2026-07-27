package com.github.lutzluca.btrbz.core.widgets;

import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.widgets.config.WidgetFrameConfig;
import com.github.lutzluca.btrbz.core.widgets.config.WidgetsConfig;
import java.util.Objects;
import java.util.function.Supplier;

/** Shared root-scale persistence plus generic access to definition-owned frame config. */
public final class WidgetStateStore {
    public static final double MIN_SCALE = 0.5;
    public static final double MAX_SCALE = 2.0;
    private final Supplier<WidgetsConfig> configSupplier;
    private final Runnable saveAction;

    public WidgetStateStore() { this(() -> ConfigManager.get().widgets, ConfigManager::save); }

    WidgetStateStore(Supplier<WidgetsConfig> configSupplier, Runnable saveAction) {
        this.configSupplier = Objects.requireNonNull(configSupplier, "configSupplier");
        this.saveAction = Objects.requireNonNull(saveAction, "saveAction");
    }

    public double globalFineTuneScale() { return clampScale(this.config().globalFineTuneScale); }
    public void setGlobalFineTuneScale(double value) {
        this.setGlobalFineTuneScale(value, true);
    }
    public void setGlobalFineTuneScale(double value, boolean persist) {
        this.config().globalFineTuneScale = clampScale(value);
        if (persist) this.saveAction.run();
    }
    public WidgetPlacement placement(WidgetDefinition<?, ?, ?> definition, String profile) {
        var frame = definition.frame();
        return frame.placements.getOrDefault(profile, frame.placements.getOrDefault(
            "default", definition.defaultFrame().placements.get("default")
        ));
    }
    public boolean isActive(WidgetDefinition<?, ?, ?> definition) { return definition.frame().enabled; }
    public void setActive(WidgetDefinition<?, ?, ?> definition, boolean active) {
        this.setActive(definition, active, true);
    }
    public void setActive(WidgetDefinition<?, ?, ?> definition, boolean active, boolean persist) {
        definition.frame().enabled = active;
        if (persist) this.saveAction.run();
    }
    public void setPlacement(
        WidgetDefinition<?, ?, ?> definition,
        String profile,
        WidgetPlacement placement,
        boolean persist
    ) {
        definition.frame().placements.put(profile, placement);
        if (persist) this.saveAction.run();
    }
    public void resetPlacement(WidgetDefinition<?, ?, ?> definition, String profile) {
        this.resetPlacement(definition, profile, true);
    }
    public void resetPlacement(WidgetDefinition<?, ?, ?> definition, String profile, boolean persist) {
        var fallback = definition.defaultFrame().placements.getOrDefault(
            profile, definition.defaultFrame().placements.get("default")
        );
        this.setPlacement(definition, profile, fallback, persist);
    }
    public double widgetScale(WidgetDefinition<?, ?, ?> definition) {
        return clampScale(definition.frame().scale);
    }
    public void setWidgetScale(WidgetDefinition<?, ?, ?> definition, double value) {
        this.setWidgetScale(definition, value, true);
    }
    public void setWidgetScale(WidgetDefinition<?, ?, ?> definition, double value, boolean persist) {
        definition.frame().scale = clampScale(value);
        if (persist) this.saveAction.run();
    }
    public void resetWidgetScale(WidgetDefinition<?, ?, ?> definition) {
        this.setWidgetScale(definition, definition.defaultFrame().scale, true);
    }
    public void resetWidgetScale(WidgetDefinition<?, ?, ?> definition, boolean persist) {
        this.setWidgetScale(definition, definition.defaultFrame().scale, persist);
    }
    public double requestedScale(WidgetDefinition<?, ?, ?> definition) {
        return WidgetScaleResolver.combineRequestedScale(this.globalFineTuneScale(), this.widgetScale(definition));
    }
    public int backgroundColor(WidgetDefinition<?, ?, ?> definition, int fallback) {
        var background = definition.frame().background;
        return background == null ? fallback : background;
    }
    public void setBackgroundColor(WidgetDefinition<?, ?, ?> definition, int color) {
        this.setBackgroundColor(definition, color, true);
    }
    public void setBackgroundColor(WidgetDefinition<?, ?, ?> definition, int color, boolean persist) {
        definition.frame().background = color;
        if (persist) this.saveAction.run();
    }
    public void resetBackgroundColor(WidgetDefinition<?, ?, ?> definition) {
        this.resetBackgroundColor(definition, true);
    }
    public void resetBackgroundColor(WidgetDefinition<?, ?, ?> definition, boolean persist) {
        definition.frame().background = definition.defaultFrame().background;
        if (persist) this.saveAction.run();
    }
    public void save() { this.saveAction.run(); }

    private WidgetsConfig config() { return this.configSupplier.get(); }
    private static double clampScale(double value) {
        if (!Double.isFinite(value)) return 1.0;
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, value));
    }
}
