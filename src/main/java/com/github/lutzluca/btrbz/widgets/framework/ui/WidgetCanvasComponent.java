package com.github.lutzluca.btrbz.widgets.framework.ui;

import com.github.lutzluca.btrbz.widgets.framework.WidgetId;
import io.wispforest.owo.ui.base.BaseParentUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.ParentUIComponent;
import io.wispforest.owo.ui.core.Size;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class WidgetCanvasComponent extends BaseParentUIComponent {
    private final List<WidgetSlotComponent> slots = new ArrayList<>();
    private final List<UIComponent> slotView = Collections.unmodifiableList(this.slots);

    public WidgetCanvasComponent(Sizing horizontalSizing, Sizing verticalSizing) {
        super(horizontalSizing, verticalSizing);
        this.allowOverflow(false);
    }

    public void replaceSlots(List<WidgetSlotComponent> newSlots) {
        var capturedWidget = this.capturedWidget();

        if (this.focusHandler != null && this.focusHandler.focused() instanceof WidgetSlotComponent) {
            this.focusHandler.focus(null, FocusSource.MOUSE_CLICK);
        }

        for (var slot : this.slots) {
            slot.dismount(DismountReason.REMOVED);
        }

        this.slots.clear();
        this.slots.addAll(newSlots);

        this.updateLayout();

        if (capturedWidget != null && this.focusHandler != null) {
            for (var slot : this.slots) {
                if (capturedWidget.equals(slot.widgetId()) && slot.resumePersistentMouseCapture()) {
                    this.focusHandler.focus(slot, FocusSource.MOUSE_CLICK);
                    break;
                }
            }
        }
    }

    private @Nullable WidgetId capturedWidget() {
        for (var slot : this.slots) {
            if (slot.hasActiveMouseTarget()) return slot.widgetId();
        }

        return null;
    }

    @Override
    public void layout(Size space) {
        for (var slot : this.slots) {
            slot.inflate(space);
            slot.mount(this, this.x + slot.localBounds().x(), this.y + slot.localBounds().y());
        }
    }

    @Override
    public List<UIComponent> children() {
        return this.slotView;
    }

    @Override
    public ParentUIComponent removeChild(UIComponent child) {
        if (this.slots.remove(child)) {
            child.dismount(DismountReason.REMOVED);
            this.updateLayout();
        }

        return this;
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        super.draw(graphics, mouseX, mouseY, partialTicks, delta);
        this.drawChildren(graphics, mouseX, mouseY, partialTicks, delta, this.slots);
    }

    @Override
    public void drawTooltip(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        var slot = topmostTooltipSlot(this.slots, mouseX, mouseY);
        if (slot != null) {
            slot.drawTooltip(graphics, mouseX, mouseY, partialTicks, delta);
        }
    }

    static @Nullable WidgetSlotComponent topmostTooltipSlot(
        List<WidgetSlotComponent> slots,
        int mouseX,
        int mouseY
    ) {
        for (int index = slots.size() - 1; index >= 0; index--) {
            var slot = slots.get(index);
            if (slot.isInBoundingBox(mouseX, mouseY) && slot.shouldDrawTooltip(mouseX, mouseY)) {
                return slot;
            }
        }

        return null;
    }
}
