package com.github.lutzluca.btrbz.core.appearance;

import com.github.lutzluca.btrbz.core.ui.RestorableVerticalScrollContainer;
import com.github.lutzluca.btrbz.core.widgets.WidgetRegistry;
import com.github.lutzluca.btrbz.core.ui.UiSurfaces;
import com.github.lutzluca.btrbz.core.ui.UiComponents;
import com.github.lutzluca.btrbz.core.ui.UiButton;
import com.github.lutzluca.btrbz.core.ui.UiStyles;
import com.github.lutzluca.btrbz.core.widgets.ui.ScrollSafeDiscreteSliderComponent;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.mojang.blaze3d.platform.InputConstants;
import dev.isxander.yacl3.api.Option;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ColorPickerComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextAreaComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;
import java.util.EnumMap;
import java.util.Arrays;
import java.util.Map;
import java.util.Locale;
import java.util.LinkedHashMap;
import io.vavr.control.Try;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A draft editor with a stable preview and bounded, dismissible sharing dialogs. */
public final class AppearanceScreen extends BaseOwoScreen<FlowLayout> {
    private final @Nullable Screen parent;
    private final AppearanceManager manager;
    private UiTheme draft;
    private final WidgetRegistry widgets;
    private AppearancePreview preview;
    private boolean previewDirty;
    private boolean previewView;
    private RestorableVerticalScrollContainer<AppearancePreview> previewScroll;
    private RestorableVerticalScrollContainer<FlowLayout> controlsScroll;
    private final @Nullable Option<Integer> highlightOpacityOption;
    private boolean colorsView;
    private UiColor.Group group = UiColor.Group.Interface;
    private FlowLayout controls;
    private int controlWidth;
    private final Map<UiColor, TextBoxComponent> colorFields = new EnumMap<>(UiColor.class);
    private @Nullable UIComponent returnFocus;
    private @Nullable TextBoxComponent nameField;
    private UiButton applyButton;
    private final Map<UiTheme, UiButton> themeButtons = new LinkedHashMap<>();
    private LabelComponent currentTheme;
    private @Nullable RestorableVerticalScrollContainer<FlowLayout> themeScroll;

    public AppearanceScreen(
        @Nullable Screen parent,
        AppearanceManager manager,
        @Nullable Option<Integer> highlightOpacityOption,
        WidgetRegistry widgets
    ) {
        super(Component.literal("BtrBz Appearance"));
        this.parent = parent;
        this.manager = manager;
        this.draft = manager.theme();
        this.widgets = widgets;
        this.highlightOpacityOption = highlightOpacityOption;
        if (highlightOpacityOption != null) {
            this.draft = this.draft.withOrderHighlightOpacity(highlightOpacityOption.pendingValue());
        }
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout root) {
        int width = Math.max(180, Math.min(700, this.width - 24));
        int height = Math.max(130, Math.min(420, this.height - 24));
        boolean compact = width < 500;
        this.controlWidth = compact ? width - 24 : Math.min(250, (width - 40) * 42 / 100);
        root.surface(Surface.flat(UiStyles.palette().screenBackdrop()));
        root.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        var panel = UIContainers.verticalFlow(Sizing.fixed(width), Sizing.fixed(height));
        panel.padding(Insets.of(12));
        panel.gap(8);
        panel.surface((graphics, component) -> UiSurfaces.drawRoundedPanel(graphics,
            component.x(), component.y(), component.width(), component.height(),
            UiStyles.palette().panelBackground(), 6));
        panel.child(row(UiComponents.boldLabel("Appearance", UiStyles.palette().textPrimary()),
            UiComponents.spacer(), UiButton.text("×", this::onClose)));

        var workspace = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.expand(100));
        workspace.gap(16);
        var left = UIContainers.verticalFlow(Sizing.fixed(this.controlWidth), Sizing.fill(100));
        left.gap(8);
        var tabs = row(this.tab("Basics", false), this.tab("Colors", true));
        if (compact) {
            var previewTab = UiButton.text("Preview", () -> {
                this.previewView = true;
                this.rebuild();
            }).selected(this.previewView);
            tabs.child(previewTab);
        }
        int tabWidth = (this.controlWidth - 5 * (tabs.children().size() - 1)) / tabs.children().size();
        tabs.children().forEach(tab -> tab.horizontalSizing(Sizing.fixed(tabWidth)));
        left.child(tabs);
        this.controls = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        this.controls.gap(6);
        this.controls.padding(Insets.right(5));
        this.controlsScroll = new RestorableVerticalScrollContainer<>(Sizing.fill(100), Sizing.expand(100),
            this.controls);
        this.controlsScroll.scrollbarThiccness(3);
        this.controlsScroll.scrollbar(scrollbar());

        int previewWidth = compact ? this.controlWidth : width - 40 - this.controlWidth;
        this.preview = new AppearancePreview(() -> this.draft,
            this.widgets, previewWidth - 16);
        this.previewDirty = false;
        this.previewScroll = new RestorableVerticalScrollContainer<>(Sizing.fill(100), Sizing.expand(100),
            this.preview);
        this.previewScroll.id("appearance-preview");
        this.previewScroll.scrollbarThiccness(3);
        this.previewScroll.scrollbar(scrollbar());
        this.previewScroll.padding(Insets.of(8));
        this.previewScroll.surface(UiSurfaces.roundedPanel(0xFF25272C, 4));
        if (compact) {
            left.child(this.previewView ? this.previewScroll : this.controlsScroll);
            workspace.child(left);
        } else {
            left.child(this.controlsScroll);
            workspace.child(left);
            var previewColumn = UIContainers.verticalFlow(Sizing.expand(100), Sizing.fill(100));
            previewColumn.gap(8);
            previewColumn.child(UiComponents.label("Live preview", UiStyles.palette().textSecondary()));
            previewColumn.child(this.previewScroll);
            workspace.child(previewColumn);
        }
        panel.child(workspace);

        var importButton = UiButton.text("Import", () -> this.openSharing(true));
        var exportButton = UiButton.text("Export", () -> this.openSharing(false));
        this.applyButton = UiButton.text("Apply", () -> {
            this.manager.apply(this.draft);
            if (this.highlightOpacityOption != null) {
                this.highlightOpacityOption.forgetPendingValue();
            }
            this.rebuild();
        }).primary(true);
        panel.child(
            row(importButton, exportButton, UiComponents.spacer(), UiButton.text("Close", this::onClose),
                this.applyButton));
        root.child(panel);
        this.buildControls();
    }

    private UiButton tab(String label, boolean colors) {
        var button = UiButton.text(label, () -> {
            this.colorsView = colors;
            this.previewView = false;
            this.rebuild();
        }).selected(!this.previewView && this.colorsView == colors);
        return button;
    }

    private void buildControls() {
        double themeOffset = this.themeScroll == null ? 0 : this.themeScroll.savedScrollOffset();
        if (this.uiAdapter.rootComponent.focusHandler() != null) {
            this.uiAdapter.rootComponent.focusHandler().focus(null, UIComponent.FocusSource.MOUSE_CLICK);
        }
        this.controls.clearChildren();
        this.colorFields.clear();
        this.themeButtons.clear();
        this.currentTheme = null;
        this.nameField = null;
        if (this.colorsView) {
            this.addNameField();
            var groups = row();
            for (var value : UiColor.Group.values()) {
                var button = UiButton.text(value.name(), () -> {
                    this.group = value;
                    this.controlsScroll.restoreScrollOffset(0);
                    this.buildControls();
                }).selected(this.group == value);
                button.horizontalSizing(Sizing.fixed((this.controlWidth - 15) / 3));
                groups.child(button);
            }
            this.controls.child(groups);
            for (var role : UiColor.values()) {
                if (role.group() == this.group) {
                    this.addColor(role);
                }
            }
            this.updateApplyState();
            return;
        }

        var saveTheme = UiButton.text("Save theme", () -> {
            this.returnFocus = null;
            GameUtils.setScreen(new SaveThemeDialog(this));
        });
        this.controls.child(row(UiComponents.label("Themes", UiStyles.palette().textSecondary()),
            UiComponents.spacer(), saveTheme));
        var themes = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        themes.gap(5);
        themes.padding(Insets.right(5));
        this.themeButtons.clear();
        for (var preset : ThemePreset.values()) {
            themes.child(this.themeButton(preset.theme()));
        }
        for (var theme : this.manager.savedThemes()) {
            var remove = UiButton.text("×", () -> {
                this.manager.removeTheme(theme.name());
                this.buildControls();
            });
            remove.sizing(Sizing.fixed(22), Sizing.fixed(22));
            remove.tooltip(Component.literal("Remove saved theme"));
            var button = this.themeButton(theme);
            button.horizontalSizing(Sizing.expand(100));
            themes.child(row(button, remove));
        }
        var themeScroll = new RestorableVerticalScrollContainer<>(Sizing.fill(100), Sizing.fixed(112), themes);
        this.themeScroll = themeScroll;
        themeScroll.restoreScrollOffset(themeOffset);
        themeScroll.id("saved-theme-list");
        themeScroll.scrollbarThiccness(3);
        themeScroll.scrollbar(scrollbar());
        this.controls.child(themeScroll);
        this.currentTheme = UiComponents.label("Current: " + this.draft.name(), UiStyles.palette().textSecondary());
        this.currentTheme.maxWidth(this.controlWidth - 5);
        this.controls.child(this.currentTheme);
        this.controls.child(UiComponents.label("Panel opacity", UiStyles.palette().textSecondary()));
        var opacity = new ScrollSafeDiscreteSliderComponent(Sizing.fill(100), 0, 100, 1);
        opacity.decimalPlaces(0);
        opacity.setFromDiscreteValue(this.draft.palette().panelOpacity() * 100);
        opacity.message(value -> Component.literal(value + "%"));
        opacity.onChanged().subscribe(value -> this.editPalette(this.draft.palette().withOpacity(value / 100)));
        this.controls.child(opacity);
        this.controls.child(UiComponents.label("Order highlight opacity", UiStyles.palette().textSecondary()));
        var highlightOpacity = new ScrollSafeDiscreteSliderComponent(Sizing.fill(100), 0, 100, 1);
        highlightOpacity.decimalPlaces(0);
        highlightOpacity.setFromDiscreteValue(this.draft.orderHighlightOpacity());
        highlightOpacity.message(value -> Component.literal(value + "%"));
        highlightOpacity.onChanged()
            .subscribe(value -> this.editTheme(this.draft.withOrderHighlightOpacity((int) Math.round(value))));
        highlightOpacity.tooltip(Component.literal("Opacity of order slot backgrounds on the Bazaar Orders page."));
        this.controls.child(highlightOpacity);
        this.controls.child(UiComponents.label("Product names", UiStyles.palette().textSecondary()));
        var formatted = UIComponents.smallCheckbox(Component.literal("Native Hypixel formatting"));
        formatted.checked(this.draft.formattedProductNames());
        this.controls.child(formatted);
        this.controls.child(UiComponents.label("Product color strength", UiStyles.palette().textSecondary()));
        var strength = new ScrollSafeDiscreteSliderComponent(Sizing.fill(100), 0, 100, 1);
        strength.decimalPlaces(0);
        strength.setFromDiscreteValue(this.draft.productColorStrength());
        strength.message(value -> Component.literal(value + "%"));
        strength.active(this.draft.formattedProductNames());
        strength.tooltip(Component.literal("100% keeps native rarity colors. Lower values blend toward secondary text. "
            + "Applies to BtrBz product names, including chat."));
        strength.onChanged().subscribe(value -> {
            this.editTheme(this.draft.withProductNames(this.draft.formattedProductNames(), (int) Math.round(value)));
        });
        formatted.onChanged().subscribe(value -> {
            this.editTheme(this.draft.withProductNames(value, this.draft.productColorStrength()));
            strength.active(value);
            this.previewDirty = true;
        });
        this.controls.child(strength);
        this.updateApplyState();
    }

    private UiButton themeButton(UiTheme theme) {
        var button = UiButton.text(theme.name(), () -> {
            this.draft = theme;
            this.previewDirty = true;
            this.buildControls();
        }).selected(theme.equals(this.draft));
        button.horizontalSizing(Sizing.fill(100));
        this.themeButtons.put(theme, button);
        return button;
    }

    private void addNameField() {
        this.controls.child(UiComponents.label("Theme name", UiStyles.palette().textSecondary()));
        var name = UIComponents.textBox(Sizing.fill(100));
        this.nameField = name;
        name.setMaxLength(64);
        name.text(this.draft.name());
        name.onChanged().subscribe(value -> {
            boolean valid = !value.isBlank() && value.chars().noneMatch(Character::isISOControl);
            name.setTextColor(valid ? UiStyles.palette().textPrimary() : UiStyles.palette().error());
            if (valid) {
                this.draft = this.draft.withName(value);
                this.previewDirty = true;
            }
            this.updateApplyState();
        });
        this.controls.child(name);
    }

    private void addColor(UiColor role) {
        this.controls.child(UiComponents.label(role.label(), UiStyles.palette().textSecondary()));
        var field = UIComponents.textBox(Sizing.expand(100));
        field.setMaxLength(7);
        field.setFilter(value -> value.matches("#?[0-9a-fA-F]{0,6}"));
        field.text(ThemeJson.formatColor(role.color(this.draft.palette())));
        var picker = UiButton.text(Component.literal("■").withStyle(UiStyles.color(role.color(this.draft.palette()))),
            _ -> {
                this.returnFocus = field;
                GameUtils.setScreen(new ColorDialog(this, role));
            });
        picker.sizing(Sizing.fixed(24), Sizing.fixed(22));
        picker.accent(() -> role.color(this.draft.palette()));
        picker.tooltip(Component.literal("Choose " + role.label().toLowerCase(Locale.ROOT)));
        field.onChanged().subscribe(value -> {
            var parsed = ThemeJson.parseColor(value);
            field.setTextColor(parsed.isSuccess() ? UiStyles.palette().textPrimary() : UiStyles.palette().error());
            parsed.onSuccess(color -> {
                this.editPalette(this.draft.palette().withColor(role, color));
                picker.setMessage(Component.literal("■").withStyle(UiStyles.color(color)));
            });
            this.updateApplyState();
        });
        this.controls.child(row(field, picker));
        this.colorFields.put(role, field);
    }

    private void updateApplyState() {
        boolean validName = this.nameField == null || !this.nameField.getValue().isBlank()
            && this.nameField.getValue().chars().noneMatch(Character::isISOControl);
        boolean validColors = this.colorFields.values().stream()
            .allMatch(field -> ThemeJson.parseColor(field.getValue()).isSuccess());
        boolean changed = !this.draft.equals(this.manager.theme());
        this.applyButton.setMessage(Component.literal(changed ? "Apply" : "Applied"));
        this.applyButton.active(validName && validColors && changed);
        this.themeButtons.forEach((theme, button) -> button.selected(theme.equals(this.draft)));
        if (this.currentTheme != null) {
            this.currentTheme.text(Component.literal("Current: " + this.draft.name()));
        }
    }

    private void editPalette(UiPalette palette) {
        this.editTheme(this.draft.withPalette(palette));
    }

    private void editTheme(UiTheme theme) {
        if (!theme.equals(this.draft)) {
            boolean preset = Arrays.stream(ThemePreset.values()).anyMatch(value -> value.theme().equals(this.draft));
            this.draft = preset ? theme.withName("Custom") : theme;
            this.previewDirty = true;
            if (this.nameField != null && !this.nameField.getValue().equals(this.draft.name())) {
                this.nameField.setValue(this.draft.name());
            }
            this.updateApplyState();
        }
    }

    private void openSharing(boolean importing) {
        this.returnFocus = this.uiAdapter.rootComponent.focusHandler().focused();
        GameUtils.setScreen(new SharingDialog(this, importing));
    }

    private void rebuild() {
        this.uiAdapter.rootComponent.focusHandler().focus(null, UIComponent.FocusSource.MOUSE_CLICK);
        double offset = this.previewScroll.savedScrollOffset();
        double controlOffset = this.controlsScroll.savedScrollOffset();
        this.preview.closeViews();
        this.uiAdapter.rootComponent.clearChildren();
        this.build(this.uiAdapter.rootComponent);
        this.previewScroll.restoreScrollOffset(offset);
        this.controlsScroll.restoreScrollOffset(controlOffset);
    }

    private void resumeDialog() {
        GameUtils.setScreen(this);
        if (this.returnFocus != null) {
            this.uiAdapter.rootComponent.focusHandler().focus(this.returnFocus, UIComponent.FocusSource.MOUSE_CLICK);
        }
        this.returnFocus = null;
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        this.rebuild();
    }

    @Override
    public void onClose() {
        GameUtils.setScreen(this.parent);
    }

    private static FlowLayout row(UIComponent... children) {
        var row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.gap(5);
        row.verticalAlignment(VerticalAlignment.CENTER);
        for (var child : children) {
            row.child(child);
        }
        return row;
    }

    private static ScrollContainer.Scrollbar scrollbar() {
        return (graphics, x, y, width, height, trackX, trackY, trackWidth, trackHeight, _, _, active) -> {
            if (active) {
                var palette = UiStyles.palette();
                graphics.fill(trackX, trackY, trackX + trackWidth, trackY + trackHeight,
                    UiPalette.alpha(palette.outline(), 0.25));
                graphics.fill(x, y, x + width, y + height, palette.scrollbar());
            }
        };
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (this.previewDirty) {
            double offset = this.previewScroll.savedScrollOffset();
            this.preview.refresh();
            this.previewScroll.restoreScrollOffset(offset);
            this.previewDirty = false;
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void dispose() {
        if (this.preview != null) {
            this.preview.closeViews();
        }
        super.dispose();
    }

    private abstract static class ThemeDialog extends BaseOwoScreen<FlowLayout> {
        protected final AppearanceScreen editor;
        protected FlowLayout panel;

        private ThemeDialog(AppearanceScreen editor, String title) {
            super(Component.literal(title));
            this.editor = editor;
        }

        @Override
        protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
            return OwoUIAdapter.create(this, UIContainers::verticalFlow);
        }

        protected void frame(FlowLayout root, int preferredHeight) {
            root.surface(Surface.flat(UiPalette.alpha(UiStyles.palette().surface(), 0.85)));
            root.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
            this.panel = UIContainers.verticalFlow(Sizing.fixed(Math.min(360, Math.max(160, this.width - 32))),
                Sizing.fixed(Math.min(preferredHeight, Math.max(110, this.height - 32))));
            this.panel.surface(UiSurfaces.roundedPanel(UiStyles.palette().surfaceRaised(), 6));
            this.panel.padding(Insets.of(12));
            this.panel.gap(8);
            this.panel.child(row(UiComponents.boldLabel(this.title.getString(), UiStyles.palette().textPrimary()),
                UiComponents.spacer(), UiButton.text("×", this::onClose)));
            root.child(this.panel);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            this.editor.extractRenderState(graphics, -10_000, -10_000, delta);
            graphics.nextStratum();
            super.extractRenderState(graphics, mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
            if (!this.panel.isInBoundingBox(click.x(), click.y())) {
                this.onClose();
                return true;
            }
            return super.mouseClicked(click, doubled);
        }

        @Override
        public boolean keyPressed(KeyEvent key) {
            if (key.key() == InputConstants.KEY_ESCAPE) {
                this.onClose();
                return true;
            }
            return super.keyPressed(key);
        }

        @Override
        public void onClose() {
            this.editor.resumeDialog();
        }
    }

    private static final class SharingDialog extends ThemeDialog {
        private final boolean importing;
        private LabelComponent feedback;
        private @Nullable TextAreaComponent input;
        private String json;

        private SharingDialog(AppearanceScreen editor, boolean importing) {
            super(editor, importing ? "Import theme" : "Export theme");
            this.importing = importing;
            this.json = importing ? "" : ThemeJson.exportTheme(editor.draft);
        }

        @Override
        protected void build(FlowLayout root) {
            this.frame(root, 270);
            if (this.importing) {
                this.input = UIComponents.textArea(Sizing.fill(100), Sizing.expand(100), this.json);
                this.input.setCharacterLimit(16_384);
                this.input.onChanged().subscribe(value -> this.json = value);
                this.panel.child(this.input);
            } else {
                var jsonText = UiComponents.label(this.json, UiStyles.palette().textPrimary());
                jsonText.maxWidth(Math.min(324, this.width - 68));
                var scroll = new RestorableVerticalScrollContainer<>(Sizing.fill(100), Sizing.expand(100), jsonText);
                scroll.scrollbarThiccness(3);
                scroll.scrollbar(scrollbar());
                scroll.padding(Insets.of(6));
                scroll.surface((graphics, component) -> {
                    graphics.fill(component.x(), component.y(), component.x() + component.width(),
                        component.y() + component.height(), 0xFF000000);
                    graphics.drawRectOutline(component.x(), component.y(), component.width(), component.height(),
                        UiStyles.palette().textSecondary());
                });
                this.panel.child(scroll);
            }
            this.feedback = UiComponents.label("", UiStyles.palette().error());
            this.feedback.horizontalSizing(Sizing.fill(100));
            this.feedback.verticalSizing(Sizing.fixed(20));
            this.feedback.maxWidth(Math.min(336, this.width - 56));
            this.panel.child(this.feedback);
            var action = UiButton.text(this.importing ? "Preview" : "Copy", () -> {
                if (!this.importing) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(this.json);
                    this.feedback.color(Color.ofArgb(UiStyles.palette().success()));
                    this.feedback.text(Component.literal("Copied"));
                    return;
                }
                ThemeJson.importTheme(this.json).onSuccess(theme -> {
                    this.editor.draft = theme;
                    this.editor.previewDirty = true;
                    this.editor.returnFocus = null;
                    this.editor.buildControls();
                    this.onClose();
                }).onFailure(error -> this.feedback.text(Component.literal("Invalid theme JSON")));
            }).primary(true);
            var buttons = row(UiButton.text("Close", this::onClose), UiComponents.spacer());
            if (this.importing) {
                buttons.child(UiButton.text("Paste", () -> {
                    if (this.input != null) {
                        this.input.setValue(Minecraft.getInstance().keyboardHandler.getClipboard());
                    }
                }));
            }
            buttons.child(action);
            this.panel.child(buttons);
        }
    }

    private static final class SaveThemeDialog extends ThemeDialog {
        private SaveThemeDialog(AppearanceScreen editor) {
            super(editor, "Save theme");
        }

        @Override
        protected void build(FlowLayout root) {
            this.frame(root, 145);
            this.panel.child(UiComponents.label("Name", UiStyles.palette().textSecondary()));
            var name = UIComponents.textBox(Sizing.fill(100));
            name.setMaxLength(64);
            boolean builtIn = Arrays.stream(ThemePreset.values())
                .anyMatch(preset -> preset.theme().name().equalsIgnoreCase(this.editor.draft.name()));
            name.text(builtIn ? this.editor.draft.name() + " custom" : this.editor.draft.name());
            this.panel.child(name);
            var feedback = UiComponents.label("", UiStyles.palette().error());
            feedback.horizontalSizing(Sizing.fill(100));
            this.panel.child(feedback);
            var save = UiButton.text("Save", () -> {
                var candidate = this.editor.draft.withName(name.getValue());
                this.editor.manager.saveTheme(candidate);
                this.editor.draft = candidate;
                this.editor.previewDirty = true;
                this.editor.buildControls();
                this.onClose();
            }).primary(true);
            save.horizontalSizing(Sizing.fixed(96));
            Runnable validate = () -> {
                var parsed = Try.of(() -> this.editor.draft.withName(name.getValue()));
                boolean reserved = parsed.isSuccess() && Arrays.stream(ThemePreset.values())
                    .anyMatch(preset -> preset.theme().name().equalsIgnoreCase(parsed.get().name()));
                boolean exists = parsed.isSuccess() && this.editor.manager.savedThemes().stream()
                    .anyMatch(theme -> theme.name().equalsIgnoreCase(parsed.get().name()));
                save.active(parsed.isSuccess() && !reserved);
                save.setMessage(Component.literal(exists ? "Update theme" : "Save"));
                feedback.text(Component.literal(reserved
                    ? "Built-in names are reserved"
                    : parsed.isFailure() ? "Enter a valid name" : ""));
            };
            name.onChanged().subscribe(_ -> validate.run());
            validate.run();
            this.panel.child(row(UiButton.text("Cancel", this::onClose), UiComponents.spacer(), save));
        }
    }

    private static final class ColorDialog extends ThemeDialog {
        private final UiColor role;
        private int candidate;

        private ColorDialog(AppearanceScreen editor, UiColor role) {
            super(editor, role.label());
            this.role = role;
            this.candidate = role.color(editor.draft.palette());
        }

        @Override
        protected void build(FlowLayout root) {
            this.frame(root, 230);
            var picker = new ColorPickerComponent();
            picker.sizing(Sizing.fill(100), Sizing.expand(100));
            picker.showAlpha(false);
            picker.selectedColor(Color.ofArgb(this.candidate));
            var hex = UIComponents.textBox(Sizing.fill(100));
            hex.setMaxLength(7);
            hex.setFilter(value -> value.matches("#?[0-9a-fA-F]{0,6}"));
            hex.text(ThemeJson.formatColor(this.candidate));
            var accept = UiButton.text("Use color", () -> {
                this.editor.editPalette(this.editor.draft.palette().withColor(this.role, this.candidate));
                this.editor.buildControls();
                this.editor.returnFocus = this.editor.colorFields.get(this.role);
                this.onClose();
            }).primary(true);
            boolean[] syncing = {false};
            hex.onChanged().subscribe(value -> {
                var parsed = ThemeJson.parseColor(value);
                accept.active(parsed.isSuccess());
                hex.setTextColor(parsed.isSuccess() ? UiStyles.palette().textPrimary() : UiStyles.palette().error());
                if (parsed.isSuccess() && !syncing[0]) {
                    this.candidate = parsed.get();
                    syncing[0] = true;
                    picker.selectedColor(Color.ofArgb(this.candidate));
                    syncing[0] = false;
                }
            });
            picker.onChanged().subscribe(color -> {
                if (!syncing[0]) {
                    this.candidate = color.argb() | 0xFF000000;
                    syncing[0] = true;
                    hex.setValue(ThemeJson.formatColor(this.candidate));
                    syncing[0] = false;
                }
            });
            this.panel.child(picker);
            this.panel.child(hex);
            this.panel.child(row(UiButton.text("Cancel", this::onClose), UiComponents.spacer(), accept));
        }
    }
}
