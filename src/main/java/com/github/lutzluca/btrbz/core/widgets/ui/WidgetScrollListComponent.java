package com.github.lutzluca.btrbz.core.widgets.ui;

import io.wispforest.owo.ui.base.BaseParentUIComponent;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.ParentUIComponent;
import io.wispforest.owo.ui.core.Size;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A retained vertical row list with a component-owned scroll viewport. */
public final class WidgetScrollListComponent extends BaseParentUIComponent {
    private final RetainedFlowLayout rowLayout;
    private final WidgetScrollContainer<RetainedFlowLayout> scroller;

    private final List<UIComponent> rows = new ArrayList<>();
    private final List<UIComponent> children;

    private int viewportHeight;
    private boolean interactive;

    public WidgetScrollListComponent(int viewportHeight, int rowGap, boolean interactive, int scrollbarColor) {
        super(Sizing.fill(100), Sizing.fixed(viewportHeight));
        this.rowLayout = RetainedFlowLayout.vertical(Sizing.fill(100), Sizing.content());
        this.rowLayout.allowOverflow(true);
        this.rowLayout.gap(rowGap);

        this.scroller = new WidgetScrollContainer<>(
            Sizing.fill(100), Sizing.fill(100), this.rowLayout, interactive);
        this.scroller.scrollbarThiccness(WidgetLayoutTokens.SCROLLBAR_THICKNESS);
        this.scroller.scrollbar(ScrollContainer.Scrollbar.flat(Color.ofArgb(scrollbarColor)));

        this.children = Collections.singletonList(this.scroller);
        this.viewportHeight = Math.max(1, viewportHeight);
        this.interactive = interactive;

        this.allowOverflow(true);
    }

    public void updateRows(List<? extends UIComponent> rows, int viewportHeight, boolean interactive) {
        int normalizedHeight = Math.max(1, viewportHeight);

        if (this.viewportHeight != normalizedHeight) {
            this.viewportHeight = normalizedHeight;
            this.verticalSizing(Sizing.fixed(normalizedHeight));
        }

        if (this.interactive != interactive) {
            this.interactive = interactive;
            this.scroller.interactive(interactive);
        }

        if (sameRows(this.rows, rows)) {
            return;
        }

        this.rows.clear();
        this.rows.addAll(rows);

        this.rowLayout.clearChildren();
        this.rowLayout.children(this.rows);
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

    public void flashScrollbar() {
        this.scroller.flashScrollbar();
    }

    public double scrollOffset() {
        return this.scroller.scrollOffset();
    }

    public void scrollOffset(double offset) {
        this.scroller.scrollOffset(offset);
    }

    private static boolean sameRows(List<UIComponent> current, List<? extends UIComponent> updated) {
        if (current.size() != updated.size()) {
            return false;
        }

        for (int index = 0; index < current.size(); index++) {
            if (current.get(index) != updated.get(index)) {
                return false;
            }
        }

        return true;
    }
}
