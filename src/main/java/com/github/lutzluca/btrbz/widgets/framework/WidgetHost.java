package com.github.lutzluca.btrbz.widgets.framework;

import com.github.lutzluca.btrbz.widgets.framework.ui.WidgetCanvasComponent;
import com.github.lutzluca.btrbz.widgets.framework.ui.WidgetChrome;
import com.github.lutzluca.btrbz.widgets.framework.ui.WidgetRenderSurface;
import com.github.lutzluca.btrbz.widgets.framework.ui.WidgetSlotComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Size;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class WidgetHost {
    private static final Logger LOGGER = LoggerFactory.getLogger(WidgetHost.class);
    private final List<WidgetDefinition<?, ?>> definitions;
    private final WidgetStateStore stateStore;
    private final boolean runtime;
    private final WidgetScreenSessionProvider sessionProvider;
    private final Map<WidgetId, WidgetRenderSurface> renderSurfaces = new HashMap<>();
    private final Map<WidgetId, WidgetInstanceState> instanceStates = new HashMap<>();
    private final Map<WidgetId, WidgetScreenSession> attachedWidgets = new HashMap<>();
    private final Set<Integer> capturedMouseButtons = new HashSet<>();

    private OwoUIAdapter<WidgetCanvasComponent> adapter;

    public WidgetHost(
        List<WidgetDefinition<?, ?>> definitions,
        WidgetStateStore stateStore,
        boolean runtime
    ) {
        this(definitions, stateStore, runtime, WidgetScreenSessionProvider.empty());
    }

    public WidgetHost(
        List<WidgetDefinition<?, ?>> definitions,
        WidgetStateStore stateStore,
        boolean runtime,
        WidgetScreenSessionProvider sessionProvider
    ) {
        this.definitions = List.copyOf(definitions);
        this.stateStore = stateStore;
        this.runtime = runtime;
        this.sessionProvider = sessionProvider;
    }

    public List<WidgetRenderResult> render(
        GuiGraphicsExtractor graphics,
        int mouseX,
        int mouseY,
        float partialTicks,
        WidgetCanvas canvas,
        WidgetHostOptions options,
        @Nullable Screen screen
    ) {
        this.ensureAdapter();
        var session = this.sessionProvider.current(screen);
        this.detachChangedSessions(session);
        var slots = new ArrayList<WidgetSlotComponent>();
        var results = new ArrayList<WidgetRenderResult>();
        var nowAttached = new HashSet<WidgetId>();

        for (var definition : this.definitions) {
            var prepared = this.prepare(definition, canvas, options, session);
            if (prepared == null) continue;
            slots.add(prepared.slot());
            results.add(prepared.result());
            nowAttached.add(definition.id());
        }

        this.detachMissing(nowAttached);
        for (var id : nowAttached) {
            this.attachedWidgets.put(id, session);
        }

        this.adapter.rootComponent.replaceSlots(slots);
        this.adapter.moveAndResize(canvas.x(), canvas.y(), canvas.width(), canvas.height());
        this.adapter.extractRenderState(graphics, mouseX, mouseY, partialTicks);
        if (options.allowTooltips()) this.adapter.drawTooltip(graphics, mouseX, mouseY, partialTicks);

        return List.copyOf(results);
    }

    public void dispose() {
        this.detachMissing(Set.of());
        this.attachedWidgets.clear();
        this.instanceStates.values().forEach(WidgetInstanceState::clear);
        this.instanceStates.clear();
        this.capturedMouseButtons.clear();

        var adapter = this.adapter;
        this.adapter = null;
        if (adapter != null) {
            try {
                adapter.dispose();
            } catch (RuntimeException exception) {
                LOGGER.warn("Failed to dispose widget host adapter", exception);
            }
        }

        var surfaces = List.copyOf(this.renderSurfaces.values());
        this.renderSurfaces.clear();
        for (var surface : surfaces) {
            try {
                surface.close();
            } catch (RuntimeException exception) {
                LOGGER.warn("Failed to close widget render surface", exception);
            }
        }
    }

    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (this.adapter == null) return false;

        boolean handled = this.adapter.mouseClicked(click, doubled);
        if (handled) this.capturedMouseButtons.add(click.button());
        return handled;
    }

    public boolean mouseReleased(MouseButtonEvent click) {
        boolean captured = this.capturedMouseButtons.remove(click.button());
        return this.adapter == null
            ? captured
            : this.adapter.mouseReleased(click) || captured;
    }

    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        boolean captured = this.capturedMouseButtons.contains(click.button());
        return this.adapter == null
            ? captured
            : this.adapter.mouseDragged(click, deltaX, deltaY) || captured;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return this.adapter != null && this.adapter.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    public boolean keyPressed(KeyEvent input) {
        return this.adapter != null && this.adapter.keyPressed(input);
    }

    private void ensureAdapter() {
        if (this.adapter == null) {
            this.adapter = OwoUIAdapter.createWithoutScreen(0, 0, 1, 1, WidgetCanvasComponent::new);
        }
    }

    private PreparedWidget prepare(
        WidgetDefinition<?, ?> definition,
        WidgetCanvas screenCanvas,
        WidgetHostOptions options,
        WidgetScreenSession session
    ) {
        boolean selected = options.isSelected(definition);
        if (!options.shouldRender(definition.id(), this.stateStore.isActive(definition))) return null;

        try {
            var anchorCanvas = this.clampAnchor(session.anchorCanvas(definition.anchorSpace(), screenCanvas), screenCanvas);
            var renderContext = new WidgetRenderContext(session);
            if (this.runtime && !definition.displayPredicate().test(renderContext)) {
                return null;
            }

            String runtimeProfile = definition.placementProfile(renderContext);
            String profile = options.placementProfile(definition, runtimeProfile);
            var placement = this.stateStore.placement(definition, profile);
            double minimumScale = WidgetScaleResolver.readableMinimumScale(
                Minecraft.getInstance().getWindow().getGuiScale()
            );
            double requestedScale = Math.max(this.stateStore.requestedScale(definition), minimumScale);
            double scale = WidgetScaleResolver.fitToCanvas(
                requestedScale,
                minimumScale,
                anchorCanvas.width(),
                anchorCanvas.height(),
                definition.minWidth(),
                definition.minHeight()
            );
            var layout = new WidgetLayoutContext(
                Math.max(1, (int) Math.floor(anchorCanvas.width() / scale)),
                Math.max(1, (int) Math.floor(anchorCanvas.height() / scale))
            );

            var component = this.buildComponent(definition, layout, renderContext);
            if (component == null) return null;
            component.inflate(Size.of(anchorCanvas.width(), anchorCanvas.height()));
            int logicalWidth = Math.max(definition.minWidth(), component.width());
            int logicalHeight = Math.max(definition.minHeight(), component.height());
            scale = WidgetScaleResolver.fitToCanvas(
                requestedScale,
                minimumScale,
                anchorCanvas.width(),
                anchorCanvas.height(),
                logicalWidth,
                logicalHeight
            );
            if (!WidgetScaleResolver.fitsCanvas(
                scale, anchorCanvas.width(), anchorCanvas.height(), logicalWidth, logicalHeight
            )) return null;

            int scaledWidth = Math.max(1, (int) Math.ceil(logicalWidth * scale));
            int scaledHeight = Math.max(1, (int) Math.ceil(logicalHeight * scale));
            var resolved = placement.resolve(
                anchorCanvas.width(), anchorCanvas.height(), scaledWidth, scaledHeight
            );
            var localBounds = new WidgetBounds(
                anchorCanvas.x() - screenCanvas.x() + resolved.x(),
                anchorCanvas.y() - screenCanvas.y() + resolved.y(),
                resolved.width(),
                resolved.height()
            );
            var absoluteBounds = new WidgetBounds(
                screenCanvas.x() + localBounds.x(),
                screenCanvas.y() + localBounds.y(),
                localBounds.width(),
                localBounds.height()
            );
            var slot = new WidgetSlotComponent(
                definition.id(),
                component,
                this.renderSurface(definition.id()),
                this.stateStore.backgroundColor(definition, WidgetChrome.DEFAULT_BACKGROUND),
                localBounds,
                logicalWidth,
                logicalHeight,
                scale,
                selected,
                options.drawManagementOverlay()
            );
            return new PreparedWidget(
                slot,
                new WidgetRenderResult(definition, profile, absoluteBounds, logicalWidth, logicalHeight, scale)
            );
        } catch (Exception exception) {
            this.logWidgetFailure(definition, exception);
            if (this.runtime) return null;
            return this.prepareFallback(definition, screenCanvas, options, exception);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private UIComponent buildComponent(
        WidgetDefinition definition,
        WidgetLayoutContext layout,
        WidgetRenderContext renderContext
    ) {
        var provider = this.runtime ? definition.dataProvider() : definition.previewDataProvider();
        var snapshot = provider.apply(renderContext);
        if (this.runtime && !definition.dataDisplayPredicate().test(snapshot)) return null;
        Consumer actions = this.runtime
            ? action -> this.dispatch(definition, action, renderContext.session())
            : action -> {};
        var buildContext = new WidgetBuildContext(
            layout,
            this.instanceStates.computeIfAbsent(definition.id(), ignored -> new WidgetInstanceState()),
            actions,
            this.runtime
        );
        return WidgetChrome.wrap((UIComponent) definition.componentFactory().apply(snapshot, buildContext));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void dispatch(
        WidgetDefinition definition,
        Object action,
        WidgetScreenSession sourceSession
    ) {
        var client = Minecraft.getInstance();
        var currentScreen = client.screen;
        var currentSession = this.sessionProvider.current(currentScreen);
        if (sourceSession.id() != currentSession.id()) return;

        try {
            definition.actionHandler().handle(action, sourceSession, currentSession);
        } catch (Exception exception) {
            LOGGER.warn("Widget action failed for {}", definition.id(), exception);
        }
    }

    private PreparedWidget prepareFallback(
        WidgetDefinition<?, ?> definition,
        WidgetCanvas canvas,
        WidgetHostOptions options,
        Exception exception
    ) {
        var layout = UIContainers.verticalFlow(Sizing.content(), Sizing.content());
        layout.padding(Insets.of(6));
        layout.child(UIComponents.label(Component.literal("Widget error: " + definition.displayName()))
            .color(Color.ofArgb(0xFFFF8888)));
        int logicalWidth = Math.max(definition.minWidth(), 130);
        int logicalHeight = Math.max(definition.minHeight(), 24);
        double minimumScale = WidgetScaleResolver.readableMinimumScale(
            Minecraft.getInstance().getWindow().getGuiScale()
        );
        double scale = WidgetScaleResolver.fitToCanvas(
            Math.max(this.stateStore.requestedScale(definition), minimumScale),
            minimumScale,
            canvas.width(),
            canvas.height(),
            logicalWidth,
            logicalHeight
        );
        if (!WidgetScaleResolver.fitsCanvas(
            scale, canvas.width(), canvas.height(), logicalWidth, logicalHeight
        )) return null;
        String profile = options.placementProfile(
            definition, WidgetScreenSession.DEFAULT_PLACEMENT_PROFILE
        );
        var placement = this.stateStore.placement(definition, profile);
        int scaledWidth = Math.max(1, (int) Math.ceil(logicalWidth * scale));
        int scaledHeight = Math.max(1, (int) Math.ceil(logicalHeight * scale));
        var localBounds = placement.resolve(canvas.width(), canvas.height(), scaledWidth, scaledHeight);
        var slot = new WidgetSlotComponent(
            definition.id(),
            layout,
            this.renderSurface(definition.id()),
            this.stateStore.backgroundColor(definition, WidgetChrome.DEFAULT_BACKGROUND),
            localBounds,
            logicalWidth,
            logicalHeight,
            scale,
            options.isSelected(definition),
            options.drawManagementOverlay()
        );
        return new PreparedWidget(
            slot,
            new WidgetRenderResult(
                definition,
                profile,
                new WidgetBounds(
                    canvas.x() + localBounds.x(), canvas.y() + localBounds.y(),
                    localBounds.width(), localBounds.height()
                ),
                logicalWidth,
                logicalHeight,
                scale
            )
        );
    }

    private void detachMissing(Set<WidgetId> nowAttached) {
        for (var id : Set.copyOf(this.attachedWidgets.keySet())) {
            if (nowAttached.contains(id)) continue;
            this.attachedWidgets.remove(id);
            var state = this.instanceStates.remove(id);
            if (state != null) state.clear();
        }
    }

    private void detachChangedSessions(WidgetScreenSession currentSession) {
        for (var entry : Map.copyOf(this.attachedWidgets).entrySet()) {
            if (entry.getValue().id() == currentSession.id()) continue;

            var id = entry.getKey();
            this.attachedWidgets.remove(id);
            var state = this.instanceStates.remove(id);
            if (state != null) state.clear();
        }
    }

    private WidgetCanvas clampAnchor(WidgetCanvas requested, WidgetCanvas screen) {
        int left = Math.max(screen.x(), requested.x());
        int top = Math.max(screen.y(), requested.y());
        int right = Math.min(screen.x() + screen.width(), requested.x() + requested.width());
        int bottom = Math.min(screen.y() + screen.height(), requested.y() + requested.height());
        if (right <= left || bottom <= top) return screen;
        return new WidgetCanvas(left, top, right - left, bottom - top);
    }

    private void logWidgetFailure(WidgetDefinition<?, ?> definition, Exception exception) {
        LOGGER.warn("Widget {} failed during render preparation", definition.id(), exception);
    }

    private WidgetRenderSurface renderSurface(WidgetId widgetId) {
        return this.renderSurfaces.computeIfAbsent(widgetId, ignored -> new WidgetRenderSurface());
    }

    private record PreparedWidget(WidgetSlotComponent slot, WidgetRenderResult result) {}
}
