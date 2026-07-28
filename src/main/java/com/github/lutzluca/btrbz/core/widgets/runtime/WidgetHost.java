package com.github.lutzluca.btrbz.core.widgets.runtime;

import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSessionProvider;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetCanvasComponent;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetChrome;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetRenderSurface;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetSlotComponent;
import com.github.lutzluca.btrbz.core.widgets.layout.WidgetCanvas;
import com.github.lutzluca.btrbz.core.widgets.layout.WidgetBounds;
import com.github.lutzluca.btrbz.core.widgets.layout.WidgetPlacement;
import com.github.lutzluca.btrbz.core.widgets.layout.WidgetScaleResolver;
import com.github.lutzluca.btrbz.core.widgets.WidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.WidgetId;
import com.github.lutzluca.btrbz.core.widgets.WidgetPreview;
import com.github.lutzluca.btrbz.core.widgets.WidgetView;
import com.github.lutzluca.btrbz.core.widgets.config.WidgetStateStore;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Size;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/** Retained runtime/preview host for the single ordered widget registry. */
@Slf4j
public final class WidgetHost {
    private final List<WidgetDefinition<?, ?, ?>> definitions;
    private final WidgetStateStore stateStore;
    private final boolean runtime;
    private final boolean runtimePlacementDragging;
    private final @Nullable WidgetSessionProvider sessionProvider;
    private final Map<WidgetId, MountedWidget> mounted = new LinkedHashMap<>();
    private final Set<Integer> capturedMouseButtons = new HashSet<>();
    private OwoUIAdapter<WidgetCanvasComponent> adapter;
    private List<RuntimeWidgetHit> runtimeWidgetHits = List.of();
    private @Nullable RuntimePlacementDrag runtimePlacementDrag;

    private WidgetHost(
        List<WidgetDefinition<?, ?, ?>> definitions,
        WidgetStateStore stateStore,
        boolean runtime,
        boolean runtimePlacementDragging,
        @Nullable WidgetSessionProvider sessionProvider
    ) {
        this.definitions = List.copyOf(definitions);
        this.stateStore = stateStore;
        this.runtime = runtime;
        this.runtimePlacementDragging = runtimePlacementDragging;
        this.sessionProvider = sessionProvider;
    }

    public static WidgetHost runtime(
        List<WidgetDefinition<?, ?, ?>> definitions,
        WidgetStateStore stateStore,
        WidgetSessionProvider sessionProvider,
        boolean placementDragging
    ) {
        return new WidgetHost(definitions, stateStore, true, placementDragging, sessionProvider);
    }

    public static WidgetHost preview(
        List<WidgetDefinition<?, ?, ?>> definitions,
        WidgetStateStore stateStore
    ) {
        return new WidgetHost(definitions, stateStore, false, false, null);
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
        WidgetSession runtimeSession = this.runtime ? this.currentSession(screen) : null;
        var attached = new HashSet<WidgetId>();
        var orderedSlots = new ArrayList<WidgetSlotComponent>();
        var results = new ArrayList<WidgetRenderResult>();
        var hits = new ArrayList<RuntimeWidgetHit>();

        for (var definition : this.definitions) {
            boolean requested = options.shouldRender(definition.getId(), definition.frame().enabled);
            if (!requested || this.runtime && !definition.supports(runtimeSession)) continue;
            var prepared = this.prepare(definition, canvas, options, runtimeSession);
            if (prepared == null) continue;
            attached.add(definition.getId());
            orderedSlots.add(prepared.mounted().slot());
            if (prepared.result() != null) {
                results.add(prepared.result());
                if (this.runtimePlacementDragging) {
                    hits.add(new RuntimeWidgetHit(prepared.result(), prepared.anchorCanvas()));
                }
            }
        }

        this.detachMissing(attached);
        this.runtimeWidgetHits = List.copyOf(hits);
        this.adapter.rootComponent.synchronizeSlots(orderedSlots);
        this.adapter.moveAndResize(canvas.x(), canvas.y(), canvas.width(), canvas.height());
        this.adapter.extractRenderState(graphics, mouseX, mouseY, partialTicks);
        if (options.allowTooltips()) this.adapter.drawTooltip(graphics, mouseX, mouseY, partialTicks);
        return List.copyOf(results);
    }

    public void dispose() {
        this.detachMissing(Set.of());
        this.capturedMouseButtons.clear();
        this.runtimeWidgetHits = List.of();
        this.runtimePlacementDrag = null;
        var current = this.adapter;
        this.adapter = null;
        if (current != null) {
            try { current.dispose(); }
            catch (RuntimeException exception) { log.warn("Failed to dispose widget host adapter", exception); }
        }
    }

    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (this.adapter == null) return false;
        boolean handled = this.adapter.mouseClicked(click, doubled);
        if (handled) {
            this.capturedMouseButtons.add(click.button());
            return true;
        }
        if (!this.runtimePlacementDragging || click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        var hit = this.runtimeHitAt(click.x(), click.y());
        if (hit == null) return false;
        var result = hit.result();
        this.runtimePlacementDrag = new RuntimePlacementDrag(
            result.definition(), result.placementProfile(), hit.anchorCanvas(),
            click.x() - result.bounds().x(), click.y() - result.bounds().y(),
            result.bounds().width(), result.bounds().height(), click.button()
        );
        this.capturedMouseButtons.add(click.button());
        return true;
    }

    public boolean mouseReleased(MouseButtonEvent click) {
        if (this.runtimePlacementDrag != null && this.runtimePlacementDrag.button() == click.button()) {
            this.updateRuntimePlacement(click.x(), click.y());
            this.runtimePlacementDrag = null;
            this.capturedMouseButtons.remove(click.button());
            this.stateStore.save();
            return true;
        }
        boolean captured = this.capturedMouseButtons.remove(click.button());
        return this.adapter == null ? captured : this.adapter.mouseReleased(click) || captured;
    }

    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        if (this.runtimePlacementDrag != null && this.runtimePlacementDrag.button() == click.button()) {
            this.updateRuntimePlacement(click.x(), click.y());
            return true;
        }
        boolean captured = this.capturedMouseButtons.contains(click.button());
        return this.adapter == null ? captured : this.adapter.mouseDragged(click, deltaX, deltaY) || captured;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return this.adapter != null && this.adapter.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    public boolean keyPressed(KeyEvent input) { return this.adapter != null && this.adapter.keyPressed(input); }

    private void ensureAdapter() {
        if (this.adapter == null) {
            this.adapter = OwoUIAdapter.createWithoutScreen(0, 0, 1, 1, WidgetCanvasComponent::new);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private @Nullable PreparedWidget prepare(
        WidgetDefinition definition,
        WidgetCanvas screenCanvas,
        WidgetHostOptions options,
        @Nullable WidgetSession runtimeSession
    ) {
        try {
            WidgetPreview preview = this.runtime ? null : (WidgetPreview) definition.getPreview().get();
            WidgetSession session = this.runtime ? runtimeSession : preview.session();
            Object data = this.runtime ? definition.getRuntimeData().apply(session) : preview.data();
            Object config = definition.config();
            var mountedWidget = this.mounted.computeIfAbsent(
                definition.getId(), _ -> this.mount(definition)
            );
            Consumer actions = this.runtime
                ? action -> this.dispatch(definition, action, session)
                : _ -> {};
            ((WidgetView) mountedWidget.view()).update(data, config, session, actions);
            boolean visible = !this.runtime || definition.getVisibility().test(data, config, session);
            String initialProfile = this.runtime
                ? definition.placementProfile(session)
                : preview.placementProfile();
            String profile = options.placementProfile(definition, initialProfile);
            var anchorCanvas = screenCanvas;
            if (!visible) {
                mountedWidget.slot().update(
                    this.stateStore.backgroundColor(definition, WidgetChrome.DEFAULT_BACKGROUND),
                    mountedWidget.slot().localBounds(), 1, 1, 1,
                    options.isSelected(definition), options.drawManagementOverlay(), false
                );
                return new PreparedWidget(mountedWidget, null, anchorCanvas);
            }

            double minimumScale = WidgetScaleResolver.MIN_SCALE;
            double requestedScale = Math.max(this.stateStore.requestedScale(definition), minimumScale);
            double scale = WidgetScaleResolver.fitToCanvas(
                requestedScale, minimumScale, anchorCanvas.width(), anchorCanvas.height(),
                definition.getMinWidth(), definition.getMinHeight()
            );
            var component = mountedWidget.component();
            component.inflate(Size.of(
                Math.max(1, (int) Math.floor(anchorCanvas.width() / scale)),
                Math.max(1, (int) Math.floor(anchorCanvas.height() / scale))
            ));
            int logicalWidth = Math.max(definition.getMinWidth(), component.width());
            int logicalHeight = Math.max(definition.getMinHeight(), component.height());
            scale = WidgetScaleResolver.fitToCanvas(
                requestedScale, minimumScale, anchorCanvas.width(), anchorCanvas.height(),
                logicalWidth, logicalHeight
            );
            if (!WidgetScaleResolver.fitsCanvas(
                scale, anchorCanvas.width(), anchorCanvas.height(), logicalWidth, logicalHeight
            )) return null;
            int scaledWidth = Math.max(1, (int) Math.ceil(logicalWidth * scale));
            int scaledHeight = Math.max(1, (int) Math.ceil(logicalHeight * scale));
            var resolved = this.stateStore.placement(definition, profile).resolve(
                anchorCanvas.width(), anchorCanvas.height(), scaledWidth, scaledHeight
            );
            var localBounds = new WidgetBounds(
                anchorCanvas.x() - screenCanvas.x() + resolved.x(),
                anchorCanvas.y() - screenCanvas.y() + resolved.y(),
                resolved.width(), resolved.height()
            );
            mountedWidget.slot().update(
                this.stateStore.backgroundColor(definition, WidgetChrome.DEFAULT_BACKGROUND),
                localBounds, logicalWidth, logicalHeight, scale,
                options.isSelected(definition), options.drawManagementOverlay(), true
            );
            var result = new WidgetRenderResult(
                definition, profile,
                new WidgetBounds(
                    screenCanvas.x() + localBounds.x(), screenCanvas.y() + localBounds.y(),
                    localBounds.width(), localBounds.height()
                ),
                logicalWidth, logicalHeight, scale
            );
            return new PreparedWidget(mountedWidget, result, anchorCanvas);
        } catch (Exception exception) {
            log.warn("Widget {} failed during retained update", definition.getId(), exception);
            this.detach(definition.getId());
            return null;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private MountedWidget mount(WidgetDefinition definition) {
        WidgetView view = (WidgetView) definition.getViewFactory().get();
        var component = WidgetChrome.wrap(view.root());
        var surface = new WidgetRenderSurface();
        var slot = new WidgetSlotComponent(
            definition.getId(), component, surface, WidgetChrome.DEFAULT_BACKGROUND,
            new WidgetBounds(0, 0, 1, 1), 1, 1, 1, false, false
        );
        return new MountedWidget(definition, view, component, slot, surface);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void dispatch(WidgetDefinition definition, Object action, WidgetSession source) {
        var current = this.currentSession(Minecraft.getInstance().screen);
        if (source.id() != current.id()) return;
        try { definition.getActionHandler().handle(action, source, current); }
        catch (Exception exception) { log.warn("Widget action failed for {}", definition.getId(), exception); }
    }

    private void detachMissing(Set<WidgetId> attached) {
        for (var id : List.copyOf(this.mounted.keySet())) {
            if (!attached.contains(id)) this.detach(id);
        }
    }

    private void detach(WidgetId id) {
        var removed = this.mounted.remove(id);
        if (removed == null) return;
        try { removed.view().close(); }
        catch (RuntimeException exception) { log.warn("Failed to close widget view {}", id, exception); }
        try { removed.surface().close(); }
        catch (RuntimeException exception) { log.warn("Failed to close widget render surface {}", id, exception); }
    }

    private WidgetSession currentSession(@Nullable Screen screen) {
        return java.util.Objects.requireNonNull(this.sessionProvider, "runtime session provider").current(screen);
    }

    private @Nullable RuntimeWidgetHit runtimeHitAt(double x, double y) {
        for (int index = this.runtimeWidgetHits.size() - 1; index >= 0; index--) {
            var hit = this.runtimeWidgetHits.get(index);
            if (hit.result().bounds().contains(x, y)) return hit;
        }
        return null;
    }

    private void updateRuntimePlacement(double mouseX, double mouseY) {
        var drag = this.runtimePlacementDrag;
        if (drag == null) return;
        int x = (int) Math.round(mouseX - drag.pointerOffsetX() - drag.anchorCanvas().x());
        int y = (int) Math.round(mouseY - drag.pointerOffsetY() - drag.anchorCanvas().y());
        this.stateStore.setPlacement(
            drag.definition(), drag.placementProfile(),
            WidgetPlacement.fromAbsolute(
                x, y, drag.anchorCanvas().width(), drag.anchorCanvas().height(),
                drag.scaledWidth(), drag.scaledHeight()
            ), false
        );
    }

    private record MountedWidget(
        WidgetDefinition<?, ?, ?> definition,
        WidgetView<?, ?, ?> view,
        io.wispforest.owo.ui.core.UIComponent component,
        WidgetSlotComponent slot,
        WidgetRenderSurface surface
    ) {}
    private record PreparedWidget(
        MountedWidget mounted,
        @Nullable WidgetRenderResult result,
        WidgetCanvas anchorCanvas
    ) {}
    private record RuntimeWidgetHit(WidgetRenderResult result, WidgetCanvas anchorCanvas) {}
    private record RuntimePlacementDrag(
        WidgetDefinition<?, ?, ?> definition,
        String placementProfile,
        WidgetCanvas anchorCanvas,
        double pointerOffsetX,
        double pointerOffsetY,
        int scaledWidth,
        int scaledHeight,
        int button
    ) {}
}
