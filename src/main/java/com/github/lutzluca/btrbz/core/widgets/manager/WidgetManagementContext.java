package com.github.lutzluca.btrbz.core.widgets.manager;

import com.github.lutzluca.btrbz.core.widgets.WidgetId;
import com.github.lutzluca.btrbz.core.widgets.WidgetPreview;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.jetbrains.annotations.Nullable;

/** Frozen real-data overrides for widgets rendered before a Bazaar screen is replaced by the manager. */
public record WidgetManagementContext(
    @Nullable AbstractContainerScreen<?> backgroundScreen,
    Map<WidgetId, WidgetPreview<?>> frozenPreviews,
    Set<WidgetId> initiallyRendered
) {
    public WidgetManagementContext {
        frozenPreviews = Map.copyOf(frozenPreviews);
        initiallyRendered = Set.copyOf(initiallyRendered);
    }
}
