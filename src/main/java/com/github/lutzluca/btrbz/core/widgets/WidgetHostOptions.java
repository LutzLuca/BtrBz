package com.github.lutzluca.btrbz.core.widgets;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

public record WidgetHostOptions(
    @Nullable WidgetId selectedWidget,
    boolean drawManagementOverlay,
    boolean allowTooltips,
    @Nullable Set<WidgetId> renderedWidgets,
    Map<WidgetId, String> placementProfiles
) {
    public WidgetHostOptions {
        placementProfiles = Map.copyOf(placementProfiles);
    }

    public static WidgetHostOptions runtime(boolean allowTooltips) {
        return new WidgetHostOptions(null, false, allowTooltips, null, Map.of());
    }

    public static WidgetHostOptions management(
        @Nullable WidgetId selectedWidget,
        Set<WidgetId> renderedWidgets,
        Map<WidgetId, String> placementProfiles
    ) {
        return new WidgetHostOptions(
            selectedWidget,
            true,
            false,
            Set.copyOf(renderedWidgets),
            placementProfiles
        );
    }

    public boolean isSelected(WidgetDefinition<?, ?, ?> definition) {
        return this.selectedWidget != null && this.selectedWidget.equals(definition.getId());
    }

    public boolean shouldRender(WidgetId widgetId, boolean runtimeVisible) {
        return this.renderedWidgets == null ? runtimeVisible : this.renderedWidgets.contains(widgetId);
    }

    public String placementProfile(WidgetDefinition<?, ?, ?> definition, String runtimeProfile) {
        return this.placementProfiles.getOrDefault(definition.getId(), runtimeProfile);
    }
}
