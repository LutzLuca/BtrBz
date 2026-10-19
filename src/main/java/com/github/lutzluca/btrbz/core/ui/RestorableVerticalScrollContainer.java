package com.github.lutzluca.btrbz.core.ui;

import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.Size;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;

/**
 * Vertical scroll container whose absolute offset can survive replacing the
 * component tree it belongs to.
 *
 * <p>Unmounted containers defer restoration until layout, when their maximum
 * scroll distance becomes known.</p>
 */
public final class RestorableVerticalScrollContainer<C extends UIComponent> extends ScrollContainer<C> {
    private double pendingScrollOffset = Double.NaN;

    public RestorableVerticalScrollContainer(
        Sizing horizontalSizing,
        Sizing verticalSizing,
        C child
    ) {
        super(ScrollDirection.VERTICAL, horizontalSizing, verticalSizing, child);
    }

    public double savedScrollOffset() {
        return Double.isNaN(this.pendingScrollOffset)
            ? this.scrollOffset
            : this.pendingScrollOffset;
    }

    public RestorableVerticalScrollContainer<C> restoreScrollOffset(double offset) {
        this.pendingScrollOffset = Math.max(0.0, offset);
        if (this.mounted) {
            this.scrollOffset = Math.clamp(this.pendingScrollOffset, 0.0, this.maxScroll);
            this.currentScrollPosition = this.scrollOffset;
            this.pendingScrollOffset = Double.NaN;
        }
        return this;
    }

    @Override
    public void layout(Size space) {
        super.layout(space);

        if (Double.isNaN(this.pendingScrollOffset)) {
            return;
        }

        double restored = Math.clamp(this.pendingScrollOffset, 0.0, this.maxScroll);
        this.scrollOffset = restored;
        this.currentScrollPosition = restored;
        this.pendingScrollOffset = Double.NaN;
    }
}
