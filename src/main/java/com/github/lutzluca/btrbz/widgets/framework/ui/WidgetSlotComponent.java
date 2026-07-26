package com.github.lutzluca.btrbz.widgets.framework.ui;

import com.github.lutzluca.btrbz.widgets.framework.WidgetBounds;
import com.github.lutzluca.btrbz.widgets.framework.WidgetId;
import io.wispforest.owo.ui.base.BaseParentUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.ParentUIComponent;
import io.wispforest.owo.ui.core.Size;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import net.minecraft.client.input.MouseButtonEvent;
import org.jetbrains.annotations.Nullable;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public final class WidgetSlotComponent extends BaseParentUIComponent {
    private final WidgetId widgetId;
    private final UIComponent child;
    private final WidgetBounds localBounds;
    private final WidgetRenderSurface renderSurface;
    private final int backgroundColor;
    private final int logicalWidth;
    private final int logicalHeight;
    private final double scale;
    private final boolean selected;
    private final boolean drawManagementOverlay;
    private @Nullable UIComponent activeMouseTarget;

    public WidgetSlotComponent(
        WidgetId widgetId,
        UIComponent child,
        WidgetRenderSurface renderSurface,
        int backgroundColor,
        WidgetBounds localBounds,
        int logicalWidth,
        int logicalHeight,
        double scale,
        boolean selected,
        boolean drawManagementOverlay
    ) {
        super(Sizing.fixed(localBounds.width()), Sizing.fixed(localBounds.height()));
        this.widgetId = widgetId;
        this.child = child;
        this.renderSurface = renderSurface;
        this.backgroundColor = backgroundColor;
        this.localBounds = localBounds;
        this.logicalWidth = Math.max(1, logicalWidth);
        this.logicalHeight = Math.max(1, logicalHeight);
        this.scale = scale;
        this.selected = selected;
        this.drawManagementOverlay = drawManagementOverlay;
        this.allowOverflow(true);
    }

    public WidgetBounds localBounds() {
        return this.localBounds;
    }

    public WidgetId widgetId() {
        return this.widgetId;
    }

    public boolean hasActiveMouseTarget() {
        return this.activeMouseTarget != null;
    }

    public boolean resumePersistentMouseCapture() {
        this.activeMouseTarget = this.findPersistentMouseCapture(this.child);
        return this.activeMouseTarget != null;
    }

    @Override
    public void layout(Size space) {
        this.child.horizontalSizing(Sizing.fixed(this.logicalWidth));
        this.child.verticalSizing(Sizing.fixed(this.logicalHeight));
        this.child.inflate(Size.of(this.logicalWidth, this.logicalHeight));
        this.child.mount(this, this.x, this.y);
    }

    @Override
    public List<UIComponent> children() {
        return List.of(this.child);
    }

    @Override
    public ParentUIComponent removeChild(UIComponent child) {
        if (child == this.child) {
            child.dismount(DismountReason.REMOVED);
        }

        return this;
    }

    @Override
    public boolean canFocus(FocusSource source) {
        return source == FocusSource.MOUSE_CLICK;
    }

    @Override
    public @Nullable UIComponent childAt(int x, int y) {
        return this.isInBoundingBox(x, y) ? this : null;
    }

    @Override
    public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
        var target = this.targetAt(click.x(), click.y());
        if (target == null) return false;

        boolean handled = this.child.onMouseDown(this.eventFor(this.child, click), doubled);
        if (!handled) {
            this.activeMouseTarget = null;
            return false;
        }

        var capturedTarget = this.findPersistentMouseCaptureOnPath(target);
        this.activeMouseTarget = capturedTarget != null ? capturedTarget : target;
        return true;
    }

    @Override
    public boolean onMouseDrag(MouseButtonEvent click, double deltaX, double deltaY) {
        if (this.activeMouseTarget == null) return false;

        return this.activeMouseTarget.onMouseDrag(
            this.eventFor(this.activeMouseTarget, click),
            deltaX / this.safeScale(),
            deltaY / this.safeScale()
        );
    }

    @Override
    public boolean onMouseUp(MouseButtonEvent click) {
        if (this.activeMouseTarget == null) return false;

        var target = this.activeMouseTarget;
        this.activeMouseTarget = null;
        return target.onMouseUp(this.eventFor(target, click));
    }

    @Override
    public boolean onMouseScroll(double mouseX, double mouseY, double amount) {
        return this.child.onMouseScroll(
            this.logicalAbsoluteX(mouseX) - this.child.x(),
            this.logicalAbsoluteY(mouseY) - this.child.y(),
            amount
        );
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        int logicalMouseX = this.logicalAbsoluteX(mouseX - this.x);
        int logicalMouseY = this.logicalAbsoluteY(mouseY - this.y);

        graphics.push();
        try {
            graphics.translate(this.x, this.y);
            graphics.getMatrixStack().scale((float) this.scale, (float) this.scale);
            WidgetSurfaces.drawRoundedPanel(
                graphics,
                0,
                0,
                this.logicalWidth,
                this.logicalHeight,
                this.backgroundColor,
                WidgetChrome.CORNER_RADIUS
            );
        } finally {
            graphics.pop();
        }

        try {
            this.renderSurface.render(
                graphics,
                this.child,
                this.logicalWidth,
                this.logicalHeight,
                logicalMouseX,
                logicalMouseY,
                partialTicks,
                delta,
                this.x,
                this.y,
                this.scale
            );
        } catch (RuntimeException exception) {
            log.warn("Widget {} failed while rendering", this.widgetId, exception);
        }

        if (this.drawManagementOverlay) {
            int color = this.selected ? 0xFFEBCB5B : 0x66FFFFFF;
            graphics.drawRectOutline(this.x - 1, this.y - 1, this.width + 2, this.height + 2, color);
            if (this.selected) {
                graphics.fill(this.x - 3, this.y - 3, this.x + 5, this.y + 5, 0xFFEBCB5B);
                graphics.drawRectOutline(this.x - 3, this.y - 3, 8, 8, 0xFF1A1C22);
            }
        }
    }

    @Override
    public boolean shouldDrawTooltip(double mouseX, double mouseY) {
        return this.tooltipTargetAt(mouseX, mouseY) != null;
    }

    @Override
    public void drawTooltip(OwoUIGraphics context, int mouseX, int mouseY, float partialTicks, float delta) {
        int logicalMouseX = this.logicalAbsoluteX(mouseX - this.x);
        int logicalMouseY = this.logicalAbsoluteY(mouseY - this.y);
        var target = this.logicalTooltipTargetAt(logicalMouseX, logicalMouseY);
        if (target == null) return;

        context.push();
        try {
            context.translate(this.x, this.y);
            context.getMatrixStack().scale((float) this.scale, (float) this.scale);
            context.translate(-this.x, -this.y);
            target.drawTooltip(context, logicalMouseX, logicalMouseY, partialTicks, delta);
        } finally {
            context.pop();
        }
    }

    private @Nullable UIComponent targetAt(double physicalX, double physicalY) {
        int logicalX = this.logicalAbsoluteX(physicalX);
        int logicalY = this.logicalAbsoluteY(physicalY);
        var target = this.child instanceof ParentUIComponent parent ? parent.childAt(logicalX, logicalY) : this.child;
        return target != null && target != this ? target : null;
    }

    @Nullable UIComponent tooltipTargetAt(double mouseX, double mouseY) {
        return this.logicalTooltipTargetAt(
            this.logicalAbsoluteX(mouseX - this.x),
            this.logicalAbsoluteY(mouseY - this.y)
        );
    }

    private @Nullable UIComponent logicalTooltipTargetAt(int logicalX, int logicalY) {
        UIComponent target = this.child instanceof ParentUIComponent parent
            ? parent.childAt(logicalX, logicalY)
            : this.child.isInBoundingBox(logicalX, logicalY) ? this.child : null;

        while (target != null && target != this) {
            if (target.shouldDrawTooltip(logicalX, logicalY)) return target;
            if (target == this.child) break;
            target = target.parent();
        }

        return null;
    }

    private @Nullable UIComponent findPersistentMouseCapture(UIComponent component) {
        if (component instanceof PersistentMouseCapture capture && capture.hasPersistentMouseCapture()) {
            return component;
        }

        if (component instanceof ParentUIComponent parent) {
            for (var child : parent.children()) {
                var captured = this.findPersistentMouseCapture(child);
                if (captured != null) return captured;
            }
        }

        return null;
    }

    private @Nullable UIComponent findPersistentMouseCaptureOnPath(UIComponent component) {
        UIComponent current = component;
        while (current != null && current != this) {
            if (current instanceof PersistentMouseCapture capture && capture.hasPersistentMouseCapture()) {
                return current;
            }
            if (current == this.child) break;
            current = current.parent();
        }

        return null;
    }

    private MouseButtonEvent eventFor(UIComponent target, MouseButtonEvent click) {
        return new MouseButtonEvent(
            this.logicalAbsoluteX(click.x()) - target.x(),
            this.logicalAbsoluteY(click.y()) - target.y(),
            click.buttonInfo()
        );
    }

    private int logicalAbsoluteX(double physicalX) {
        return this.x + (int) Math.floor(physicalX / this.safeScale());
    }

    private int logicalAbsoluteY(double physicalY) {
        return this.y + (int) Math.floor(physicalY / this.safeScale());
    }

    private double safeScale() {
        return Math.max(0.0001, this.scale);
    }
}
