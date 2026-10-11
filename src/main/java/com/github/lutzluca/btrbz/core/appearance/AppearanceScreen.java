package com.github.lutzluca.btrbz.core.appearance;

import com.github.lutzluca.btrbz.core.ui.RestorableVerticalScrollContainer;
import com.github.lutzluca.btrbz.core.ui.UiText;
import com.github.lutzluca.btrbz.core.ui.UiSurfaces;
import com.github.lutzluca.btrbz.core.ui.UiComponents;
import com.github.lutzluca.btrbz.core.ui.UiButton;
import com.github.lutzluca.btrbz.core.ui.UiStyles;
import com.github.lutzluca.btrbz.core.widgets.ui.ScrollSafeDiscreteSliderComponent;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.mojang.blaze3d.platform.InputConstants;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.component.ColorPickerComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextAreaComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
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
    private boolean formattedNames;
    private boolean colorsView;
    private UiColor.Group group = UiColor.Group.Interface;
    private FlowLayout controls;
    private int controlWidth;
    private final Map<UiColor, TextBoxComponent> colorFields = new EnumMap<>(UiColor.class);
    private @Nullable UIComponent returnFocus;
    private @Nullable TextBoxComponent nameField;
    private UiButton applyButton;

    public AppearanceScreen(@Nullable Screen parent, AppearanceManager manager) {
        super(Component.literal("BtrBz Appearance"));
        this.parent = parent;
        this.manager = manager;
        this.draft = manager.theme();
        this.formattedNames = manager.formattedProductNames();
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout root) {
        int width = Math.max(180, Math.min(600, this.width - 24));
        int height = Math.max(130, Math.min(360, this.height - 24));
        this.controlWidth = (width - 36) * 45 / 100;
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
        workspace.gap(12);
        var left = UIContainers.verticalFlow(Sizing.fixed(this.controlWidth), Sizing.fill(100));
        left.gap(6);
        left.child(row(this.tab("Basics", false), this.tab("Colors", true)));
        this.controls = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        this.controls.gap(6);
        this.controls.padding(Insets.right(5));
        var scroll = new RestorableVerticalScrollContainer<>(Sizing.fill(100), Sizing.expand(100), this.controls);
        scroll.scrollbarThiccness(3);
        left.child(scroll);
        workspace.child(left);

        var preview = UIContainers.verticalFlow(Sizing.expand(100), Sizing.fill(100));
        preview.gap(6);
        preview.child(UiComponents.label("Preview", UiStyles.palette().textSecondary()));
        var previewContent = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        previewContent.child(new ThemePreview());
        var previewScroll = new RestorableVerticalScrollContainer<>(Sizing.fill(100), Sizing.expand(100),
            previewContent);
        previewScroll.scrollbarThiccness(3);
        preview.child(previewScroll);
        workspace.child(preview);
        panel.child(workspace);

        var importButton = UiButton.text("Import", () -> this.openSharing(true));
        var exportButton = UiButton.text("Export", () -> this.openSharing(false));
        this.applyButton = UiButton.text("Apply", () -> {
            this.manager.apply(this.draft, this.formattedNames);
            this.onClose();
        }).primary(true);
        panel.child(
            row(importButton, exportButton, UiComponents.spacer(), UiButton.text("Cancel", this::onClose),
                this.applyButton));
        root.child(panel);
        this.buildControls();
    }

    private UiButton tab(String label, boolean colors) {
        var button = UiButton.text(label, () -> {
            this.colorsView = colors;
            this.rebuild();
        }).selected(this.colorsView == colors);
        button.horizontalSizing(Sizing.expand(50));
        return button;
    }

    private void buildControls() {
        if (this.uiAdapter.rootComponent.focusHandler() != null) {
            this.uiAdapter.rootComponent.focusHandler().focus(null, UIComponent.FocusSource.MOUSE_CLICK);
        }
        this.controls.clearChildren();
        this.colorFields.clear();
        this.nameField = null;
        if (this.colorsView) {
            var groups = row();
            if (this.controlWidth < 220) {
                var selectGroup = UiButton.text(this.group.name() + " colors", () -> {
                    var values = UiColor.Group.values();
                    this.group = values[(this.group.ordinal() + 1) % values.length];
                    this.rebuild();
                });
                selectGroup.horizontalSizing(Sizing.fill(100));
                groups.child(selectGroup);
            } else {
                for (var value : UiColor.Group.values()) {
                    var button = UiButton.text(value.name(), () -> {
                        this.group = value;
                        this.rebuild();
                    }).selected(this.group == value);
                    button.horizontalSizing(Sizing.expand(33));
                    groups.child(button);
                }
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

        for (var preset : ThemePreset.values()) {
            var button = UiButton.text(preset.theme().name(), () -> {
                this.draft = preset.theme();
                this.buildControls();
            }).selected(preset.theme().equals(this.draft));
            button.horizontalSizing(Sizing.fill(100));
            this.controls.child(button);
        }
        this.controls.child(UiComponents.label("Name", UiStyles.palette().textSecondary()));
        var name = UIComponents.textBox(Sizing.fill(100));
        this.nameField = name;
        name.setMaxLength(64);
        name.text(this.draft.name());
        name.onChanged().subscribe(value -> {
            boolean valid = !value.isBlank() && value.chars().noneMatch(Character::isISOControl);
            name.setTextColor(valid ? UiStyles.palette().textPrimary() : UiStyles.palette().error());
            if (valid) {
                this.draft = new UiTheme(value, this.draft.palette());
            }
            this.updateApplyState();
        });
        this.controls.child(name);
        var opacity = new ScrollSafeDiscreteSliderComponent(Sizing.fill(100), 0, 100, 1);
        opacity.decimalPlaces(0);
        opacity.setFromDiscreteValue(this.draft.palette().panelOpacity() * 100);
        opacity.message(value -> Component.literal("Panel opacity " + value + "%"));
        opacity.onChanged().subscribe(value -> this.editPalette(this.draft.palette().withOpacity(value / 100)));
        this.controls.child(opacity);
        var formatted = UIComponents.smallCheckbox(Component.literal("Formatted product names"));
        formatted.checked(this.formattedNames);
        formatted.onChanged().subscribe(value -> this.formattedNames = value);
        this.controls.child(formatted);
        this.updateApplyState();
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
        this.applyButton.active(validName && validColors);
    }

    private void editPalette(UiPalette palette) {
        if (!palette.equals(this.draft.palette())) {
            boolean preset = Arrays.stream(ThemePreset.values()).anyMatch(value -> value.theme().equals(this.draft));
            this.draft = new UiTheme(preset ? "Custom" : this.draft.name(), palette);
            if (this.nameField != null && !this.nameField.getValue().equals(this.draft.name())) {
                this.nameField.setValue(this.draft.name());
            }
        }
    }

    private void openSharing(boolean importing) {
        this.returnFocus = this.uiAdapter.rootComponent.focusHandler().focused();
        GameUtils.setScreen(new SharingDialog(this, importing));
    }

    private void rebuild() {
        this.uiAdapter.rootComponent.focusHandler().focus(null, UIComponent.FocusSource.MOUSE_CLICK);
        this.uiAdapter.rootComponent.clearChildren();
        this.build(this.uiAdapter.rootComponent);
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

    private final class ThemePreview extends BaseUIComponent {
        private ThemePreview() {
            this.sizing(Sizing.fill(100), Sizing.fixed(260));
        }

        @Override
        public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
            var p = AppearanceScreen.this.draft.palette();
            UiSurfaces.drawRoundedPanel(graphics, this.x, this.y, this.width, this.height, p.surfaceRaised(), 4);
            int x = this.x + 8;
            int y = this.y + 8;
            int width = this.width - 16;
            this.text(graphics, Component.literal(AppearanceScreen.this.draft.name()), x, y, width, p.textSecondary());
            y += 14;
            UiSurfaces.drawRoundedPanel(graphics, x, y, width, 110, p.panelBackground(), 4);
            this.text(graphics,
                Component.literal("Tracked Orders").withStyle(UiStyles.color(p.textPrimary()).withBold(true)),
                x + 7, y + 8, width - 14, p.textPrimary());
            graphics.fill(x + 7, y + 22, x + width - 7, y + 23, p.outline());
            this.order(graphics, x + 7, y + 30, width - 14, "Enchanted Sugar", "1,024 × 2.4k", "Matched", p.matched(),
                p);
            this.order(graphics, x + 7, y + 57, width - 14, "Enchanted Carrot", "716 × 3.1k", "Best", p.best(), p);
            graphics.fill(x + 4, y + 81, x + width - 4, y + 108, p.undercutRow());
            this.order(graphics, x + 7, y + 84, width - 14, "Enchanted Diamond", "64 × 1.8k", "Undercut", p.undercut(),
                p);
            y += 118;
            String[] states = {"Filled", "Expired", "Unknown"};
            int[] colors = {p.filled(), p.expired(), p.unknown()};
            for (int i = 0; i < states.length; i++) {
                this.text(graphics, Component.literal("● " + states[i]), x + i * width / 3, y, width / 3, colors[i]);
            }
            y += 16;
            graphics.fill(x, y, x + width, y + 1, p.progressTrack());
            graphics.fill(x, y, x + width * 3 / 5, y + 1, p.progressFill());
            y += 12;
            this.text(graphics, Component.literal("Buy order"), x, y, width / 2, p.buy());
            this.text(graphics, Component.literal("Sell offer"), x + width / 2, y, width / 2, p.sell());
            y += 16;
            this.text(graphics, Component.literal("✓ Saved"), x, y, width / 3, p.success());
            this.text(graphics, Component.literal("! Limit"), x + width / 3, y, width / 3, p.warning());
            this.text(graphics, Component.literal("× Error"), x + width * 2 / 3, y, width / 3, p.error());
            y += 16;
            UiSurfaces.drawRoundedPanel(graphics, x, y, width, 31, p.surface(), 3);
            this.text(graphics, Component.literal("[BtrBz] ").withStyle(UiStyles.color(p.accent()))
                .append(UiStyles.productName(GameUtils.legacyFormattedComponent("§aEnchanted Sugar"), false,
                    AppearanceScreen.this.formattedNames, p.textPrimary())),
                x + 5, y + 5, width - 10, p.textPrimary());
            this.text(graphics, Component.literal("Your offer was ").withStyle(UiStyles.color(p.textSecondary()))
                .append(Component.literal("MATCHED!").withStyle(UiStyles.color(p.matched()))), x + 5, y + 18,
                width - 10, p.textPrimary());
            y += 37;
            UiSurfaces.drawRoundedPanel(graphics, x, y, Math.min(70, width), 20, p.accent(), 3);
            this.text(graphics, Component.literal("Apply"), x + 8, y + 6, width - 16, p.onAccent());
            this.text(graphics, Component.literal("Just now"), x + 78, y + 6, width - 78, p.textMuted());
        }

        private void order(
            OwoUIGraphics graphics,
            int x,
            int y,
            int width,
            String name,
            String amount,
            String status,
            int color,
            UiPalette p
        ) {
            var product = UiStyles.productName(GameUtils.legacyFormattedComponent("§a" + name), false,
                AppearanceScreen.this.formattedNames, p.textPrimary());
            this.text(graphics, product, x, y, width, p.textPrimary());
            String[] parts = amount.split(" × ");
            var value = Component.literal(parts[0]).withStyle(UiStyles.color(p.textPrimary()))
                .append(Component.literal(" × ").withStyle(UiStyles.color(p.textSecondary())))
                .append(Component.literal(parts[1]).withStyle(UiStyles.color(p.money())));
            this.text(graphics, value, x, y + 12, width * 2 / 3, p.textPrimary());
            this.text(graphics, Component.literal(status), x + width * 2 / 3, y + 12, width / 3, color);
        }

        private void text(OwoUIGraphics graphics, Component text, int x, int y, int width, int color) {
            graphics.text(Minecraft.getInstance().font, UiText.ellipsize(text, Math.max(0, width)), x, y, color, false);
        }
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
                jsonText.maxWidth(Math.min(336, this.width - 56));
                var scroll = new RestorableVerticalScrollContainer<>(Sizing.fill(100), Sizing.expand(100), jsonText);
                scroll.scrollbarThiccness(3);
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
