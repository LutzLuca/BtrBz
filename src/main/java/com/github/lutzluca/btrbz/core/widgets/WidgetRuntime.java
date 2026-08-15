package com.github.lutzluca.btrbz.core.widgets;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetStateStore;
import com.github.lutzluca.btrbz.core.widgets.manager.WidgetManagementScreen;
import com.github.lutzluca.btrbz.core.widgets.manager.WidgetManagementContext;
import com.github.lutzluca.btrbz.core.widgets.runtime.WidgetHost;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSessionProvider;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import org.jetbrains.annotations.Nullable;
import lombok.extern.slf4j.Slf4j;

/** Application facade over BtrBz's finalized production widget registry. */
@Slf4j
public final class WidgetRuntime {
    private final WidgetRegistry registry;
    private final WidgetStateStore stateStore;
    private final WidgetSessionProvider sessionProvider;
    private final Map<WidgetId, Double> scrollOffsets = new HashMap<>();

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

    public void invalidateWidgetContent(WidgetId id, String reason) {
        this.registry.find(id).orElseThrow().getConfigHandle().invalidate(reason);
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
            this.scrollOffsets,
            placementDragging);
    }

    public WidgetManagementScreen createManagementScreen(@Nullable Screen previousScreen) {
        var context = this.captureManagementContext(previousScreen);
        return context == null
            ? new WidgetManagementScreen(previousScreen, this.registry, this.stateStore)
            : new WidgetManagementScreen(previousScreen, this.registry, this.stateStore, context);
    }

    public WidgetManagementScreen createManagementScreenForWidget(
        @Nullable Screen previousScreen,
        WidgetId widgetId
    ) {
        var context = this.captureManagementContext(previousScreen);
        return context == null
            ? new WidgetManagementScreen(previousScreen, this.registry, this.stateStore, widgetId)
            : new WidgetManagementScreen(previousScreen, this.registry, this.stateStore, widgetId, context);
    }

    public boolean canOpenContextualManager(@Nullable Screen screen) {
        var session = this.sessionProvider.current(screen);
        return contextualManagerSupported(
            screen instanceof AbstractContainerScreen<?>,
            screen instanceof SignEditScreen,
            session,
            this.registry.all());
    }

    static boolean contextualManagerSupported(
        boolean containerScreen,
        boolean signScreen,
        WidgetSession session,
        List<WidgetDefinition<?, ?, ?>> definitions
    ) {
        if (containerScreen) {
            return session.inBazaarContainer();
        }

        return signScreen && definitions.stream().anyMatch(definition -> definition.supports(session));
    }

    private @Nullable WidgetManagementContext captureManagementContext(@Nullable Screen screen) {
        if (!this.canOpenContextualManager(screen)) {
            return null;
        }

        var session = this.sessionProvider.current(screen);
        var frozenSession = session.detachedCopy();

        var previews = new LinkedHashMap<WidgetId, WidgetPreview<?>>();
        var rendered = new LinkedHashSet<WidgetId>();

        for (var definition : this.registry.all()) {
            if (!definition.frame().enabled || !definition.supports(frozenSession)) {
                continue;
            }

            try {
                var preview = capture(definition, frozenSession);

                if (!visible(definition, preview)) {
                    continue;
                }

                previews.put(definition.getId(), preview);
                rendered.add(definition.getId());
            } catch (RuntimeException exception) {
                log.warn("Failed to freeze widget {} for management", definition.getId(), exception);
            }
        }

        var background = screen instanceof AbstractContainerScreen<?> container ? container : null;

        return new WidgetManagementContext(background, previews, rendered);
    }

    @SuppressWarnings({"rawtypes"})
    private static WidgetPreview<?> capture(WidgetDefinition definition, WidgetSession session) {
        return definition.captureRuntimePreview(session);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean visible(WidgetDefinition definition, WidgetPreview preview) {
        return definition.isVisible(preview);
    }
}
