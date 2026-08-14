package com.github.lutzluca.btrbz.core.widgets.manager;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.widgets.WidgetId;
import com.github.lutzluca.btrbz.core.widgets.WidgetRuntime;
import com.github.lutzluca.btrbz.core.widgets.config.WidgetStateStore;
import com.github.lutzluca.btrbz.core.widgets.layout.WidgetBounds;
import com.github.lutzluca.btrbz.core.widgets.layout.WidgetCanvas;
import com.github.lutzluca.btrbz.core.widgets.layout.WidgetPlacement;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetCanvasComponent;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetSlotComponent;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetSurfaces;
import com.mojang.blaze3d.platform.InputConstants;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Always-draggable Bazaar affordance that opens the shared widget manager screen. */
public final class WidgetManagerLauncher {
    private static final int SIZE = 22;
    private static final int ICON_SIZE = 16;
    private static final double DRAG_THRESHOLD = 2.0;
    private static final Identifier ICON = Identifier.fromNamespaceAndPath(BtrBz.MOD_ID, "icon.png");
    private static final WidgetId ID = WidgetId.of(
        Identifier.fromNamespaceAndPath(BtrBz.MOD_ID, "widget_manager_launcher"));

    private final WidgetRuntime runtime;
    private final WidgetStateStore stateStore;

    private OwoUIAdapter<WidgetCanvasComponent> adapter;
    private FlowLayout button;
    private WidgetSlotComponent slot;

    private WidgetBounds bounds = new WidgetBounds(0, 0, SIZE, SIZE);
    private boolean visible;

    private boolean captured;
    private boolean dragging;
    private double startX;
    private double startY;
    private double pointerOffsetX;
    private double pointerOffsetY;

    public WidgetManagerLauncher(WidgetRuntime runtime) {
        this.runtime = runtime;
        this.stateStore = runtime.stateStore();
    }

    public void render(
        GuiGraphicsExtractor graphics,
        int mouseX,
        int mouseY,
        float delta,
        WidgetCanvas canvas,
        Screen screen
    ) {
        this.visible = this.stateStore.managerLauncherVisible()
            && this.runtime.canOpenContextualManager(screen);

        if (!this.visible) {
            return;
        }

        this.ensureAdapter();

        var layout = WidgetManagerLauncherLayout.resolve(
            canvas,
            this.stateStore.managerLauncherPosition(),
            SIZE,
            this.stateStore.requestedGlobalScale());

        this.bounds = layout.screenBounds();

        this.slot.update(
            0x00000000,
            layout.localBounds(),
            SIZE,
            SIZE,
            layout.scale(),
            false,
            false,
            true);

        this.adapter.rootComponent.synchronizeSlots(List.of(this.slot));
        this.adapter.moveAndResize(canvas.x(), canvas.y(), canvas.width(), canvas.height());
        this.adapter.extractRenderState(graphics, mouseX, mouseY, delta);

        if (!this.captured) {
            this.adapter.drawTooltip(graphics, mouseX, mouseY, delta);
        }
    }

    public boolean mouseClicked(MouseButtonEvent click) {
        if (!this.visible || click.button() != InputConstants.MOUSE_BUTTON_LEFT
            || !this.bounds.contains(click.x(), click.y())) {
            return false;
        }

        this.captured = true;
        this.dragging = false;
        this.startX = click.x();
        this.startY = click.y();
        this.pointerOffsetX = click.x() - this.bounds.x();
        this.pointerOffsetY = click.y() - this.bounds.y();

        return true;
    }

    public boolean mouseDragged(MouseButtonEvent click, WidgetCanvas canvas) {
        if (!this.captured || click.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }

        if (Math.abs(click.x() - this.startX) > DRAG_THRESHOLD
            || Math.abs(click.y() - this.startY) > DRAG_THRESHOLD) {
            this.dragging = true;
        }

        if (this.dragging) {
            this.updatePlacement(click.x(), click.y(), canvas);
        }

        return true;
    }

    public boolean mouseReleased(MouseButtonEvent click, WidgetCanvas canvas, Screen screen) {
        if (!this.captured || click.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }

        boolean moved = this.dragging;

        if (moved) {
            this.updatePlacement(click.x(), click.y(), canvas);
            this.stateStore.save();
        }

        this.captured = false;
        this.dragging = false;

        if (!moved) {
            var manager = this.runtime.createManagementScreen(screen);

            if (screen instanceof WidgetManagerLauncherOwner owner) {
                owner.btrbz$prepareManagerTransition();
            }

            Minecraft.getInstance().setScreen(manager);
        }

        return true;
    }

    public void dispose() {
        this.visible = false;
        this.captured = false;
        this.dragging = false;
        var current = this.adapter;
        this.adapter = null;
        this.button = null;
        this.slot = null;

        if (current != null) {
            current.dispose();
        }
    }

    private void ensureAdapter() {
        if (this.adapter != null) {
            return;
        }

        this.adapter = OwoUIAdapter.createWithoutScreen(0, 0, 1, 1, WidgetCanvasComponent::new);

        this.button = UIContainers.verticalFlow(Sizing.fixed(SIZE), Sizing.fixed(SIZE));
        this.button.padding(Insets.of((SIZE - ICON_SIZE) / 2));
        this.button.surface(WidgetSurfaces.roundedPanel(0xE0222730, 5));
        this.button.cursorStyle(CursorStyle.HAND);
        this.button.tooltip(Component.literal("Open widget manager"));

        var icon = UIComponents.texture(ICON, 0, 0, 1024, 1024, 1024, 1024);
        icon.sizing(Sizing.fixed(ICON_SIZE), Sizing.fixed(ICON_SIZE));
        icon.blend(true);
        this.button.child(icon);

        this.slot = new WidgetSlotComponent(
            ID,
            this.button,
            0x00000000,
            new WidgetBounds(0, 0, SIZE, SIZE),
            SIZE,
            SIZE,
            1.0,
            false,
            false);
        this.adapter.rootComponent.synchronizeSlots(List.of(this.slot));
    }

    private void updatePlacement(double mouseX, double mouseY, WidgetCanvas canvas) {
        int x = (int) Math.round(mouseX - this.pointerOffsetX - canvas.x());
        int y = (int) Math.round(mouseY - this.pointerOffsetY - canvas.y());
        this.stateStore.setManagerLauncherPosition(
            WidgetPlacement.fromAbsolute(
                x, y, canvas.width(), canvas.height(), this.bounds.width(), this.bounds.height()),
            false);
    }
}
