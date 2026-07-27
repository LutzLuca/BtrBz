package com.github.lutzluca.btrbz.core.widgets;

import com.github.lutzluca.btrbz.core.widgets.manager.WidgetManagementScreen;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSessionProvider;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Runtime services over BtrBz's finalized production widget registry.
 */
public final class WidgetRuntime {
    private final WidgetRegistry registry;
    private final WidgetStateStore stateStore;
    private final WidgetSessionProvider sessionProvider;

    public WidgetRuntime(
        WidgetRegistry registry,
        WidgetStateStore stateStore,
        WidgetSessionProvider sessionProvider
    ) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.registry.freeze();
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore");
        this.sessionProvider = Objects.requireNonNull(sessionProvider, "sessionProvider");
    }

    public WidgetRegistry registry() {
        return this.registry;
    }

    public WidgetStateStore stateStore() {
        return this.stateStore;
    }

    public WidgetHost createHudHost() {
        return this.createHost(false);
    }

    public WidgetHost createScreenHost() {
        return this.createHost(true);
    }

    private WidgetHost createHost(boolean placementDragging) {
        return WidgetHost.runtime(
            this.registry.all(),
            this.stateStore,
            this.sessionProvider,
            placementDragging
        );
    }

    public WidgetManagementScreen createManagementScreen(@Nullable Screen previousScreen) {
        return new WidgetManagementScreen(previousScreen, this.registry, this.stateStore);
    }

    public WidgetManagementScreen createManagementScreenForWidget(
        @Nullable Screen previousScreen,
        WidgetId widgetId
    ) {
        return new WidgetManagementScreen(previousScreen, this.registry, this.stateStore, widgetId);
    }
}
