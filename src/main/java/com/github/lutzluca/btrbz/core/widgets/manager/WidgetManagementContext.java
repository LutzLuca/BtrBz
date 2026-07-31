package com.github.lutzluca.btrbz.core.widgets.manager;

import com.github.lutzluca.btrbz.core.widgets.WidgetId;
import com.github.lutzluca.btrbz.core.widgets.WidgetPreview;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/** Frozen real-data overrides for widgets rendered before a Bazaar screen is replaced by the manager. */
public record WidgetManagementContext(
    AbstractContainerScreen<?> backgroundScreen,
    Map<WidgetId, WidgetPreview<?>> frozenPreviews,
    Set<WidgetId> initiallyRendered
) {
    public WidgetManagementContext {
        Objects.requireNonNull(backgroundScreen, "backgroundScreen");
        frozenPreviews = Map.copyOf(frozenPreviews);
        initiallyRendered = Set.copyOf(initiallyRendered);
    }
}
