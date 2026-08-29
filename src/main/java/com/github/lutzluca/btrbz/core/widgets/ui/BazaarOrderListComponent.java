package com.github.lutzluca.btrbz.core.widgets.ui;

import io.wispforest.owo.ui.base.BaseParentUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.ParentUIComponent;
import io.wispforest.owo.ui.core.Size;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A retained keyed list for ordinary Bazaar rows. */
public final class BazaarOrderListComponent extends BaseParentUIComponent {
    private final WidgetScrollListComponent scrollList;

    private final RetainedRows<String, BazaarOrderRowComponent> retainedRows = new RetainedRows<>();
    private final List<BazaarOrderRowComponent> rows = new ArrayList<>();
    private final List<UIComponent> children;

    private int viewportHeight;

    public BazaarOrderListComponent(boolean hoverable, int rowHeight, int height) {
        super(Sizing.fill(100), Sizing.fixed(height));
        this.scrollList = new WidgetScrollListComponent(
            height, WidgetLayoutTokens.LIST_GAP, hoverable, BazaarStyles.SCROLLBAR);

        this.children = Collections.singletonList(this.scrollList);
        this.viewportHeight = Math.max(1, height);

        this.allowOverflow(true);
    }

    public void update(
        List<BazaarOrderRowComponent.BazaarRow> rowData,
        boolean hoverable,
        int rowHeight,
        int height
    ) {
        boolean reserveScrollbarSpace = WidgetLayoutTokens.requiresScrollbar(rowHeight, rowData.size(), height);
        var ordered = this.retainedRows.reconcile(
            rowData,
            BazaarOrderRowComponent.BazaarRow::id,
            (data, _) -> new BazaarOrderRowComponent(data, hoverable, rowHeight, reserveScrollbarSpace),
            (row, data, _) -> row.update(data, hoverable, rowHeight, reserveScrollbarSpace));

        this.rows.clear();
        this.rows.addAll(ordered);

        int normalizedHeight = Math.max(1, height);

        if (this.viewportHeight != normalizedHeight) {
            this.viewportHeight = normalizedHeight;
            this.verticalSizing(Sizing.fixed(normalizedHeight));
        }

        this.scrollList.updateRows(this.rows, height, hoverable);
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

        boolean suppressRowHover = !this.scrollList.isPointerInsideViewport(mouseX, mouseY)
            || this.scrollList.scrollbarOwnsMouseCapture();

        for (var row : this.rows) {
            row.suppressHover(suppressRowHover);
        }

        this.drawChildren(graphics, mouseX, mouseY, partialTicks, delta, this.children);
    }
}
