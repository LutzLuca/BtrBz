package com.github.lutzluca.btrbz.core.widgets.ui;

import com.github.lutzluca.btrbz.core.widgets.WidgetBounds;
import com.github.lutzluca.btrbz.core.widgets.WidgetId;
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
    private WidgetBounds localBounds;
    private final WidgetRenderSurface renderSurface;
    private int backgroundColor;
    private int logicalWidth;
    private int logicalHeight;
    private double scale;
    private boolean selected;
    private boolean drawManagementOverlay;
    private boolean visible = true;
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

    public boolean visible() { return this.visible; }

    public void update(
        int backgroundColor,
        WidgetBounds localBounds,
        int logicalWidth,
        int logicalHeight,
        double scale,
        boolean selected,
        boolean drawManagementOverlay,
        boolean visible
    ) {
        this.backgroundColor = backgroundColor;
        this.localBounds = localBounds;
        this.logicalWidth = Math.max(1, logicalWidth);
        this.logicalHeight = Math.max(1, logicalHeight);
        this.scale = scale;
        this.selected = selected;
        this.drawManagementOverlay = drawManagementOverlay;
        if (this.visible && !visible) this.activeMouseTarget = null;
        this.visible = visible;
        this.sizing(Sizing.fixed(Math.max(1, localBounds.width())), Sizing.fixed(Math.max(1, localBounds.height())));
    }

    @Override
    public void layout(Size space) {
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
        return this.visible && this.isInBoundingBox(x, y) ? this : null;
    }

    @Override
    public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
        if (!this.visible) return false;
        var target = this.targetAt(click.x(), click.y());
        if (target == null) return false;

        UIComponent candidate = target;
        while (candidate != null && candidate != this) {
            if (candidate.onMouseDown(this.eventFor(candidate, click), doubled)) {
                this.activeMouseTarget = candidate;
                return true;
            }
            if (candidate == this.child) break;
            candidate = candidate.parent();
        }

        this.activeMouseTarget = null;
        return false;
    }

    @Override
    public boolean onMouseDrag(MouseButtonEvent click, double deltaX, double deltaY) {
        if (!this.visible || this.activeMouseTarget == null) return false;

        return this.activeMouseTarget.onMouseDrag(
            this.eventFor(this.activeMouseTarget, click),
            deltaX / this.safeScale(),
            deltaY / this.safeScale()
        );
    }

    @Override
    public boolean onMouseUp(MouseButtonEvent click) {
        if (!this.visible || this.activeMouseTarget == null) return false;

        var target = this.activeMouseTarget;
        this.activeMouseTarget = null;
        return target.onMouseUp(this.eventFor(target, click));
    }

    @Override
    public boolean onMouseScroll(double mouseX, double mouseY, double amount) {
        if (!this.visible) return false;
        return this.child.onMouseScroll(
            this.logicalAbsoluteX(mouseX) - this.child.x(),
            this.logicalAbsoluteY(mouseY) - this.child.y(),
            amount
        );
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        if (!this.visible) return;
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
        return this.visible && this.tooltipTargetAt(mouseX, mouseY) != null;
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
