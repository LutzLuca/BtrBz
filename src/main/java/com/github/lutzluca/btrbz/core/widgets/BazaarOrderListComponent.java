package com.github.lutzluca.btrbz.core.widgets;

import com.github.lutzluca.btrbz.widgets.framework.ui.WidgetLayoutTokens;
import com.github.lutzluca.btrbz.widgets.framework.ui.WidgetScrollListComponent;
import com.github.lutzluca.btrbz.widgets.framework.ui.WidgetScrollState;
import io.wispforest.owo.ui.base.BaseParentUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.ParentUIComponent;
import io.wispforest.owo.ui.core.Size;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class BazaarOrderListComponent extends BaseParentUIComponent {
    private final WidgetScrollListComponent scrollList;
    private final List<BazaarOrderRowComponent> rows;
    private final List<UIComponent> children;

    BazaarOrderListComponent(
        List<BazaarOrderRowComponent.BazaarRow> rowData,
        boolean hoverable,
        int rowHeight,
        int height,
        WidgetScrollState scrollState
    ) {
        super(Sizing.fill(100), Sizing.fixed(height));
        this.rows = new ArrayList<>();

        for (var rowDataEntry : rowData) {
            var row = new BazaarOrderRowComponent(rowDataEntry, hoverable, rowHeight, true);
            this.rows.add(row);
        }

        this.scrollList = new WidgetScrollListComponent(
            this.rows,
            height,
            WidgetLayoutTokens.LIST_GAP,
            hoverable,
            scrollState,
            BazaarStyles.SCROLLBAR
        );
        this.children = Collections.singletonList(this.scrollList);
        this.allowOverflow(true);
    }

    @Override
    public void layout(Size space) {
        this.scrollList.inflate(this.calculateChildSpace(space));
        this.scrollList.mount(this, this.x, this.y);
    }

    @Override
    public List<UIComponent> children() {
        return this.children;
    }

    @Override
    public ParentUIComponent removeChild(UIComponent child) {
        throw new UnsupportedOperationException("Bazaar list owns its scroll container");
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        super.draw(graphics, mouseX, mouseY, partialTicks, delta);

        boolean suppressRowHover = this.scrollList.scrollbarOwnsMouseCapture()
            || this.scrollList.isPointerOverScrollbar(mouseX, mouseY);
        for (var row : this.rows) {
            row.suppressHover(suppressRowHover);
        }
        this.drawChildren(graphics, mouseX, mouseY, partialTicks, delta, this.children);
    }
}
