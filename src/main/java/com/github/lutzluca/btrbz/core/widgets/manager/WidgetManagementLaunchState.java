package com.github.lutzluca.btrbz.core.widgets.manager;

import com.github.lutzluca.btrbz.core.widgets.WidgetId;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Set;

record WidgetManagementLaunchState(
    @Nullable WidgetId selectedWidget,
    Set<WidgetId> renderedWidgets
) {
    WidgetManagementLaunchState {
        renderedWidgets = Set.copyOf(renderedWidgets);
    }

    static WidgetManagementLaunchState empty() {
        return new WidgetManagementLaunchState(null, Set.of());
    }

    static WidgetManagementLaunchState configure(WidgetId widgetId) {
        Objects.requireNonNull(widgetId, "widgetId");
        return new WidgetManagementLaunchState(widgetId, Set.of(widgetId));
    }

    static WidgetManagementLaunchState contextual(Set<WidgetId> renderedWidgets) {
        return new WidgetManagementLaunchState(null, renderedWidgets);
    }

    static WidgetManagementLaunchState contextual(Set<WidgetId> renderedWidgets, WidgetId selectedWidget) {
        Objects.requireNonNull(selectedWidget, "selectedWidget");
        var rendered = new java.util.LinkedHashSet<>(renderedWidgets);
        rendered.add(selectedWidget);
        return new WidgetManagementLaunchState(selectedWidget, rendered);
    }
}
