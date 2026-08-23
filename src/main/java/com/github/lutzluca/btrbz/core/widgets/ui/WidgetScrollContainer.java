package com.github.lutzluca.btrbz.core.widgets.ui;

import com.github.lutzluca.btrbz.core.widgets.WidgetMath;
import com.mojang.blaze3d.platform.InputConstants;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Size;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.jetbrains.annotations.Nullable;

/** A retained widget scroll container which owns its scroll and thumb-capture state. */
public final class WidgetScrollContainer<C extends UIComponent> extends ScrollContainer<C> {
    private static final double WHEEL_SCROLL_DISTANCE = 15.0;
    private static final double SMOOTH_SCROLL_DURATION_SECONDS = 0.25;
    private static final double TICKS_PER_SECOND = 20.0;
    private static final int MINIMUM_SCROLLBAR_LENGTH = 8;

    private final RetainedScrollState retainedScroll = new RetainedScrollState();

    private boolean interactive;
    private boolean thumbCaptured;
    private long retainedVisibleUntil;

    private double smoothScrollTimeRemaining;

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

        if (!interactive) {
            this.thumbCaptured = false;
        }
    }

    @Override
    public void layout(Size space) {
        super.layout(space);

        // Reconciliation briefly lays out an empty child; retain the last user-owned offset across that pass.
        double restoredOffset = this.retainedScroll.restore(this.maxScroll);

        this.scrollOffset = restoredOffset;
        this.currentScrollPosition = restoredOffset;
        this.smoothScrollTimeRemaining = 0.0;
        this.scrollbaring = this.interactive && this.thumbCaptured;
        this.lastScrollbarInteractTime = this.interactive ? this.retainedVisibleUntil : 0L;
        this.updateChildPosition();
    }

    @Override
    protected void parentUpdate(float delta, int mouseX, int mouseY) {
        double previousPosition = this.currentScrollPosition;

        super.parentUpdate(delta, mouseX, mouseY);

        double elapsedSeconds = Math.max(0.0, delta) / TICKS_PER_SECOND;

        if (elapsedSeconds >= this.smoothScrollTimeRemaining) {
            this.currentScrollPosition = this.scrollOffset;
            this.smoothScrollTimeRemaining = 0.0;
        } else {
            double progress = elapsedSeconds / this.smoothScrollTimeRemaining;
            this.currentScrollPosition = previousPosition
                + (this.scrollOffset - previousPosition) * progress;
            this.smoothScrollTimeRemaining -= elapsedSeconds;
        }

        this.updateChildPosition();
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        this.scrollbaring = this.interactive && this.thumbCaptured;

        if (!this.interactive) {
            this.lastScrollbarInteractTime = 0L;
        }

        this.fixedScrollbarLength = this.resolveScrollbarLength();
        super.draw(graphics, mouseX, mouseY, partialTicks, delta);
        this.rememberState();
    }

    @Override
    public boolean canFocus(FocusSource source) {
        return this.interactive && super.canFocus(source);
    }

    @Override
    public boolean onKeyPress(KeyEvent input) {
        // Runtime widget lists use pointer scrolling; leave keyboard input to the host screen.
        return false;
    }

    @Override
    public boolean onMouseScroll(double mouseX, double mouseY, double amount) {
        if (!this.interactive) {
            return false;
        }

        if (this.child.onMouseScroll(this.x + mouseX - this.child.x(), this.y + mouseY - this.child.y(), amount)) {
            return true;
        }

        if (!ScrollWheelPolicy.wouldMove(this.scrollOffset, this.maxScroll, amount, WHEEL_SCROLL_DISTANCE)) {
            return false;
        }
        this.scrollBy(-amount * WHEEL_SCROLL_DISTANCE, false, true);
        this.rememberState();
        return true;
    }

    @Override
    protected void scrollBy(double offset, boolean instant, boolean showScrollbar) {
        double targetOffset = WidgetMath.clamp(this.scrollOffset + offset, 0.0, this.maxScroll);
        boolean changed = targetOffset != this.scrollOffset;
        this.scrollOffset = targetOffset;

        if (instant) {
            this.currentScrollPosition = this.scrollOffset;
            this.smoothScrollTimeRemaining = 0.0;
        } else if (changed) {
            this.smoothScrollTimeRemaining = SMOOTH_SCROLL_DURATION_SECONDS;
        }

        if (showScrollbar && changed) {
            this.lastScrollbarInteractTime = System.currentTimeMillis() + 1250L;
        }
    }

    @Override
    public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
        if (!this.interactive) {
            return false;
        }

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
        if (!this.interactive) {
            return false;
        }

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
        if (!this.interactive) {
            return false;
        }

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
        if (!this.interactive || this.maxScroll <= 0 || !this.isInBoundingBox(mouseX, mouseY)) {
            return false;
        }

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
        this.smoothScrollTimeRemaining = 0.0;
        this.updateChildPosition();
    }

    private boolean isInThumb(double mouseX, double mouseY) {
        if (!this.isInScrollbar(mouseX, mouseY)) {
            return false;
        }

        var padding = this.padding.get();
        double contentHeight = this.height - padding.vertical();
        double thumbTop = this.y + padding.top()
            + (this.currentScrollPosition / this.maxScroll) * (contentHeight - this.lastScrollbarLength);
        return mouseY >= thumbTop && mouseY < thumbTop + this.lastScrollbarLength;
    }

    public void scrollByProgress(double delta) {
        if (!this.interactive) {
            return;
        }

        double progress = this.maxScroll <= 0 ? 0.0 : this.scrollOffset / this.maxScroll;
        double targetOffset = this.maxScroll * WidgetMath.unit(progress + delta);
        this.scrollOffset = targetOffset;
        this.currentScrollPosition = targetOffset;
        this.smoothScrollTimeRemaining = 0.0;
        this.updateChildPosition();
        this.rememberState();
    }

    public void flashScrollbar() {
        if (!this.interactive) {
            return;
        }

        this.retainedVisibleUntil = System.currentTimeMillis() + 1250L;
        this.lastScrollbarInteractTime = this.retainedVisibleUntil;
    }

    private void updateChildPosition() {
        int topInset = this.padding.get().top() + this.child.margins().get().top();
        this.child.updateY(this.y + topInset - (int) this.currentScrollPosition);
    }

    private void rememberState() {
        if (!this.interactive) {
            return;
        }

        this.retainedScroll.remember(this.scrollOffset);
        this.retainedVisibleUntil = this.lastScrollbarInteractTime;
    }

    private int resolveScrollbarLength() {
        int trackLength = Math.max(0, this.height - this.padding.get().vertical());

        if (trackLength == 0) {
            return 0;
        }

        int calculatedLength = this.childSize <= 0
            ? trackLength
            : (int) Math.min(Math.floor((double) this.height / this.childSize * trackLength), trackLength);

        return WidgetMath.clamp(calculatedLength, Math.min(MINIMUM_SCROLLBAR_LENGTH, trackLength), trackLength);
    }
}
