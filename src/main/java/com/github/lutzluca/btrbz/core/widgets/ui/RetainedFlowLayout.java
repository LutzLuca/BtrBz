package com.github.lutzluca.btrbz.core.widgets.ui;

import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import java.util.Collection;

/**
 * A flow layout whose child mutations remain observable while it is detached.
 *
 * <p>owo's normal child mutators request a layout only when the component is
 * mounted. Retained widget branches are sometimes reconciled while detached;
 * preserving the dirty flag ensures their children are mounted on reattachment.</p>
 */
public final class RetainedFlowLayout extends FlowLayout {
    private RetainedFlowLayout(Sizing horizontalSizing, Sizing verticalSizing, Algorithm algorithm) {
        super(horizontalSizing, verticalSizing, algorithm);
    }

    public static RetainedFlowLayout vertical(Sizing horizontalSizing, Sizing verticalSizing) {
        return new RetainedFlowLayout(horizontalSizing, verticalSizing, Algorithm.VERTICAL);
    }

    public static RetainedFlowLayout horizontal(Sizing horizontalSizing, Sizing verticalSizing) {
        return new RetainedFlowLayout(horizontalSizing, verticalSizing, Algorithm.HORIZONTAL);
    }

    @Override
    public RetainedFlowLayout child(UIComponent child) {
        this.dirty = true;
        super.child(child);
        return this;
    }

    @Override
    public RetainedFlowLayout children(Collection<? extends UIComponent> children) {
        this.dirty = true;
        super.children(children);
        return this;
    }

    @Override
    public RetainedFlowLayout child(int index, UIComponent child) {
        this.dirty = true;
        super.child(index, child);
        return this;
    }

    @Override
    public RetainedFlowLayout children(int index, Collection<? extends UIComponent> children) {
        this.dirty = true;
        super.children(index, children);
        return this;
    }

    @Override
    public RetainedFlowLayout removeChild(UIComponent child) {
        this.dirty = true;
        super.removeChild(child);
        return this;
    }

    @Override
    public RetainedFlowLayout clearChildren() {
        this.dirty = true;
        super.clearChildren();
        return this;
    }
}
