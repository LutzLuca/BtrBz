package com.github.lutzluca.btrbz.core.widgets.manager;

/** One-shot suppression for the removal caused by opening the widget manager. */
public final class SignEditScreenTransitionState {
    private boolean suspended;

    public void suspendNextRemoval() {
        this.suspended = true;
    }

    public boolean consumeSuspendedRemoval() {
        if (!this.suspended) return false;
        this.suspended = false;
        return true;
    }
}
