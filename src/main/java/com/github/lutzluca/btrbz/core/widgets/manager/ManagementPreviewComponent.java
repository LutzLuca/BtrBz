package com.github.lutzluca.btrbz.core.widgets.manager;

import com.github.lutzluca.btrbz.core.widgets.layout.WidgetCanvas;
import com.github.lutzluca.btrbz.core.widgets.WidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.runtime.WidgetHost;
import com.github.lutzluca.btrbz.core.widgets.runtime.WidgetHostOptions;
import com.github.lutzluca.btrbz.core.widgets.layout.WidgetPlacement;
import com.github.lutzluca.btrbz.core.widgets.runtime.WidgetRenderResult;
import com.mojang.blaze3d.platform.InputConstants;
import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.List;

final class ManagementPreviewComponent extends BaseUIComponent {
    private static final double DRAG_THRESHOLD = 2.0;

    private final WidgetManagementScreen screen;
    private final WidgetHost host;

    private WidgetCanvas canvas = new WidgetCanvas(0, 0, 1, 1);
    private List<WidgetRenderResult> lastResults = List.of();
    private DragState dragState;
    private boolean clearSelectionOnRelease = false;
    private boolean dragMoved = false;
    private double dragStartX = 0.0;
    private double dragStartY = 0.0;

    ManagementPreviewComponent(WidgetManagementScreen screen, WidgetHost host) {
        this.screen = screen;
        this.host = host;
        this.sizing(Sizing.expand(100), Sizing.fill(100));
        this.cursorStyle(CursorStyle.HAND);
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        this.canvas = new WidgetCanvas(this.x, this.y, this.width, this.height);

        graphics.fill(this.x, this.y, this.x + this.width, this.y + this.height, 0x660B0D12);
        this.lastResults = this.host.render(
            graphics,
            mouseX,
            mouseY,
            partialTicks,
            this.canvas,
            WidgetHostOptions.management(
                this.screen.selectedWidget(),
                this.screen.renderedWidgets(),
                this.screen.previewProfiles()
            ),
            this.screen
        );
    }

    @Override
    public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
        return this.beginDrag(this.x + click.x(), this.y + click.y(), click.button())
            || super.onMouseDown(click, doubled);
    }

    boolean beginDrag(double absoluteX, double absoluteY, int button) {
        if (button != InputConstants.MOUSE_BUTTON_LEFT) return false;

        var result = this.hitResult(absoluteX, absoluteY);
        if (result == null) return false;

        boolean alreadySelected = result.definition().getId().equals(this.screen.selectedWidget());
        this.clearSelectionOnRelease = alreadySelected;
        this.dragMoved = false;
        this.dragStartX = absoluteX;
        this.dragStartY = absoluteY;

        if (!alreadySelected) {
            this.screen.selectWidget(result.definition().getId());
        }

        this.dragState = new DragState(
            result.definition(),
            result.placementProfile(),
            absoluteX - result.bounds().x(),
            absoluteY - result.bounds().y(),
            result.bounds().width(),
            result.bounds().height()
        );
        return true;
    }

    @Override
    public boolean onMouseDrag(MouseButtonEvent click, double deltaX, double deltaY) {
        return this.dragTo(this.x + click.x(), this.y + click.y());
    }

    boolean dragTo(double absoluteMouseX, double absoluteMouseY) {
        if (this.dragState == null) return false;

        if (Math.abs(absoluteMouseX - this.dragStartX) > DRAG_THRESHOLD
            || Math.abs(absoluteMouseY - this.dragStartY) > DRAG_THRESHOLD) {
            this.dragMoved = true;
        }

        int localDropX = (int) Math.round(absoluteMouseX - this.dragState.pointerOffsetX() - this.canvas.x());
        int localDropY = (int) Math.round(absoluteMouseY - this.dragState.pointerOffsetY() - this.canvas.y());

        var placement = WidgetPlacement.fromAbsolute(
            localDropX,
            localDropY,
            this.canvas.width(),
            this.canvas.height(),
            this.dragState.scaledWidth(),
            this.dragState.scaledHeight()
        );
        this.screen.stateStore().setPlacement(
            this.dragState.definition(),
            this.dragState.placementProfile(),
            placement,
            false
        );
        return true;
    }

    @Override
    public boolean onMouseUp(MouseButtonEvent click) {
        return this.endDrag();
    }

    boolean endDrag() {
        if (this.dragState == null) return false;

        if (this.dragMoved) this.screen.markDirty();
        if (this.clearSelectionOnRelease && !this.dragMoved) {
            this.screen.clearSelectedWidget();
        }

        this.dragState = null;
        this.clearSelectionOnRelease = false;
        this.dragMoved = false;
        return true;
    }

    boolean isDragging() {
        return this.dragState != null;
    }

    private WidgetRenderResult hitResult(double absoluteX, double absoluteY) {
        for (int i = this.lastResults.size() - 1; i >= 0; i--) {
            var result = this.lastResults.get(i);
            if (result.bounds().contains(absoluteX, absoluteY)) {
                return result;
            }
        }

        return null;
    }

    private record DragState(
        WidgetDefinition<?, ?, ?> definition,
        String placementProfile,
        double pointerOffsetX,
        double pointerOffsetY,
        int scaledWidth,
        int scaledHeight
    ) {}
}
