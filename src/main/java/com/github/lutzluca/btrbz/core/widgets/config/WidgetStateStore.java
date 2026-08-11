package com.github.lutzluca.btrbz.core.widgets.config;

import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.widgets.WidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.WidgetId;
import com.github.lutzluca.btrbz.core.widgets.cache.CacheToken;
import com.github.lutzluca.btrbz.core.widgets.cache.InvalidationReason;
import com.github.lutzluca.btrbz.core.widgets.layout.WidgetPlacement;
import com.github.lutzluca.btrbz.core.widgets.layout.WidgetScaleResolver;
import java.util.Objects;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Shared root-scale persistence plus generic access to definition-owned frame config. */
public final class WidgetStateStore {
    private final Supplier<WidgetsConfig> configSupplier;
    private final Runnable saveAction;
    private final Map<WidgetId, CacheToken> frameChanges = new HashMap<>();
    private final CacheToken globalFrameChanges = CacheToken.named("widget-frame.global");

    public WidgetStateStore() {
        this(() -> ConfigManager.get().widgets, ConfigManager::save);
    }

    public WidgetStateStore(Supplier<WidgetsConfig> configSupplier, Runnable saveAction) {
        this.configSupplier = Objects.requireNonNull(configSupplier, "configSupplier");
        this.saveAction = Objects.requireNonNull(saveAction, "saveAction");
    }

    public CacheToken frameChanges(WidgetId id) {
        return this.frameChanges.computeIfAbsent(
            Objects.requireNonNull(id, "id"),
            key -> CacheToken.named("widget-frame." + key)
        );
    }

    public CacheToken globalFrameChanges() {
        return this.globalFrameChanges;
    }

    public double globalFineTuneScale() {
        return WidgetScaleResolver.clampScale(this.config().globalFineTuneScale);
    }

    public void setGlobalFineTuneScale(double value) {
        this.setGlobalFineTuneScale(value, true);
    }

    public void setGlobalFineTuneScale(double value, boolean persist) {
        this.config().globalFineTuneScale = WidgetScaleResolver.clampScale(value);
        this.globalFrameChanges.invalidate(InvalidationReason.of("global widget scale changed"));

        if (persist) {
            this.saveAction.run();
        }
    }

    public int globalBackgroundColor() {
        return this.config().globalBackground;
    }

    public void setGlobalBackgroundColor(int color) {
        this.setGlobalBackgroundColor(color, true);
    }

    public void setGlobalBackgroundColor(int color, boolean persist) {
        this.config().globalBackground = color;
        this.globalFrameChanges.invalidate(InvalidationReason.of("global widget background changed"));

        if (persist) {
            this.saveAction.run();
        }
    }

    public int managerPanelWidth() {
        return this.config().managerPanelWidth;
    }

    public void setManagerPanelWidth(int value, boolean persist) {
        this.config().managerPanelWidth = value;

        if (persist) {
            this.saveAction.run();
        }
    }

    public int managerPanelHeightPercent() {
        return this.config().managerPanelHeightPercent;
    }

    public void setManagerPanelHeightPercent(int value, boolean persist) {
        this.config().managerPanelHeightPercent = value;

        if (persist) {
            this.saveAction.run();
        }
    }

    public boolean runtimeDragging() {
        return this.config().runtimeDragging;
    }

    public void setRuntimeDragging(boolean enabled, boolean persist) {
        this.config().runtimeDragging = enabled;

        if (persist) {
            this.saveAction.run();
        }
    }

    public boolean managerLauncherVisible() {
        return this.config().managerLauncherVisible;
    }

    public void setManagerLauncherVisible(boolean visible, boolean persist) {
        this.config().managerLauncherVisible = visible;

        if (persist) {
            this.saveAction.run();
        }
    }

    public WidgetPlacement managerLauncherPosition() {
        var placement = this.config().managerLauncherPosition;

        return placement == null ? WidgetsConfig.DEFAULT_MANAGER_LAUNCHER_POSITION : placement;
    }

    public void setManagerLauncherPosition(WidgetPlacement placement, boolean persist) {
        this.config().managerLauncherPosition = Objects.requireNonNull(placement, "placement");

        if (persist) {
            this.saveAction.run();
        }
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

    public boolean isActive(WidgetDefinition<?, ?, ?> definition) {
        return definition.frame().enabled;
    }

    public void setActive(WidgetDefinition<?, ?, ?> definition, boolean active) {
        this.setActive(definition, active, true);
    }

    public void setActive(WidgetDefinition<?, ?, ?> definition, boolean active, boolean persist) {
        definition.frame().enabled = active;
        this.invalidate(definition, "widget enablement changed");

        if (persist) {
            this.saveAction.run();
        }
    }

    public void setPlacement(
        WidgetDefinition<?, ?, ?> definition,
        String profile,
        WidgetPlacement placement,
        boolean persist
    ) {
        definition.frame().placements.put(profile, placement);
        this.invalidate(definition, "widget placement changed");

        if (persist) {
            this.saveAction.run();
        }
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
        this.invalidate(definition, "widget scale override changed");

        if (persist) {
            this.saveAction.run();
        }
    }

    public void setWidgetScale(WidgetDefinition<?, ?, ?> definition, double value) {
        this.setWidgetScale(definition, value, true);
    }

    public void setWidgetScale(WidgetDefinition<?, ?, ?> definition, double value, boolean persist) {
        definition.frame().scale = WidgetScaleResolver.clampScale(value);
        this.invalidate(definition, "widget scale changed");

        if (persist) {
            this.saveAction.run();
        }
    }

    public void resetWidgetScale(WidgetDefinition<?, ?, ?> definition) {
        this.resetWidgetScale(definition, true);
    }

    public void resetWidgetScale(WidgetDefinition<?, ?, ?> definition, boolean persist) {
        var frame = definition.frame();
        var defaults = definition.defaultFrame();

        frame.overrideScale = defaults.overrideScale;
        frame.scale = defaults.scale;
        this.invalidate(definition, "widget scale reset");

        if (persist) {
            this.saveAction.run();
        }
    }

    public double requestedScale(WidgetDefinition<?, ?, ?> definition) {
        double base = this.hasWidgetScaleOverride(definition)
            ? this.widgetScale(definition)
            : this.globalFineTuneScale();

        return base * WidgetScaleResolver.automaticGuiScale();
    }

    public double requestedGlobalScale() {
        return this.globalFineTuneScale() * WidgetScaleResolver.automaticGuiScale();
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
        this.invalidate(definition, "widget background override changed");

        if (persist) {
            this.saveAction.run();
        }
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
        this.invalidate(definition, "widget background changed");

        if (persist) {
            this.saveAction.run();
        }
    }

    public void resetBackgroundColor(WidgetDefinition<?, ?, ?> definition) {
        this.resetBackgroundColor(definition, true);
    }

    public void resetBackgroundColor(WidgetDefinition<?, ?, ?> definition, boolean persist) {
        var frame = definition.frame();
        var defaults = definition.defaultFrame();

        frame.overrideBackground = defaults.overrideBackground;
        frame.background = defaults.background;
        this.invalidate(definition, "widget background reset");

        if (persist) {
            this.saveAction.run();
        }
    }

    public void save() {
        this.saveAction.run();
    }

    public void resetFrame(WidgetDefinition<?, ?, ?> definition, boolean persist) {
        var source = definition.defaultFrame();
        var target = definition.frame();

        target.enabled = source.enabled;
        target.placements.clear();
        target.placements.putAll(source.placements);
        target.overrideScale = source.overrideScale;
        target.scale = source.scale;
        target.overrideBackground = source.overrideBackground;
        target.background = source.background;
        this.invalidate(definition, "widget frame reset");

        if (persist) {
            this.saveAction.run();
        }
    }

    public void resetAll(WidgetDefinition<?, ?, ?> definition, boolean persist) {
        definition.getConfigHandle().resetPreferences("all widget preferences reset");
        this.resetFrame(definition, false);

        if (persist) {
            this.saveAction.run();
        }
    }

    private void invalidate(WidgetDefinition<?, ?, ?> definition, String reason) {
        this.frameChanges(definition.getId()).invalidate(InvalidationReason.of(reason));
    }

    private WidgetsConfig config() {
        return this.configSupplier.get();
    }
}
