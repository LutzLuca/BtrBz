package com.github.lutzluca.btrbz.core.widgets.manager;

import com.github.lutzluca.btrbz.core.widgets.WidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.WidgetHost;
import com.github.lutzluca.btrbz.core.widgets.WidgetId;
import com.github.lutzluca.btrbz.core.widgets.WidgetRegistry;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import com.github.lutzluca.btrbz.core.widgets.WidgetStateStore;
import com.github.lutzluca.btrbz.core.widgets.ui.ScrollSafeDiscreteSliderComponent;
import com.github.lutzluca.btrbz.core.widgets.ui.RestorableVerticalScrollContainer;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetChrome;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetColorFormat;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetSurfaces;
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
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class WidgetManagementScreen extends BaseOwoScreen<FlowLayout> {
    private static final int SIDEBAR_WIDTH = 222;
    private static final int MINIMIZED_SIDEBAR_WIDTH = 150;
    private static final int MINIMIZED_SIDEBAR_HEIGHT = 32;
    private static final int SIDEBAR_HEIGHT_PERCENT = 85;
    private static final int SIDEBAR_MARGIN = 18;
    private static final int SIDEBAR_PADDING = 7;
    private static final int HEADER_HEIGHT = 18;
    private static final double SCALE_STEP = 0.01;

    private final @Nullable Screen previousScreen;
    private final WidgetRegistry registry;
    private final WidgetStateStore stateStore;
    private final WidgetManagerEditSession editSession;
    private final WidgetHost previewHost;
    private final WidgetManagerPanelState sidebarPosition = new WidgetManagerPanelState();
    private final Set<WidgetId> renderedWidgets = new LinkedHashSet<>();
    private final Map<WidgetId, String> previewProfiles = new LinkedHashMap<>();

    private FlowLayout root;
    private ManagementPreviewComponent preview;
    private FlowLayout sidebar;
    private FlowLayout sidebarHeader;
    private FlowLayout sidebarContent;
    private RestorableVerticalScrollContainer<FlowLayout> sidebarScroller;
    private ButtonComponent sidebarSizeButton;
    private @Nullable WidgetId selectedWidget;
    private boolean sidebarMinimized;
    private boolean sidebarCapturedMouse;
    private double sidebarScrollOffset;

    public WidgetManagementScreen(
        @Nullable Screen previousScreen,
        WidgetRegistry registry,
        WidgetStateStore stateStore
    ) {
        this(previousScreen, registry, stateStore, WidgetManagementLaunchState.empty());
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
            WidgetManagementLaunchState.configure(initiallySelectedWidget)
        );
    }

    private WidgetManagementScreen(
        @Nullable Screen previousScreen,
        WidgetRegistry registry,
        WidgetStateStore stateStore,
        WidgetManagementLaunchState launchState
    ) {
        super(Component.literal("BtrBz Widgets"));
        this.previousScreen = previousScreen;
        this.registry = registry;
        this.stateStore = stateStore;
        this.editSession = new WidgetManagerEditSession(stateStore::save);
        this.previewHost = WidgetHost.preview(
            registry.all(),
            stateStore
        );
        if (launchState.selectedWidget() != null) {
            if (registry.find(launchState.selectedWidget()).isEmpty()) {
                throw new IllegalArgumentException("Unknown widget: " + launchState.selectedWidget());
            }
            this.selectedWidget = launchState.selectedWidget();
        }
        this.renderedWidgets.addAll(launchState.renderedWidgets());
        for (var definition : registry.all()) {
            this.previewProfiles.put(
                definition.getId(),
                WidgetSession.DEFAULT_PLACEMENT_PROFILE
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
        rootComponent.surface(Surface.flat(0x00000000));
        rootComponent.padding(Insets.none());
        rootComponent.gap(0);
        rootComponent.allowOverflow(false);
        this.preview = new ManagementPreviewComponent(this, this.previewHost);
        this.preview.positioning(Positioning.absolute(0, 0));
        rootComponent.child(this.preview);
        this.addSidebar();
    }

    WidgetStateStore stateStore() { return this.stateStore; }
    @Nullable WidgetId selectedWidget() { return this.selectedWidget; }
    Set<WidgetId> renderedWidgets() { return Set.copyOf(this.renderedWidgets); }
    Map<WidgetId, String> previewProfiles() { return Map.copyOf(this.previewProfiles); }
    void markDirty() { this.editSession.markDirty(); }

    String placementProfile(WidgetDefinition<?, ?, ?> definition) {
        return this.previewProfiles.getOrDefault(
            definition.getId(),
            WidgetSession.DEFAULT_PLACEMENT_PROFILE
        );
    }

    void selectWidget(WidgetId id) {
        if (id.equals(this.selectedWidget)) return;
        this.selectedWidget = id;
        this.rebuildSidebar();
    }

    void clearSelectedWidget() {
        if (this.selectedWidget == null) return;
        this.selectedWidget = null;
        this.rebuildSidebar();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.previousScreen);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
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
            if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
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
        if (input.key() == GLFW.GLFW_KEY_B && !input.hasControlDown() && !input.hasAltDown()) {
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
            this.sidebarMinimized ? Sizing.fixed(this.sidebarHeight()) : Sizing.fill(SIDEBAR_HEIGHT_PERCENT)
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
            minimized ? Sizing.fixed(this.sidebarHeight()) : Sizing.fill(SIDEBAR_HEIGHT_PERCENT)
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
        int preferredWidth = this.sidebarMinimized ? MINIMIZED_SIDEBAR_WIDTH : SIDEBAR_WIDTH;
        return Math.min(preferredWidth, Math.max(1, this.width - SIDEBAR_MARGIN * 2));
    }

    private int sidebarHeight() {
        return this.sidebarMinimized
            ? Math.min(MINIMIZED_SIDEBAR_HEIGHT, Math.max(1, this.height))
            : Math.round(this.height * SIDEBAR_HEIGHT_PERCENT / 100f);
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
        if (this.sidebarScroller != null) {
            this.sidebarScrollOffset = this.sidebarScroller.savedScrollOffset();
        }
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

        this.addBaseScale();
        this.sidebarContent.child(label("Widgets", 0xFFB8C0CF));
        this.sidebarContent.child(label("R: rendered here", 0xFF808997));
        var list = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        list.gap(4);
        this.registry.all().forEach(definition -> list.child(this.widgetRow(definition)));
        this.sidebarContent.child(list);

        var selected = this.selectedWidget == null ? null : this.registry.find(this.selectedWidget).orElse(null);
        this.sidebarContent.child(label("Selected", 0xFFB8C0CF));
        this.sidebarContent.child(label(selected == null ? "None" : selected.getDisplayName(), 0xFFFFFFFF));
        if (selected != null) {
            this.addGameplayEnabledControl(selected);
            this.addWidgetScaleControls(selected);
            this.addBackgroundControls(selected);
            this.sidebarContent.child(button("Reset position", _ -> {
                this.stateStore.resetPlacement(selected, this.placementProfile(selected), false);
                this.markDirty();
            }));
            this.sidebarContent.child(button("Reset all positions", _ -> {
                var binding = selected.binding(this::markDirty);
                var current = binding.frame();
                current.placements.clear();
                current.placements.putAll(binding.defaultFrame().placements);
                binding.markChanged();
            }));
            this.addPlacementProfileControl(selected);
            var configurationPanel = selected.settingsPanel(this::markDirty);
            if (configurationPanel != null) {
                this.sidebarContent.child(label("Content & behavior", 0xFFB8C0CF));
                this.sidebarContent.child(configurationPanel);
                this.sidebarContent.child(button("Reset content preferences", _ -> {
                    selected.binding(this::markDirty).resetPreferences();
                    this.rebuildSidebar();
                }));
            }
            this.sidebarContent.child(button("Reset all widget preferences", _ -> {
                selected.binding(this::markDirty).resetAll();
                this.rebuildSidebar();
            }));
        }
        this.sidebarContent.child(button("Close", _ -> this.onClose()));

        // Attach only after the replacement content is complete, otherwise the
        // mounted sidebar performs an empty layout and clamps the saved offset
        // before the settings controls have been added.
        this.sidebarScroller.restoreScrollOffset(this.sidebarScrollOffset);
        this.sidebar.child(this.sidebarScroller);
    }

    private FlowLayout createSidebarHeader() {
        this.sidebarHeader = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(HEADER_HEIGHT));
        this.sidebarHeader.gap(5);
        this.sidebarHeader.verticalAlignment(VerticalAlignment.CENTER);
        this.sidebarHeader.cursorStyle(CursorStyle.MOVE);

        var title = label("Widget Manager", 0xFFFFFFFF).shadow(true);
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
        this.sidebarSizeButton.tooltip(Component.literal(tooltip));
        this.sidebarHeader.child(this.sidebarSizeButton);
        return this.sidebarHeader;
    }

    private void addBaseScale() {
        var slider = new ScrollSafeDiscreteSliderComponent(
            Sizing.fill(100), WidgetStateStore.MIN_SCALE, WidgetStateStore.MAX_SCALE, SCALE_STEP
        );
        slider.decimalPlaces(2);
        slider.setFromDiscreteValue(this.stateStore.globalFineTuneScale());
        slider.message(value -> Component.literal("Base scale " + value));
        slider.onChanged().subscribe(value -> {
            this.stateStore.setGlobalFineTuneScale(value, false);
            this.markDirty();
        });
        this.sidebarContent.child(slider);
    }

    private void addPlacementProfileControl(WidgetDefinition<?, ?, ?> selected) {
        if (selected.placementProfileKeys().size() <= 1) return;
        String current = this.placementProfile(selected);
        this.sidebarContent.child(button("Placement: " + selected.placementProfileLabel(current), control -> {
            var profiles = selected.placementProfileKeys();
            int index = profiles.indexOf(this.placementProfile(selected));
            String next = profiles.get((index + 1) % profiles.size());
            this.previewProfiles.put(selected.getId(), next);
            control.setMessage(Component.literal("Placement: " + selected.placementProfileLabel(next)));
        }));
    }

    private void addWidgetScaleControls(WidgetDefinition<?, ?, ?> selected) {
        this.sidebarContent.child(label("Widget scale", 0xFFB8C0CF));
        var slider = new ScrollSafeDiscreteSliderComponent(
            Sizing.fill(100), WidgetStateStore.MIN_SCALE, WidgetStateStore.MAX_SCALE, SCALE_STEP
        );
        slider.decimalPlaces(2);
        slider.setFromDiscreteValue(this.stateStore.widgetScale(selected));
        slider.message(value -> Component.literal("Widget scale " + value));
        slider.onChanged().subscribe(value -> {
            this.stateStore.setWidgetScale(selected, value, false);
            this.markDirty();
        });
        this.sidebarContent.child(slider);
        this.sidebarContent.child(button("Reset widget scale", _ -> {
            this.stateStore.resetWidgetScale(selected, false);
            this.markDirty();
            this.rebuildSidebar();
        }));
    }

    private void addGameplayEnabledControl(WidgetDefinition<?, ?, ?> selected) {
        var enabled = UIComponents.smallCheckbox(Component.literal("Enable"));
        enabled.checked(this.stateStore.isActive(selected));
        enabled.onChanged().subscribe(value -> {
            this.stateStore.setActive(selected, value, false);
            this.markDirty();
        });
        this.sidebarContent.child(enabled);
    }

    private void addBackgroundControls(WidgetDefinition<?, ?, ?> selected) {
        this.sidebarContent.child(label("Background", 0xFFB8C0CF));
        this.addBackgroundEditor(selected, WidgetChrome.DEFAULT_BACKGROUND);
        this.sidebarContent.child(button("Reset background", _ -> {
            this.stateStore.resetBackgroundColor(selected, false);
            this.markDirty();
            this.rebuildSidebar();
        }));
    }

    private void addBackgroundEditor(
        WidgetDefinition<?, ?, ?> selected,
        int fallback
    ) {
        int value = this.stateStore.backgroundColor(selected, fallback);
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
        var synchronizing = new boolean[] {false};
        hex.onChanged().subscribe(text -> {
            int current = this.stateStore.backgroundColor(selected, fallback);
            var parsed = WidgetColorFormat.parse(text, current);
            hex.setTextColor(parsed.isPresent() ? 0xFFE8EDF5 : 0xFFE06C75);
            if (parsed.isEmpty() || synchronizing[0]) return;
            synchronizing[0] = true;
            this.stateStore.setBackgroundColor(selected, parsed.getAsInt(), false);
            this.markDirty();
            picker.selectedColor(Color.ofArgb(parsed.getAsInt()));
            synchronizing[0] = false;
        });
        picker.onChanged().subscribe(color -> {
            this.stateStore.setBackgroundColor(selected, color.argb(), false);
            this.markDirty();
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
        var rendered = UIComponents.smallCheckbox(Component.literal("R"));
        rendered.tooltip(Component.literal("Rendered in the widget manager"));
        rendered.checked(this.renderedWidgets.contains(definition.getId()));
        rendered.onChanged().subscribe(value -> {
            if (value) this.renderedWidgets.add(definition.getId());
            else this.renderedWidgets.remove(definition.getId());
        });
        row.child(rendered);
        var select = button(definition.getDisplayName(), _ -> this.selectWidget(definition.getId()));
        select.horizontalSizing(Sizing.expand(100));
        if (definition.getId().equals(this.selectedWidget)) {
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
