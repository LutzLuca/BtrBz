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
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

@Slf4j
public class WidgetHost {
    private final List<WidgetDefinition<?, ?>> definitions;
    private final WidgetStateStore stateStore;
    private final boolean runtime;
    private final @Nullable WidgetScreenSessionProvider sessionProvider;
    private final Map<WidgetId, WidgetRenderSurface> renderSurfaces = new HashMap<>();
    private final Map<WidgetId, WidgetInstanceState> instanceStates = new HashMap<>();
    private final Map<WidgetId, WidgetScreenSession> attachedWidgets = new HashMap<>();
    private final Set<Integer> capturedMouseButtons = new HashSet<>();

    private OwoUIAdapter<WidgetCanvasComponent> adapter;

    private WidgetHost(
        List<WidgetDefinition<?, ?>> definitions,
        WidgetStateStore stateStore,
        boolean runtime,
        @Nullable WidgetScreenSessionProvider sessionProvider
    ) {
        this.definitions = List.copyOf(definitions);
        this.stateStore = stateStore;
        this.runtime = runtime;
        this.sessionProvider = sessionProvider;
    }

    public static WidgetHost runtime(
        List<WidgetDefinition<?, ?>> definitions,
        WidgetStateStore stateStore,
        WidgetScreenSessionProvider sessionProvider
    ) {
        return new WidgetHost(
            definitions,
            stateStore,
            true,
            java.util.Objects.requireNonNull(sessionProvider, "sessionProvider")
        );
    }

    public static WidgetHost preview(
        List<WidgetDefinition<?, ?>> definitions,
        WidgetStateStore stateStore
    ) {
        return new WidgetHost(definitions, stateStore, false, null);
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
        var session = this.runtime ? this.currentSession(screen) : null;
        if (session != null) this.detachChangedSessions(session);
        var slots = new ArrayList<WidgetSlotComponent>();
        var results = new ArrayList<WidgetRenderResult>();
        var nowAttached = new HashSet<WidgetId>();

        for (var definition : this.definitions) {
            var prepared = this.prepare(definition, canvas, options, session);
            if (prepared == null) continue;
            slots.add(prepared.slot());
            results.add(prepared.result());
            nowAttached.add(definition.getId());
        }

        this.detachMissing(nowAttached);
        for (var id : nowAttached) {
            if (session != null) this.attachedWidgets.put(id, session);
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
                log.warn("Failed to dispose widget host adapter", exception);
            }
        }

        var surfaces = List.copyOf(this.renderSurfaces.values());
        this.renderSurfaces.clear();
        for (var surface : surfaces) {
            try {
                surface.close();
            } catch (RuntimeException exception) {
                log.warn("Failed to close widget render surface", exception);
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
        @Nullable WidgetScreenSession session
    ) {
        boolean selected = options.isSelected(definition);
        if (!options.shouldRender(definition.getId(), this.stateStore.isActive(definition))) return null;

        try {
            var renderContext = this.runtime
                ? new WidgetRenderContext(java.util.Objects.requireNonNull(session, "runtime session"))
                : null;
            var anchorCanvas = renderContext != null
                ? this.clampAnchor(
                    renderContext.session().anchorCanvas(definition.getAnchorSpace(), screenCanvas),
                    screenCanvas
                )
                : screenCanvas;
            if (this.runtime && !definition.getDisplayPredicate().test(renderContext)) {
                return null;
            }

            String runtimeProfile = this.runtime
                ? definition.placementProfile(renderContext)
                : WidgetScreenSession.DEFAULT_PLACEMENT_PROFILE;
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
                definition.getMinWidth(),
                definition.getMinHeight()
            );
            var layout = new WidgetLayoutContext(
                Math.max(1, (int) Math.floor(anchorCanvas.width() / scale)),
                Math.max(1, (int) Math.floor(anchorCanvas.height() / scale))
            );

            var component = this.buildComponent(definition, layout, renderContext);
            if (component == null) return null;
            component.inflate(Size.of(anchorCanvas.width(), anchorCanvas.height()));
            int logicalWidth = Math.max(definition.getMinWidth(), component.width());
            int logicalHeight = Math.max(definition.getMinHeight(), component.height());
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
                definition.getId(),
                component,
                this.renderSurface(definition.getId()),
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
        @Nullable WidgetRenderContext renderContext
    ) {
        var snapshot = this.runtime
            ? definition.getDataProvider().apply(java.util.Objects.requireNonNull(renderContext, "runtime context"))
            : definition.getPreviewDataProvider().get();
        if (this.runtime && !definition.getDataDisplayPredicate().test(snapshot)) return null;
        Consumer actions = this.runtime
            ? action -> this.dispatch(
                definition,
                action,
                java.util.Objects.requireNonNull(renderContext, "runtime context").session()
            )
            : _ -> {};
        var buildContext = new WidgetBuildContext(
            layout,
            this.instanceStates.computeIfAbsent(definition.getId(), _ -> new WidgetInstanceState()),
            actions,
            this.runtime
        );
        return WidgetChrome.wrap((UIComponent) definition.getComponentFactory().apply(snapshot, buildContext));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void dispatch(
        WidgetDefinition definition,
        Object action,
        WidgetScreenSession sourceSession
    ) {
        var client = Minecraft.getInstance();
        var currentScreen = client.screen;
        var currentSession = this.currentSession(currentScreen);
        if (sourceSession.id() != currentSession.id()) return;

        try {
            definition.getActionHandler().handle(action, sourceSession, currentSession);
        } catch (Exception exception) {
            log.warn("Widget action failed for {}", definition.getId(), exception);
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
        layout.child(UIComponents.label(Component.literal("Widget error: " + definition.getDisplayName()))
            .color(Color.ofArgb(0xFFFF8888)));
        int logicalWidth = Math.max(definition.getMinWidth(), 130);
        int logicalHeight = Math.max(definition.getMinHeight(), 24);
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
            definition.getId(),
            layout,
            this.renderSurface(definition.getId()),
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
        var previouslyAttached = new HashSet<>(this.attachedWidgets.keySet());
        previouslyAttached.addAll(this.instanceStates.keySet());
        for (var id : previouslyAttached) {
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
        log.warn("Widget {} failed during render preparation", definition.getId(), exception);
    }

    private WidgetScreenSession currentSession(@Nullable Screen screen) {
        return java.util.Objects.requireNonNull(this.sessionProvider, "runtime session provider").current(screen);
    }

    private WidgetRenderSurface renderSurface(WidgetId widgetId) {
        return this.renderSurfaces.computeIfAbsent(widgetId, _ -> new WidgetRenderSurface());
    }

    private record PreparedWidget(WidgetSlotComponent slot, WidgetRenderResult result) {}
}
