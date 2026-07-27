package com.github.lutzluca.btrbz.core.widgets.ui;

import io.wispforest.owo.ui.base.BaseParentUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.ParentUIComponent;
import io.wispforest.owo.ui.core.Size;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A retained keyed list for ordinary Bazaar rows. */
public final class BazaarOrderListComponent extends BaseParentUIComponent {
    private final WidgetScrollListComponent scrollList;
    private final Map<String, BazaarOrderRowComponent> rowsById = new LinkedHashMap<>();
    private final List<BazaarOrderRowComponent> rows = new ArrayList<>();
    private final List<UIComponent> children;

    public BazaarOrderListComponent(boolean hoverable, int rowHeight, int height) {
        super(Sizing.fill(100), Sizing.fixed(height));
        this.scrollList = new WidgetScrollListComponent(
            height, WidgetLayoutTokens.LIST_GAP, hoverable, BazaarStyles.SCROLLBAR
        );
        this.children = Collections.singletonList(this.scrollList);
        this.allowOverflow(true);
    }

    public void update(
        List<BazaarOrderRowComponent.BazaarRow> rowData,
        boolean hoverable,
        int rowHeight,
        int height
    ) {
        this.dirty = true;
        var retainedIds = new HashSet<String>();
        var ordered = new ArrayList<BazaarOrderRowComponent>(rowData.size());
        for (var data : rowData) {
            if (!retainedIds.add(data.id())) throw new IllegalArgumentException("Duplicate Bazaar row id: " + data.id());
            var row = this.rowsById.computeIfAbsent(
                data.id(), _ -> new BazaarOrderRowComponent(data, hoverable, rowHeight, true)
            );
            row.update(data, hoverable, rowHeight);
            ordered.add(row);
        }
        this.rowsById.keySet().removeIf(id -> !retainedIds.contains(id));
        this.rows.clear();
        this.rows.addAll(ordered);
        this.verticalSizing(Sizing.fixed(Math.max(1, height)));
        this.scrollList.updateRows(this.rows, height, hoverable);
        this.updateLayout();
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
        for (var row : this.rows) row.suppressHover(suppressRowHover);
        this.drawChildren(graphics, mouseX, mouseY, partialTicks, delta, this.children);
    }
}
