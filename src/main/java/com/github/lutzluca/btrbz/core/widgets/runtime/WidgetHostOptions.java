package com.github.lutzluca.btrbz.core.widgets.runtime;

import com.github.lutzluca.btrbz.core.widgets.WidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.WidgetId;
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
    private static final WidgetHostOptions RUNTIME_WITH_TOOLTIPS =
        new WidgetHostOptions(null, false, true, null, Map.of());
    private static final WidgetHostOptions RUNTIME_NO_TOOLTIPS =
        new WidgetHostOptions(null, false, false, null, Map.of());

    public WidgetHostOptions {
        placementProfiles = Map.copyOf(placementProfiles);
    }

    public static WidgetHostOptions runtime(boolean allowTooltips) {
        return allowTooltips ? RUNTIME_WITH_TOOLTIPS : RUNTIME_NO_TOOLTIPS;
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
