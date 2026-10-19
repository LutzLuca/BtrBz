package com.github.lutzluca.btrbz.core.ui;

import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.Size;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * Vertical scroll container whose absolute offset can survive replacing the
 * component tree it belongs to.
 *
 * <p>Unmounted containers defer restoration until layout, when their maximum
 * scroll distance becomes known.</p>
 * <p>The height-bounded constructor fits short content and scrolls taller content.</p>
 */
public final class RestorableVerticalScrollContainer<C extends UIComponent> extends ScrollContainer<C> {
    private double pendingScrollOffset = Double.NaN;
    @Getter
    @Accessors(fluent = true)
    private int maxContentHeight = -1;

    public RestorableVerticalScrollContainer(
        Sizing horizontalSizing,
        Sizing verticalSizing,
        C child
    ) {
        super(ScrollDirection.VERTICAL, horizontalSizing, verticalSizing, child);
    }

    public RestorableVerticalScrollContainer(Sizing horizontalSizing, int maxContentHeight, C child) {
        this(horizontalSizing, Sizing.content(), child);
        this.maxContentHeight = Math.max(1, maxContentHeight);
    }

    public void maxContentHeight(int height) {
        int maximum = Math.max(1, height);
        if (this.maxContentHeight != maximum) {
            this.maxContentHeight = maximum;
            this.updateLayout();
        }
    }

    @Override
    protected int determineVerticalContentSize(Sizing sizing) {
        return this.maxContentHeight < 0
            ? super.determineVerticalContentSize(sizing)
            : Math.min(this.maxContentHeight, this.child.fullSize().height() + this.padding().get().vertical());
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
        if (this.maxContentHeight >= 0) {
            // Measure the content before calculating the viewport and its scroll range.
            this.child.inflate(this.calculateChildSpace(space));
            this.applySizing();
        }
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
