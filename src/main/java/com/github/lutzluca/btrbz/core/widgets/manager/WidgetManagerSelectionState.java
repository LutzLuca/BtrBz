package com.github.lutzluca.btrbz.core.widgets.manager;

import com.github.lutzluca.btrbz.core.widgets.WidgetId;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

final class WidgetManagerSelectionState {
    private final Set<WidgetId> renderedWidgets = new LinkedHashSet<>();
    private @Nullable WidgetId selectedWidget;

    WidgetManagerSelectionState(WidgetManagementLaunchState launchState) {
        Objects.requireNonNull(launchState, "launchState");
        this.selectedWidget = launchState.selectedWidget();
        this.renderedWidgets.addAll(launchState.renderedWidgets());
    }

    @Nullable WidgetId selectedWidget() {
        return this.selectedWidget;
    }

    Set<WidgetId> renderedWidgets() {
        return Set.copyOf(this.renderedWidgets);
    }

    boolean select(WidgetId id) {
        Objects.requireNonNull(id, "id");
        boolean changed = this.renderedWidgets.add(id);
        if (!id.equals(this.selectedWidget)) {
            this.selectedWidget = id;
            changed = true;
        }
        return changed;
    }

    boolean clearSelection() {
        if (this.selectedWidget == null) return false;
        this.selectedWidget = null;
        return true;
    }

    boolean setRendered(WidgetId id, boolean rendered) {
        Objects.requireNonNull(id, "id");
        return rendered
            ? this.renderedWidgets.add(id)
            : this.renderedWidgets.remove(id);
    }
}
