package com.github.lutzluca.btrbz.core.widgets.manager;

import com.github.lutzluca.btrbz.core.widgets.WidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.runtime.WidgetHost;
import com.github.lutzluca.btrbz.core.widgets.WidgetId;
import com.github.lutzluca.btrbz.core.widgets.WidgetRegistry;
import com.github.lutzluca.btrbz.core.widgets.layout.WidgetScaleResolver;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import com.github.lutzluca.btrbz.core.widgets.config.WidgetStateStore;
import com.github.lutzluca.btrbz.core.widgets.ui.ScrollSafeDiscreteSliderComponent;
import com.github.lutzluca.btrbz.core.widgets.ui.RestorableVerticalScrollContainer;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetColorFormat;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetSurfaces;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetTooltips;
import com.github.lutzluca.btrbz.core.widgets.ui.TooltipDelayState;
import com.mojang.blaze3d.platform.InputConstants;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.ColorPickerComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class WidgetManagementScreen extends BaseOwoScreen<FlowLayout> {
    private static final int MINIMIZED_SIDEBAR_WIDTH = 150;
    private static final int MINIMIZED_SIDEBAR_HEIGHT = 32;
    private static final int SIDEBAR_MARGIN = 18;
    private static final int SIDEBAR_PADDING = 7;
    private static final int HEADER_HEIGHT = 18;
    private static final double SCALE_STEP = 0.01;
    private static final long TOOLTIP_DELAY_MILLIS = 200;

    private final @Nullable Screen previousScreen;
    private final @Nullable AbstractContainerScreen<?> backgroundScreen;
    private final WidgetRegistry registry;
    private final WidgetStateStore stateStore;
    private final WidgetManagerEditSession editSession;
    private final WidgetHost previewHost;
    private final WidgetManagerSelectionState selectionState;
    private final WidgetManagerSidebarScrollState sidebarScrollState = new WidgetManagerSidebarScrollState();
    private final WidgetManagerPanelState sidebarPosition = new WidgetManagerPanelState();
    private final Map<WidgetId, String> previewProfiles = new LinkedHashMap<>();
    private final TooltipDelayState<UIComponent> tooltipDelay =
        new TooltipDelayState<>(TOOLTIP_DELAY_MILLIS);

    private FlowLayout root;
    private ManagementPreviewComponent preview;
    private FlowLayout sidebar;
    private FlowLayout sidebarHeader;
    private FlowLayout sidebarContent;
    private RestorableVerticalScrollContainer<FlowLayout> sidebarScroller;
    private ButtonComponent sidebarSizeButton;
    private boolean sidebarMinimized;
    private boolean sidebarCapturedMouse;
    private @Nullable WidgetId pendingResetConfirmation;

    public WidgetManagementScreen(
        @Nullable Screen previousScreen,
        WidgetRegistry registry,
        WidgetStateStore stateStore
    ) {
        this(previousScreen, registry, stateStore, WidgetManagementLaunchState.empty(), null);
    }

    public WidgetManagementScreen(
        @Nullable Screen previousScreen,
        WidgetRegistry registry,
        WidgetStateStore stateStore,
        WidgetId initiallySelectedWidget
    ) {
        this(
            previousScreen,
            registry,
            stateStore,
            WidgetManagementLaunchState.configure(initiallySelectedWidget),
            null
        );
    }

    public WidgetManagementScreen(
        @Nullable Screen previousScreen,
        WidgetRegistry registry,
        WidgetStateStore stateStore,
        WidgetManagementContext context
    ) {
        this(
            previousScreen,
            registry,
            stateStore,
            WidgetManagementLaunchState.contextual(context.initiallyRendered()),
            context
        );
    }

    public WidgetManagementScreen(
        @Nullable Screen previousScreen,
        WidgetRegistry registry,
        WidgetStateStore stateStore,
        WidgetId initiallySelectedWidget,
        WidgetManagementContext context
    ) {
        this(
            previousScreen,
            registry,
            stateStore,
            WidgetManagementLaunchState.contextual(context.initiallyRendered(), initiallySelectedWidget),
            context
        );
    }

    private WidgetManagementScreen(
        @Nullable Screen previousScreen,
        WidgetRegistry registry,
        WidgetStateStore stateStore,
        WidgetManagementLaunchState launchState,
        @Nullable WidgetManagementContext context
    ) {
        super(Component.literal("BtrBz Widgets"));
        this.previousScreen = previousScreen;
        this.backgroundScreen = context == null ? null : context.backgroundScreen();
        this.registry = registry;
        this.stateStore = stateStore;
        this.editSession = new WidgetManagerEditSession(stateStore::save);
        this.previewHost = context == null
            ? WidgetHost.preview(registry.all(), stateStore)
            : WidgetHost.preview(registry.all(), stateStore, context.frozenPreviews());
        if (launchState.selectedWidget() != null) {
            if (registry.find(launchState.selectedWidget()).isEmpty()) {
                throw new IllegalArgumentException("Unknown widget: " + launchState.selectedWidget());
            }
        }
        this.selectionState = new WidgetManagerSelectionState(launchState);
        for (var definition : registry.all()) {
            var frozenPreview = context == null
                ? null
                : context.frozenPreviews().get(definition.getId());
            this.previewProfiles.put(
                definition.getId(),
                frozenPreview == null
                    ? WidgetSession.DEFAULT_PLACEMENT_PROFILE
                    : frozenPreview.placementProfile()
            );
        }
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::horizontalFlow);
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        this.root = rootComponent;
        rootComponent.surface(Surface.flat(0x1A000000));
        rootComponent.padding(Insets.none());
        rootComponent.gap(0);
        rootComponent.allowOverflow(false);
        this.preview = new ManagementPreviewComponent(this, this.previewHost);
        this.preview.positioning(Positioning.absolute(0, 0));
        rootComponent.child(this.preview);
        this.addSidebar();
    }

    WidgetStateStore stateStore() { return this.stateStore; }
    @Nullable WidgetId selectedWidget() { return this.selectionState.selectedWidget(); }
    Set<WidgetId> renderedWidgets() { return this.selectionState.renderedWidgets(); }
    Map<WidgetId, String> previewProfiles() { return Map.copyOf(this.previewProfiles); }
    void markDirty() { this.editSession.markDirty(); }
    boolean hasBazaarBackground() { return this.backgroundScreen != null; }

    String placementProfile(WidgetDefinition<?, ?, ?> definition) {
        return this.previewProfiles.getOrDefault(
            definition.getId(),
            WidgetSession.DEFAULT_PLACEMENT_PROFILE
        );
    }

    void selectWidget(WidgetId id) {
        if (!id.equals(this.selectionState.selectedWidget())) this.sidebarScrollState.openDetail();
        this.pendingResetConfirmation = null;
        if (!this.selectionState.select(id)) return;
        this.rebuildSidebar();
    }

    void clearSelectedWidget() {
        this.pendingResetConfirmation = null;
        if (!this.selectionState.clearSelection()) return;
        this.rebuildSidebar();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.returnScreen());
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        if (this.backgroundScreen != null) {
            this.backgroundScreen.resize(width, height);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (this.backgroundScreen != null) {
            this.backgroundScreen.extractBackground(graphics, -10_000, -10_000, delta);
            graphics.nextStratum();
            this.backgroundScreen.extractContents(graphics, -10_000, -10_000, delta);
            graphics.nextStratum();
        }
        if (this.sidebar != null && this.sidebar.width() != this.sidebarWidth()) {
            this.sidebar.horizontalSizing(Sizing.fixed(this.sidebarWidth()));
        }
        if (this.sidebarMinimized && this.sidebar != null && this.sidebar.height() != this.sidebarHeight()) {
            this.sidebar.verticalSizing(Sizing.fixed(this.sidebarHeight()));
        }
        this.sidebarPosition.fitToViewport(
            this.width,
            this.height,
            this.sidebarWidth(),
            this.sidebarHeight(),
            SIDEBAR_MARGIN
        );
        this.applySidebarPosition();
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    protected void drawComponentTooltip(
        GuiGraphicsExtractor graphics,
        int mouseX,
        int mouseY,
        float delta
    ) {
        if (this.sidebarPosition.isDragging() || this.preview != null && this.preview.isDragging()) {
            this.tooltipDelay.reset();
            return;
        }
        UIComponent target = this.root == null ? null : this.root.childAt(mouseX, mouseY);
        if (this.tooltipDelay.ready(target, System.nanoTime())) {
            super.drawComponentTooltip(graphics, mouseX, mouseY, delta);
        }
    }

    private @Nullable Screen returnScreen() {
        if (this.backgroundScreen == null) return this.previousScreen;
        if (this.minecraft.player == null
            || this.minecraft.player.containerMenu != this.backgroundScreen.getMenu()) return null;
        return this.backgroundScreen;
    }

    @Override
    public void removed() {
        try {
            super.removed();
        } finally {
            try {
                this.editSession.close();
            } finally {
                this.previewHost.dispose();
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (this.isSidebarHit(click.x(), click.y())) {
            if (click.button() == InputConstants.MOUSE_BUTTON_LEFT
                && this.isSidebarHeaderHit(click.x(), click.y())
                && !this.isSidebarSizeButtonHit(click.x(), click.y())) {
                this.sidebarCapturedMouse = false;
                this.sidebarPosition.beginDrag(click.x(), click.y());
                return true;
            }

            this.sidebarCapturedMouse = true;
            super.mouseClicked(click, doubled);
            return true;
        }
        this.sidebarCapturedMouse = false;
        if (this.preview != null && this.preview.beginDrag(click.x(), click.y(), click.button())) return true;
        this.clearSelectedWidget();
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        if (this.sidebarPosition.isDragging()) {
            boolean handled = this.sidebarPosition.dragTo(
                click.x(),
                click.y(),
                this.width,
                this.height,
                this.sidebarWidth(),
                this.sidebarHeight()
            );
            this.applySidebarPosition();
            return handled;
        }
        if (this.sidebarCapturedMouse) return super.mouseDragged(click, deltaX, deltaY);
        if (this.preview != null && this.preview.isDragging()) return this.preview.dragTo(click.x(), click.y());
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (this.sidebarPosition.endDrag()) return true;
        if (this.sidebarCapturedMouse) {
            this.sidebarCapturedMouse = false;
            super.mouseReleased(click);
            return true;
        }
        if (this.preview != null && this.preview.endDrag()) return true;
        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (input.key() == InputConstants.KEY_ESCAPE) {
            if (this.selectionState.selectedWidget() != null) {
                this.clearSelectedWidget();
            } else {
                this.onClose();
            }
            return true;
        }
        if (input.key() == InputConstants.KEY_B && !input.hasControlDown() && !input.hasAltDown()) {
            this.setSidebarMinimized(!this.sidebarMinimized);
            return true;
        }
        return super.keyPressed(input);
    }

    private void addSidebar() {
        if (this.root == null) return;
        this.sidebarPosition.fitToViewport(
            this.width,
            this.height,
            this.sidebarWidth(),
            this.sidebarHeight(),
            SIDEBAR_MARGIN
        );
        this.sidebar = UIContainers.verticalFlow(
            Sizing.fixed(this.sidebarWidth()),
            this.sidebarMinimized
                ? Sizing.fixed(this.sidebarHeight())
                : Sizing.fill(this.sidebarHeightPercent())
        );
        this.sidebar.positioning(Positioning.absolute(this.sidebarPosition.x(), this.sidebarPosition.y()));
        this.sidebar.surface(WidgetSurfaces.roundedPanel(0xF0181B22, 6));
        this.sidebar.padding(Insets.of(SIDEBAR_PADDING));
        this.sidebar.gap(7);
        this.root.child(this.sidebar);
        this.rebuildSidebar();
    }

    private void setSidebarMinimized(boolean minimized) {
        if (this.sidebarMinimized == minimized) return;

        int oldWidth = this.sidebarWidth();
        this.sidebarMinimized = minimized;
        this.sidebarCapturedMouse = false;
        this.sidebarPosition.resizePanel(
            oldWidth,
            this.sidebarWidth(),
            this.width,
            this.height,
            this.sidebarHeight(),
            SIDEBAR_MARGIN
        );

        if (this.sidebar == null) return;
        this.sidebar.sizing(
            Sizing.fixed(this.sidebarWidth()),
            minimized
                ? Sizing.fixed(this.sidebarHeight())
                : Sizing.fill(this.sidebarHeightPercent())
        );
        this.applySidebarPosition();
        this.rebuildSidebar();
    }

    private boolean isSidebarHit(double absoluteX, double absoluteY) {
        return this.sidebar != null && this.sidebar.isInBoundingBox(absoluteX, absoluteY);
    }

    private boolean isSidebarHeaderHit(double absoluteX, double absoluteY) {
        return this.sidebarHeader != null && this.sidebarHeader.isInBoundingBox(absoluteX, absoluteY);
    }

    private boolean isSidebarSizeButtonHit(double absoluteX, double absoluteY) {
        return this.sidebarSizeButton != null && this.sidebarSizeButton.isInBoundingBox(absoluteX, absoluteY);
    }

    private int sidebarWidth() {
        int preferredWidth = this.sidebarMinimized
            ? MINIMIZED_SIDEBAR_WIDTH
            : this.expandedSidebarWidth();
        return Math.min(preferredWidth, Math.max(1, this.width - SIDEBAR_MARGIN * 2));
    }

    private int expandedSidebarWidth() {
        return WidgetManagerPanelState.configuredWidth(this.stateStore.managerPanelWidth());
    }

    private int sidebarHeightPercent() {
        return WidgetManagerPanelState.configuredHeightPercent(
            this.stateStore.managerPanelHeightPercent()
        );
    }

    private int sidebarHeight() {
        return this.sidebarMinimized
            ? Math.min(MINIMIZED_SIDEBAR_HEIGHT, Math.max(1, this.height))
            : Math.round(this.height * this.sidebarHeightPercent() / 100f);
    }

    private void applySidebarPosition() {
        if (this.sidebar == null) return;
        var target = Positioning.absolute(this.sidebarPosition.x(), this.sidebarPosition.y());
        if (!target.equals(this.sidebar.positioning().get())) {
            this.sidebar.positioning(target);
        }
    }

    private void rebuildSidebar() {
        if (this.sidebar == null) return;
        this.saveSidebarScrollOffset();
        this.sidebar.clearChildren();
        this.sidebarScroller = null;
        this.sidebarContent = null;
        this.sidebar.child(this.createSidebarHeader());
        if (this.sidebarMinimized) return;

        this.sidebarContent = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        this.sidebarContent.gap(7);
        this.sidebarContent.padding(Insets.right(8));
        this.sidebarContent.allowOverflow(true);
        this.sidebarScroller = new RestorableVerticalScrollContainer<>(
            Sizing.fill(100), Sizing.expand(100), this.sidebarContent
        );
        this.sidebarScroller.scrollbarThiccness(4);

        var selectedWidget = this.selectionState.selectedWidget();
        var selected = selectedWidget == null ? null : this.registry.find(selectedWidget).orElse(null);
        if (selected == null) {
            this.addOverviewControls();
        } else {
            this.addDetailControls(selected);
        }
        this.sidebarContent.child(button("Close", _ -> this.onClose()));

        // Attach only after the replacement content is complete, otherwise the
        // mounted sidebar performs an empty layout and clamps the saved offset
        // before the settings controls have been added.
        this.sidebarScroller.restoreScrollOffset(this.sidebarScrollState.mount(selected != null));
        this.sidebar.child(this.sidebarScroller);
    }

    private void saveSidebarScrollOffset() {
        if (this.sidebarScroller == null) return;
        this.sidebarScrollState.saveMountedOffset(this.sidebarScroller.savedScrollOffset());
    }

    private void addOverviewControls() {
        this.addGlobalAppearanceControls();
        this.sidebarContent.child(label("Widgets", 0xFFB8C0CF));
        if (this.hasBazaarBackground()) {
            this.sidebarContent.child(label("Visible content frozen at open", 0xFF808997));
        }
        this.sidebarContent.child(label("Preview controls manager visibility", 0xFF808997));
        var list = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        list.gap(4);
        this.registry.all().forEach(definition -> list.child(this.widgetRow(definition)));
        this.sidebarContent.child(list);
        this.addManagerControls();
    }

    private void addDetailControls(WidgetDefinition<?, ?, ?> selected) {
        this.sidebarContent.child(button("← Back", _ -> this.clearSelectedWidget()));
        this.sidebarContent.child(label(selected.getDisplayName(), 0xFFFFFFFF));

        this.sidebarContent.child(label("Availability", 0xFFB8C0CF));
        this.addGameplayEnabledControl(selected);

        this.sidebarContent.child(label("Appearance", 0xFFB8C0CF));
        this.addWidgetScaleControls(selected);
        this.addBackgroundControls(selected);

        this.sidebarContent.child(label("Placement", 0xFFB8C0CF));
        this.addPlacementProfileControl(selected);
        var resetPosition = button("Reset this profile position", _ -> {
            this.stateStore.resetPlacement(selected, this.placementProfile(selected), false);
            this.markDirty();
        });
        resetPosition.tooltip(WidgetTooltips.wrapped(
            "Restores only the selected placement profile to its default position."
        ));
        this.sidebarContent.child(resetPosition);

        var configurationPanel = selected.settingsPanel(this::markDirty);
        if (configurationPanel != null) {
            this.sidebarContent.child(label("Content & behavior", 0xFFB8C0CF));
            this.sidebarContent.child(configurationPanel);
            var resetContent = button("Reset content settings", _ -> {
                selected.binding(this::markDirty).resetPreferences();
                this.rebuildSidebar();
            });
            resetContent.tooltip(WidgetTooltips.wrapped(
                "Restores this widget's content settings while preserving placement and appearance."
            ));
            this.sidebarContent.child(resetContent);
        }

        this.addEntireWidgetReset(selected);
    }

    private void addEntireWidgetReset(WidgetDefinition<?, ?, ?> selected) {
        if (!selected.getId().equals(this.pendingResetConfirmation)) {
            var reset = button("Reset entire widget…", _ -> {
                this.pendingResetConfirmation = selected.getId();
                this.rebuildSidebar();
            });
            reset.tooltip(WidgetTooltips.wrapped(
                "Restores enabled state, appearance, every placement profile, and all content settings."
            ));
            this.sidebarContent.child(reset);
            return;
        }

        this.sidebarContent.child(label("Reset every setting for this widget?", 0xFFE06C75));
        this.sidebarContent.child(button("Confirm entire reset", _ -> {
            this.stateStore.resetAll(selected, false);
            this.markDirty();
            this.pendingResetConfirmation = null;
            this.rebuildSidebar();
        }));
        this.sidebarContent.child(button("Cancel", _ -> {
            this.pendingResetConfirmation = null;
            this.rebuildSidebar();
        }));
    }

    private void addManagerControls() {
        this.sidebarContent.child(label("Widget Manager", 0xFFB8C0CF));

        var launcherVisible = UIComponents.smallCheckbox(
            Component.literal("Show quick-access button")
        );
        launcherVisible.checked(this.stateStore.managerLauncherVisible());
        launcherVisible.tooltip(WidgetTooltips.wrapped(
            "Shows the draggable widget manager button on supported Bazaar screens. "
                + "Its size follows the global widget scale."
        ));
        launcherVisible.onChanged().subscribe(value -> {
            this.stateStore.setManagerLauncherVisible(value, false);
            this.markDirty();
        });
        this.sidebarContent.child(launcherVisible);

        var width = new ScrollSafeDiscreteSliderComponent(
            Sizing.fill(100),
            WidgetManagerPanelState.MINIMUM_WIDTH,
            WidgetManagerPanelState.MAXIMUM_WIDTH
        );
        width.decimalPlaces(0);
        width.setFromDiscreteValue(this.expandedSidebarWidth());
        width.message(value -> Component.literal("Panel width " + value));
        width.onChanged().subscribe(value -> this.resizeSidebar(
            (int) Math.round(value),
            this.sidebarHeightPercent()
        ));
        width.tooltip(WidgetTooltips.wrapped("Changes the width of the widget manager panel."));
        this.sidebarContent.child(width);

        var height = new ScrollSafeDiscreteSliderComponent(
            Sizing.fill(100),
            WidgetManagerPanelState.MINIMUM_HEIGHT_PERCENT,
            WidgetManagerPanelState.MAXIMUM_HEIGHT_PERCENT
        );
        height.decimalPlaces(0);
        height.setFromDiscreteValue(this.sidebarHeightPercent());
        height.message(value -> Component.literal("Panel height " + value + "%"));
        height.onChanged().subscribe(value -> this.resizeSidebar(
            this.expandedSidebarWidth(),
            (int) Math.round(value)
        ));
        height.tooltip(WidgetTooltips.wrapped("Changes how much of the screen height the manager panel can use."));
        this.sidebarContent.child(height);

        var runtimeDragging = UIComponents.smallCheckbox(Component.literal("Enable runtime Alt-dragging"));
        runtimeDragging.checked(this.stateStore.runtimeDragging());
        runtimeDragging.tooltip(WidgetTooltips.wrapped(
            "Convenience feature only; not recommended for regular use because interactions may behave unexpectedly."
        ));
        runtimeDragging.onChanged().subscribe(value -> {
            this.stateStore.setRuntimeDragging(value, false);
            this.markDirty();
        });
        this.sidebarContent.child(runtimeDragging);
        this.sidebarContent.child(label("Convenience feature only.", 0xFF808997));
        this.sidebarContent.child(label("Not advised for regular use;", 0xFF808997));
        this.sidebarContent.child(label("interactions may be unexpected.", 0xFF808997));
    }

    private void resizeSidebar(int panelWidth, int panelHeightPercent) {
        int oldWidth = this.sidebarWidth();
        this.stateStore.setManagerPanelWidth(panelWidth, false);
        this.stateStore.setManagerPanelHeightPercent(panelHeightPercent, false);
        this.markDirty();
        this.sidebarPosition.resizePanel(
            oldWidth,
            this.sidebarWidth(),
            this.width,
            this.height,
            this.sidebarHeight(),
            SIDEBAR_MARGIN
        );
        if (this.sidebar == null) return;
        this.sidebar.sizing(
            Sizing.fixed(this.sidebarWidth()),
            Sizing.fill(this.sidebarHeightPercent())
        );
        this.applySidebarPosition();
    }

    private FlowLayout createSidebarHeader() {
        this.sidebarHeader = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(HEADER_HEIGHT));
        this.sidebarHeader.gap(5);
        this.sidebarHeader.verticalAlignment(VerticalAlignment.CENTER);
        this.sidebarHeader.cursorStyle(CursorStyle.MOVE);

        var selectedId = this.selectionState.selectedWidget();
        String titleText = selectedId == null
            ? "Widget Manager"
            : this.registry.find(selectedId).map(WidgetDefinition::getDisplayName).orElse("Widget");
        var title = label(titleText, 0xFFFFFFFF).shadow(true);
        title.horizontalSizing(Sizing.expand(100));
        title.cursorStyle(CursorStyle.MOVE);
        this.sidebarHeader.child(title);

        String buttonText = this.sidebarMinimized ? "+" : "-";
        String tooltip = this.sidebarMinimized ? "Maximize widget manager" : "Minimize widget manager";
        this.sidebarSizeButton = UIComponents.button(
            Component.literal(buttonText),
            _ -> this.setSidebarMinimized(!this.sidebarMinimized)
        );
        this.sidebarSizeButton.renderer(ButtonComponent.Renderer.flat(0xFF2C3340, 0xFF465066, 0xFF20242D));
        this.sidebarSizeButton.textShadow(false);
        this.sidebarSizeButton.sizing(Sizing.fixed(HEADER_HEIGHT), Sizing.fixed(HEADER_HEIGHT));
        this.sidebarSizeButton.tooltip(WidgetTooltips.wrapped(tooltip));
        this.sidebarHeader.child(this.sidebarSizeButton);
        return this.sidebarHeader;
    }

    private void addGlobalAppearanceControls() {
        this.sidebarContent.child(label("Global appearance", 0xFFB8C0CF));
        var slider = new ScrollSafeDiscreteSliderComponent(
            Sizing.fill(100), WidgetScaleResolver.MIN_SCALE, WidgetScaleResolver.MAX_SCALE, SCALE_STEP
        );
        slider.decimalPlaces(2);
        slider.setFromDiscreteValue(this.stateStore.globalFineTuneScale());
        slider.message(value -> Component.literal("Global scale " + value));
        slider.onChanged().subscribe(value -> {
            this.stateStore.setGlobalFineTuneScale(value, false);
            this.markDirty();
        });
        slider.tooltip(WidgetTooltips.wrapped(
            "Fine-tunes the scale of every widget unless that widget has its own scale override."
        ));
        this.sidebarContent.child(slider);
        this.sidebarContent.child(label("Global background", 0xFF808997));
        this.addColorEditor(
            this.stateStore::globalBackgroundColor,
            color -> {
                this.stateStore.setGlobalBackgroundColor(color, false);
                this.markDirty();
            }
        );
    }

    private void addPlacementProfileControl(WidgetDefinition<?, ?, ?> selected) {
        if (selected.placementProfileKeys().size() <= 1) return;
        String current = this.placementProfile(selected);
        var control = button("Placement: " + selected.placementProfileLabel(current), button -> {
            var profiles = selected.placementProfileKeys();
            int index = profiles.indexOf(this.placementProfile(selected));
            String next = profiles.get((index + 1) % profiles.size());
            this.previewProfiles.put(selected.getId(), next);
            button.setMessage(Component.literal("Placement: " + selected.placementProfileLabel(next)));
        });
        control.tooltip(WidgetTooltips.wrapped(
            "Cycles through the screen-specific placements available for this widget."
        ));
        this.sidebarContent.child(control);
    }

    private void addWidgetScaleControls(WidgetDefinition<?, ?, ?> selected) {
        var override = UIComponents.smallCheckbox(Component.literal("Override scale"));
        override.checked(this.stateStore.hasWidgetScaleOverride(selected));
        override.tooltip(WidgetTooltips.wrapped(
            "Uses a scale for this widget instead of the global widget scale."
        ));
        override.onChanged().subscribe(value -> {
            this.stateStore.setWidgetScaleOverride(selected, value, false);
            this.markDirty();
            this.rebuildSidebar();
        });
        this.sidebarContent.child(override);
        if (!this.stateStore.hasWidgetScaleOverride(selected)) return;

        var slider = new ScrollSafeDiscreteSliderComponent(
            Sizing.fill(100), WidgetScaleResolver.MIN_SCALE, WidgetScaleResolver.MAX_SCALE, SCALE_STEP
        );
        slider.decimalPlaces(2);
        slider.setFromDiscreteValue(this.stateStore.widgetScale(selected));
        slider.message(value -> Component.literal("Widget scale " + value));
        slider.onChanged().subscribe(value -> {
            this.stateStore.setWidgetScale(selected, value, false);
            this.markDirty();
        });
        slider.tooltip(WidgetTooltips.wrapped("Changes only this widget's text, icon, width, and height scale."));
        this.sidebarContent.child(slider);
    }

    private void addGameplayEnabledControl(WidgetDefinition<?, ?, ?> selected) {
        var enabled = UIComponents.smallCheckbox(Component.literal("Enable"));
        enabled.checked(this.stateStore.isActive(selected));
        enabled.tooltip(WidgetTooltips.wrapped(
            "Controls whether this widget appears during normal gameplay. Its manager preview is independent."
        ));
        enabled.onChanged().subscribe(value -> {
            this.stateStore.setActive(selected, value, false);
            this.markDirty();
        });
        this.sidebarContent.child(enabled);
    }

    private void addBackgroundControls(WidgetDefinition<?, ?, ?> selected) {
        var override = UIComponents.smallCheckbox(Component.literal("Override background"));
        override.checked(this.stateStore.hasBackgroundOverride(selected));
        override.tooltip(WidgetTooltips.wrapped(
            "Uses a background color for this widget instead of the global widget background."
        ));
        override.onChanged().subscribe(value -> {
            this.stateStore.setBackgroundOverride(selected, value, false);
            this.markDirty();
            this.rebuildSidebar();
        });
        this.sidebarContent.child(override);
        if (!this.stateStore.hasBackgroundOverride(selected)) return;

        this.addColorEditor(
            () -> this.stateStore.backgroundColor(selected),
            color -> {
                this.stateStore.setBackgroundColor(selected, color, false);
                this.markDirty();
            }
        );
    }

    private void addColorEditor(IntSupplier currentColor, IntConsumer changeColor) {
        int value = currentColor.getAsInt();
        var picker = new ColorPickerComponent();
        picker.sizing(Sizing.fill(100), Sizing.fixed(72));
        picker.selectorWidth(12);
        picker.selectorPadding(5);
        picker.showAlpha(true);
        picker.selectedColor(Color.ofArgb(value));
        var hex = UIComponents.textBox(Sizing.fill(100));
        hex.setMaxLength(9);
        hex.setFilter(text -> text.matches("#?[0-9a-fA-F]{0,8}"));
        hex.text(WidgetColorFormat.formatArgb(value));
        hex.tooltip(WidgetTooltips.wrapped(
            "ARGB color in hexadecimal form: alpha, red, green, and blue. Alpha controls opacity."
        ));
        picker.tooltip(WidgetTooltips.wrapped(
            "Selects the color and opacity used by the widget background."
        ));
        var synchronizing = new boolean[] {false};
        hex.onChanged().subscribe(text -> {
            int current = currentColor.getAsInt();
            var parsed = WidgetColorFormat.parse(text, current);
            hex.setTextColor(parsed.isPresent() ? 0xFFE8EDF5 : 0xFFE06C75);
            if (parsed.isEmpty() || synchronizing[0]) return;
            synchronizing[0] = true;
            changeColor.accept(parsed.getAsInt());
            picker.selectedColor(Color.ofArgb(parsed.getAsInt()));
            synchronizing[0] = false;
        });
        picker.onChanged().subscribe(color -> {
            changeColor.accept(color.argb());
            if (synchronizing[0]) return;
            synchronizing[0] = true;
            hex.setValue(WidgetColorFormat.formatArgb(color.argb()));
            synchronizing[0] = false;
        });
        this.sidebarContent.child(label("Hex (#AARRGGBB)", 0xFF808997));
        this.sidebarContent.child(hex);
        this.sidebarContent.child(picker);
    }

    private FlowLayout widgetRow(WidgetDefinition<?, ?, ?> definition) {
        var row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.gap(5);
        row.verticalAlignment(VerticalAlignment.CENTER);
        var rendered = UIComponents.smallCheckbox(Component.literal("Preview"));
        rendered.tooltip(WidgetTooltips.wrapped(
            "Controls whether this widget is rendered on the manager canvas. This does not enable it in gameplay."
        ));
        rendered.checked(this.selectionState.renderedWidgets().contains(definition.getId()));
        rendered.onChanged().subscribe(value -> this.selectionState.setRendered(definition.getId(), value));
        row.child(rendered);
        var select = button(definition.getDisplayName(), _ -> this.selectWidget(definition.getId()));
        select.horizontalSizing(Sizing.expand(100));
        if (definition.getId().equals(this.selectionState.selectedWidget())) {
            select.renderer(ButtonComponent.Renderer.flat(0xFF3B4252, 0xFF465066, 0xFF292D36));
        }
        row.child(select);
        return row;
    }

    private static LabelComponent label(String text, int color) {
        return UIComponents.label(Component.literal(text)).color(Color.ofArgb(color));
    }

    private static ButtonComponent button(String text, java.util.function.Consumer<ButtonComponent> onPress) {
        var button = UIComponents.button(Component.literal(text), onPress);
        button.renderer(ButtonComponent.Renderer.flat(0xFF2C3340, 0xFF384252, 0xFF20242D));
        button.textShadow(false);
        button.sizing(Sizing.fill(100), Sizing.fixed(20));
        return button;
    }
}
