package com.github.lutzluca.btrbz.widgets.framework;

import com.github.lutzluca.btrbz.widgets.framework.screen.WidgetManagementScreen;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Composition root for the widget framework.
 *
 * <p>This object deliberately owns framework services but not application
 * services. BtrBz supplies state, semantic screen sessions, and action handlers
 * through the constructor and registered definitions.</p>
 */
public final class WidgetRuntime {
    private final WidgetRegistry registry;
    private final WidgetStateStore stateStore;
    private final WidgetScreenSessionProvider sessionProvider;
    private boolean initialized;

    public WidgetRuntime(
        WidgetRegistry registry,
        WidgetStateStore stateStore,
        WidgetScreenSessionProvider sessionProvider
    ) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore");
        this.sessionProvider = Objects.requireNonNull(sessionProvider, "sessionProvider");
    }

    public WidgetRuntime install(Consumer<WidgetRegistry> contributor) {
        if (this.initialized) {
            throw new IllegalStateException("Widget contributors must be installed before runtime initialization");
        }
        Objects.requireNonNull(contributor, "contributor").accept(this.registry);
        return this;
    }

    public WidgetRuntime initialize() {
        if (!this.initialized) {
            this.stateStore.initializeDefaults(this.registry);
            this.initialized = true;
        }
        return this;
    }

    public WidgetRegistry registry() {
        return this.registry;
    }

    public WidgetStateStore stateStore() {
        return this.stateStore;
    }

    public WidgetHost createHudHost() {
        return this.createHost(this.registry.hud(), false);
    }

    public WidgetHost createBazaarHost() {
        return this.createHost(this.registry.bazaar(), true);
    }

    public WidgetHost createContainerHost() {
        return this.createHost(this.registry.container(), true);
    }

    private WidgetHost createHost(List<WidgetDefinition<?, ?>> definitions, boolean placementDragging) {
        this.requireInitialized();
        return WidgetHost.runtime(
            definitions,
            this.stateStore,
            this.sessionProvider,
            placementDragging
        );
    }

    public WidgetManagementScreen createManagementScreen(@Nullable Screen previousScreen) {
        this.requireInitialized();
        return new WidgetManagementScreen(previousScreen, this.registry, this.stateStore);
    }

    public WidgetManagementScreen createManagementScreenForWidget(
        @Nullable Screen previousScreen,
        WidgetId widgetId
    ) {
        this.requireInitialized();
        return new WidgetManagementScreen(previousScreen, this.registry, this.stateStore, widgetId);
    }

    public boolean initialized() {
        return this.initialized;
    }

    private void requireInitialized() {
        if (!this.initialized) {
            throw new IllegalStateException("Widget runtime must be initialized after installing contributors");
        }
    }
}
