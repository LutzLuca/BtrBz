package com.github.lutzluca.btrbz.core.widgets.ui;

import com.github.lutzluca.btrbz.core.widgets.layout.WidgetBounds;
import com.github.lutzluca.btrbz.core.widgets.WidgetId;
import io.wispforest.owo.ui.base.BaseParentUIComponent;
import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.ParentUIComponent;
import io.wispforest.owo.ui.core.Size;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WidgetCanvasComponentTest {
    @Test
    void scaledSlotResolvesTooltipUsingLogicalCoordinates() {
        var first = new TooltipComponent(100, 50);
        var second = new TooltipComponent(100, 50);
        var rows = UIContainers.verticalFlow(Sizing.fixed(100), Sizing.fixed(100));
        rows.child(first);
        rows.child(second);

        var slot = slot("test:scaled", rows, 10, 20, 100, 100, .5);

        // Physical y=57 lies over the first row's unscaled bounds, but maps to
        // logical y=94 and therefore belongs to the rendered second row.
        assertSame(second, slot.tooltipTargetAt(35, 57));
    }

    @Test
    void canvasChoosesOnlyTheTopmostTooltipSlot() {
        var back = slot("test:back", new TooltipComponent(100, 100), 10, 20, 100, 100, 1.0);
        var front = slot("test:front", new TooltipComponent(100, 100), 10, 20, 100, 100, 1.0);

        assertSame(front, WidgetCanvasComponent.topmostTooltipSlot(List.of(back, front), 30, 40));
        assertNull(WidgetCanvasComponent.topmostTooltipSlot(List.of(back, front), 200, 200));
    }

    @Test
    void canvasSuppressesAllTooltipsWhileAnySlotOwnsMouseCapture() {
        var tooltipSlot = slot("test:tooltip", new TooltipComponent(100, 100), 10, 20, 100, 100, 1.0);
        var captureSlot = slot(
            "test:capture",
            new DragParent(new PassiveComponent(100, 100)),
            150,
            20,
            100,
            100,
            1.0);
        var captureClick = new MouseButtonEvent(25, 25, new MouseButtonInfo(0, 0));
        var slots = List.of(tooltipSlot, captureSlot);

        assertSame(tooltipSlot, WidgetCanvasComponent.topmostTooltipSlot(slots, 30, 40));
        assertTrue(captureSlot.onMouseDown(captureClick, false));
        assertNull(WidgetCanvasComponent.topmostTooltipSlot(slots, 30, 40));

        captureSlot.onMouseUp(captureClick);

        assertSame(tooltipSlot, WidgetCanvasComponent.topmostTooltipSlot(slots, 30, 40));
    }

    @Test
    void dragStaysWithTheComponentThatHandledMouseDown() {
        var dragParent = new DragParent(new PassiveComponent(100, 100));
        var slot = slot("test:drag-parent", dragParent, 10, 20, 100, 100, .5);
        var click = new MouseButtonEvent(25, 25, new MouseButtonInfo(0, 0));

        assertTrue(slot.onMouseDown(click, false));
        assertTrue(slot.onMouseDrag(click, 2, 2));
        assertTrue(dragParent.dragged);
    }

    @Test
    void slotDoesNotOverwriteRetainedChildSizing() {
        var child = new PassiveComponent(80, 60);

        slot("test:retained-sizing", child, 0, 0, 40, 30, 1.0);

        assertEquals(80, child.width());
        assertEquals(60, child.height());
    }

    private static WidgetSlotComponent slot(
        String id,
        BaseUIComponent child,
        int x,
        int y,
        int logicalWidth,
        int logicalHeight,
        double scale
    ) {
        int physicalWidth = (int) Math.ceil(logicalWidth * scale);
        int physicalHeight = (int) Math.ceil(logicalHeight * scale);
        var slot = new WidgetSlotComponent(
            WidgetId.parse(id),
            child,
            0,
            new WidgetBounds(x, y, physicalWidth, physicalHeight),
            logicalWidth,
            logicalHeight,
            scale,
            false,
            false);
        slot.mount(null, x, y);
        slot.inflate(Size.of(physicalWidth, physicalHeight));
        return slot;
    }

    private static final class TooltipComponent extends BaseUIComponent {
        private TooltipComponent(int width, int height) {
            this.horizontalSizing(Sizing.fixed(width));
            this.verticalSizing(Sizing.fixed(height));
        }

        @Override
        public boolean shouldDrawTooltip(double mouseX, double mouseY) {
            return this.isInBoundingBox(mouseX, mouseY);
        }

        @Override
        public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {}
    }

    private static final class PassiveComponent extends BaseUIComponent {
        private PassiveComponent(int width, int height) {
            this.horizontalSizing(Sizing.fixed(width));
            this.verticalSizing(Sizing.fixed(height));
        }

        @Override
        public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {}
    }

    private static final class DragParent extends BaseParentUIComponent {
        private final UIComponent child;
        private boolean dragged;

        private DragParent(UIComponent child) {
            super(Sizing.fixed(100), Sizing.fixed(100));
            this.child = child;
        }

        @Override
        public void layout(Size space) {
            this.child.inflate(this.calculateChildSpace(space));
            this.child.mount(this, this.x, this.y);
        }

        @Override
        public List<UIComponent> children() {
            return List.of(this.child);
        }

        @Override
        public ParentUIComponent removeChild(UIComponent child) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
            return true;
        }

        @Override
        public boolean onMouseDrag(MouseButtonEvent click, double deltaX, double deltaY) {
            this.dragged = true;
            return true;
        }

        @Override
        public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {}
    }
}
