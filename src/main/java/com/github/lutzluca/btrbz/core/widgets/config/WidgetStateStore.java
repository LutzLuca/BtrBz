package com.github.lutzluca.btrbz.core.widgets.config;

import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.widgets.WidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.layout.WidgetPlacement;
import com.github.lutzluca.btrbz.core.widgets.layout.WidgetScaleResolver;
import java.util.Objects;
import java.util.function.Supplier;

/** Shared root-scale persistence plus generic access to definition-owned frame config. */
public final class WidgetStateStore {
    private final Supplier<WidgetsConfig> configSupplier;
    private final Runnable saveAction;

    public WidgetStateStore() { this(() -> ConfigManager.get().widgets, ConfigManager::save); }

    public WidgetStateStore(Supplier<WidgetsConfig> configSupplier, Runnable saveAction) {
        this.configSupplier = Objects.requireNonNull(configSupplier, "configSupplier");
        this.saveAction = Objects.requireNonNull(saveAction, "saveAction");
    }

    public double globalFineTuneScale() { return WidgetScaleResolver.clampScale(this.config().globalFineTuneScale); }
    public void setGlobalFineTuneScale(double value) {
        this.setGlobalFineTuneScale(value, true);
    }
    public void setGlobalFineTuneScale(double value, boolean persist) {
        this.config().globalFineTuneScale = WidgetScaleResolver.clampScale(value);
        if (persist) this.saveAction.run();
    }
    public int globalBackgroundColor() { return this.config().globalBackground; }
    public void setGlobalBackgroundColor(int color) {
        this.setGlobalBackgroundColor(color, true);
    }
    public void setGlobalBackgroundColor(int color, boolean persist) {
        this.config().globalBackground = color;
        if (persist) this.saveAction.run();
    }
    public int managerPanelWidth() {
        return this.config().managerPanelWidth;
    }
    public void setManagerPanelWidth(int value, boolean persist) {
        this.config().managerPanelWidth = value;
        if (persist) this.saveAction.run();
    }
    public int managerPanelHeightPercent() {
        return this.config().managerPanelHeightPercent;
    }
    public void setManagerPanelHeightPercent(int value, boolean persist) {
        this.config().managerPanelHeightPercent = value;
        if (persist) this.saveAction.run();
    }
    public boolean runtimeDragging() { return this.config().runtimeDragging; }
    public void setRuntimeDragging(boolean enabled, boolean persist) {
        this.config().runtimeDragging = enabled;
        if (persist) this.saveAction.run();
    }
    public boolean managerLauncherVisible() { return this.config().managerLauncherVisible; }
    public void setManagerLauncherVisible(boolean visible, boolean persist) {
        this.config().managerLauncherVisible = visible;
        if (persist) this.saveAction.run();
    }
    public WidgetPlacement managerLauncherPosition() {
        var placement = this.config().managerLauncherPosition;
        return placement == null ? WidgetsConfig.DEFAULT_MANAGER_LAUNCHER_POSITION : placement;
    }
    public void setManagerLauncherPosition(WidgetPlacement placement, boolean persist) {
        this.config().managerLauncherPosition = Objects.requireNonNull(placement, "placement");
        if (persist) this.saveAction.run();
    }
    public void resetManagerLauncherPosition(boolean persist) {
        this.setManagerLauncherPosition(WidgetsConfig.DEFAULT_MANAGER_LAUNCHER_POSITION, persist);
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
        return WidgetScaleResolver.clampScale(definition.frame().scale);
    }
    public boolean hasWidgetScaleOverride(WidgetDefinition<?, ?, ?> definition) {
        return definition.frame().overrideScale;
    }
    public void setWidgetScaleOverride(WidgetDefinition<?, ?, ?> definition, boolean enabled) {
        this.setWidgetScaleOverride(definition, enabled, true);
    }
    public void setWidgetScaleOverride(
        WidgetDefinition<?, ?, ?> definition,
        boolean enabled,
        boolean persist
    ) {
        definition.frame().overrideScale = enabled;
        if (persist) this.saveAction.run();
    }
    public void setWidgetScale(WidgetDefinition<?, ?, ?> definition, double value) {
        this.setWidgetScale(definition, value, true);
    }
    public void setWidgetScale(WidgetDefinition<?, ?, ?> definition, double value, boolean persist) {
        definition.frame().scale = WidgetScaleResolver.clampScale(value);
        if (persist) this.saveAction.run();
    }
    public void resetWidgetScale(WidgetDefinition<?, ?, ?> definition) {
        this.resetWidgetScale(definition, true);
    }
    public void resetWidgetScale(WidgetDefinition<?, ?, ?> definition, boolean persist) {
        var frame = definition.frame();
        var defaults = definition.defaultFrame();
        frame.overrideScale = defaults.overrideScale;
        frame.scale = defaults.scale;
        if (persist) this.saveAction.run();
    }
    public double requestedScale(WidgetDefinition<?, ?, ?> definition) {
        double base = this.hasWidgetScaleOverride(definition)
            ? this.widgetScale(definition)
            : this.globalFineTuneScale();

        return base * WidgetScaleResolver.automaticGuiScale();
    }
    public boolean hasBackgroundOverride(WidgetDefinition<?, ?, ?> definition) {
        return definition.frame().overrideBackground;
    }
    public void setBackgroundOverride(WidgetDefinition<?, ?, ?> definition, boolean enabled) {
        this.setBackgroundOverride(definition, enabled, true);
    }
    public void setBackgroundOverride(
        WidgetDefinition<?, ?, ?> definition,
        boolean enabled,
        boolean persist
    ) {
        definition.frame().overrideBackground = enabled;
        if (persist) this.saveAction.run();
    }
    public int backgroundColor(WidgetDefinition<?, ?, ?> definition) {
        return this.hasBackgroundOverride(definition)
            ? definition.frame().background
            : this.globalBackgroundColor();
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
        var frame = definition.frame();
        var defaults = definition.defaultFrame();
        frame.overrideBackground = defaults.overrideBackground;
        frame.background = defaults.background;
        if (persist) this.saveAction.run();
    }
    public void save() { this.saveAction.run(); }

    private WidgetsConfig config() { return this.configSupplier.get(); }
}
