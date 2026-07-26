package com.github.lutzluca.btrbz.widgets.framework.ui;

import io.wispforest.owo.ui.base.BaseParentUIComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.ParentUIComponent;
import io.wispforest.owo.ui.core.Size;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;

import java.util.Collections;
import java.util.List;

/** A standard vertical widget row list with a fixed-height scroll viewport. */
public final class WidgetScrollListComponent extends BaseParentUIComponent {
    private final WidgetScrollContainer<FlowLayout> scroller;
    private final List<UIComponent> children;

    public WidgetScrollListComponent(
        List<? extends UIComponent> rows,
        int viewportHeight,
        int rowGap,
        boolean interactive,
        WidgetScrollState state,
        int scrollbarColor
    ) {
        super(Sizing.fill(100), Sizing.fixed(viewportHeight));

        var rowLayout = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        rowLayout.allowOverflow(true);
        rowLayout.gap(rowGap);
        for (var row : rows) rowLayout.child(row);

        this.scroller = new WidgetScrollContainer<>(
            Sizing.fill(100), Sizing.fill(100), rowLayout, interactive, state
        );
        this.scroller.scrollbarThiccness(WidgetLayoutTokens.SCROLLBAR_THICKNESS);
        this.scroller.scrollbar(ScrollContainer.Scrollbar.flat(Color.ofArgb(scrollbarColor)));
        this.children = Collections.singletonList(this.scroller);
        this.allowOverflow(true);
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

    /**
     * Reports thumb capture without making this shell the capture target. The
     * nested scroll container must receive resumed drag and mouse-up events.
     */
    public boolean scrollbarOwnsMouseCapture() {
        return this.scroller.hasPersistentMouseCapture();
    }

    public boolean isPointerOverScrollbar(double mouseX, double mouseY) {
        return this.scroller.isPointerOverScrollbar(mouseX, mouseY);
    }

    public void scrollByProgress(double delta) {
        this.scroller.scrollByProgress(delta);
    }
}
