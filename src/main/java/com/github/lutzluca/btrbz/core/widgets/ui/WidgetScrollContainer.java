package com.github.lutzluca.btrbz.core.widgets.ui;

import com.mojang.blaze3d.platform.InputConstants;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Size;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import net.minecraft.client.input.MouseButtonEvent;
import org.jetbrains.annotations.Nullable;

/** A retained widget scroll container which owns its scroll and thumb-capture state. */
public final class WidgetScrollContainer<C extends UIComponent> extends ScrollContainer<C> {
    private final RetainedScrollState retainedScroll = new RetainedScrollState();
    private boolean interactive;
    private boolean thumbCaptured;
    private long retainedVisibleUntil;

    public WidgetScrollContainer(
        Sizing horizontalSizing,
        Sizing verticalSizing,
        C child,
        boolean interactive
    ) {
        super(ScrollDirection.VERTICAL, horizontalSizing, verticalSizing, child);
        this.interactive = interactive;
    }

    public void interactive(boolean interactive) {
        this.interactive = interactive;
        if (!interactive) this.thumbCaptured = false;
    }

    @Override
    public void layout(Size space) {
        super.layout(space);
        // Reconciliation briefly lays out an empty child; retain the last user-owned offset across that pass.
        double restoredOffset = this.retainedScroll.restore(this.maxScroll);
        this.scrollOffset = restoredOffset;
        this.currentScrollPosition = restoredOffset;
        this.scrollbaring = this.interactive && this.thumbCaptured;
        this.lastScrollbarInteractTime = this.interactive ? this.retainedVisibleUntil : 0L;
        this.updateChildPosition();
    }

    @Override
    protected void parentUpdate(float delta, int mouseX, int mouseY) {
        super.parentUpdate(delta, mouseX, mouseY);
        this.updateChildPosition();
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        this.scrollbaring = this.interactive && this.thumbCaptured;
        if (!this.interactive) this.lastScrollbarInteractTime = 0L;
        super.draw(graphics, mouseX, mouseY, partialTicks, delta);
        this.rememberState();
    }

    @Override
    public boolean canFocus(FocusSource source) {
        return this.interactive && super.canFocus(source);
    }

    @Override
    public boolean onMouseScroll(double mouseX, double mouseY, double amount) {
        if (!this.interactive) return false;
        boolean handled = super.onMouseScroll(mouseX, mouseY, amount);
        this.rememberState();
        return handled;
    }

    @Override
    public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
        if (!this.interactive) return false;
        double absoluteX = this.x + click.x();
        double absoluteY = this.y + click.y();
        if (this.isInScrollbar(absoluteX, absoluteY)) {
            if (click.button() == InputConstants.MOUSE_BUTTON_LEFT && this.isInThumb(absoluteX, absoluteY)) {
                this.thumbCaptured = true;
                this.scrollbaring = true;
                this.retainedVisibleUntil = System.currentTimeMillis() + 1500L;
                this.lastScrollbarInteractTime = this.retainedVisibleUntil;
            }
            return true;
        }
        return super.onMouseDown(click, doubled);
    }

    @Override
    public boolean onMouseDrag(MouseButtonEvent click, double deltaX, double deltaY) {
        if (!this.interactive) return false;
        if (this.thumbCaptured) {
            this.scrollbaring = true;
            super.onMouseDrag(click, deltaX, deltaY);
            this.updateChildPosition();
            this.rememberState();
            return true;
        }
        return super.onMouseDrag(click, deltaX, deltaY);
    }

    @Override
    public boolean onMouseUp(MouseButtonEvent click) {
        if (!this.interactive) return false;
        if (this.thumbCaptured) {
            this.thumbCaptured = false;
            this.scrollbaring = false;
            this.retainedVisibleUntil = System.currentTimeMillis() + 1500L;
            this.lastScrollbarInteractTime = this.retainedVisibleUntil;
            return true;
        }
        return super.onMouseUp(click);
    }

    @Override
    public @Nullable UIComponent childAt(int x, int y) {
        return this.interactive ? super.childAt(x, y) : null;
    }

    @Override
    protected boolean isInScrollbar(double mouseX, double mouseY) {
        return this.isPointerOverScrollbar(mouseX, mouseY);
    }

    public boolean isPointerOverScrollbar(double mouseX, double mouseY) {
        if (!this.interactive || this.maxScroll <= 0 || !this.isInBoundingBox(mouseX, mouseY)) return false;
        var padding = this.padding.get();
        int stripStart = this.x + this.width - padding.right() - this.scrollbarThiccness;
        return mouseX >= stripStart;
    }

    public boolean thumbCaptured() {
        return this.interactive && this.thumbCaptured;
    }

    public double scrollOffset() {
        return this.retainedScroll.offset();
    }

    public void scrollOffset(double offset) {
        this.retainedScroll.remember(offset);
        double restoredOffset = this.retainedScroll.restore(this.maxScroll);
        this.scrollOffset = restoredOffset;
        this.currentScrollPosition = restoredOffset;
        this.updateChildPosition();
    }

    private boolean isInThumb(double mouseX, double mouseY) {
        if (!this.isInScrollbar(mouseX, mouseY)) return false;
        var padding = this.padding.get();
        double contentHeight = this.height - padding.vertical();
        double thumbTop = this.y + padding.top()
            + (this.currentScrollPosition / this.maxScroll) * (contentHeight - this.lastScrollbarLength);
        return mouseY >= thumbTop && mouseY < thumbTop + this.lastScrollbarLength;
    }

    public void scrollByProgress(double delta) {
        if (!this.interactive) return;
        double progress = this.maxScroll <= 0 ? 0.0 : this.scrollOffset / this.maxScroll;
        double targetOffset = this.maxScroll * Math.max(0.0, Math.min(1.0, progress + delta));
        this.scrollOffset = targetOffset;
        this.currentScrollPosition = targetOffset;
        this.updateChildPosition();
        this.rememberState();
    }

    public void flashScrollbar() {
        if (!this.interactive) return;
        this.retainedVisibleUntil = System.currentTimeMillis() + 1250L;
        this.lastScrollbarInteractTime = this.retainedVisibleUntil;
    }

    private void updateChildPosition() {
        int topInset = this.padding.get().top() + this.child.margins().get().top();
        this.child.updateY(this.y + topInset - (int) this.currentScrollPosition);
    }

    private void rememberState() {
        if (!this.interactive) return;
        this.retainedScroll.remember(this.scrollOffset);
        this.retainedVisibleUntil = this.lastScrollbarInteractTime;
    }
}
