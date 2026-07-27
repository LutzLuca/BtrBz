package com.github.lutzluca.btrbz.core.widgets.ui;

import io.wispforest.owo.ui.base.BaseParentUIComponent;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.ParentUIComponent;
import io.wispforest.owo.ui.core.Size;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import java.util.Collections;
import java.util.List;

/** A retained vertical row list with a component-owned scroll viewport. */
public final class WidgetScrollListComponent extends BaseParentUIComponent {
    private final RetainedFlowLayout rowLayout;
    private final WidgetScrollContainer<RetainedFlowLayout> scroller;
    private final List<UIComponent> children;

    public WidgetScrollListComponent(int viewportHeight, int rowGap, boolean interactive, int scrollbarColor) {
        super(Sizing.fill(100), Sizing.fixed(viewportHeight));
        this.rowLayout = RetainedFlowLayout.vertical(Sizing.fill(100), Sizing.content());
        this.rowLayout.allowOverflow(true);
        this.rowLayout.gap(rowGap);
        this.scroller = new WidgetScrollContainer<>(
            Sizing.fill(100), Sizing.fill(100), this.rowLayout, interactive
        );
        this.scroller.scrollbarThiccness(WidgetLayoutTokens.SCROLLBAR_THICKNESS);
        this.scroller.scrollbar(ScrollContainer.Scrollbar.flat(Color.ofArgb(scrollbarColor)));
        this.children = Collections.singletonList(this.scroller);
        this.allowOverflow(true);
    }

    public void updateRows(List<? extends UIComponent> rows, int viewportHeight, boolean interactive) {
        this.dirty = true;
        this.verticalSizing(Sizing.fixed(Math.max(1, viewportHeight)));
        this.scroller.interactive(interactive);
        this.rowLayout.clearChildren();
        for (var row : rows) this.rowLayout.child(row);
        this.updateLayout();
    }

    @Override
    public void layout(Size space) {
        this.scroller.inflate(this.calculateChildSpace(space));
        this.scroller.mount(this, this.x, this.y);
    }

    @Override
    public List<UIComponent> children() {
        return this.children;
    }

    @Override
    public ParentUIComponent removeChild(UIComponent child) {
        throw new UnsupportedOperationException("Widget scroll list owns its scroll container");
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        super.draw(graphics, mouseX, mouseY, partialTicks, delta);
        this.drawChildren(graphics, mouseX, mouseY, partialTicks, delta, this.children);
    }

    public boolean scrollbarOwnsMouseCapture() {
        return this.scroller.thumbCaptured();
    }

    public boolean isPointerOverScrollbar(double mouseX, double mouseY) {
        return this.scroller.isPointerOverScrollbar(mouseX, mouseY);
    }

    public void scrollByProgress(double delta) {
        this.scroller.scrollByProgress(delta);
    }
}
