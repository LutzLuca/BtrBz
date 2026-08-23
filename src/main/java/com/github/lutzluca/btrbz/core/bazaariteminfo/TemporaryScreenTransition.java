package com.github.lutzluca.btrbz.core.bazaariteminfo;

import java.util.Objects;

/** One temporary child-screen transition that either resumes or abandons its owner. */
final class TemporaryScreenTransition {
    private final Runnable abandonedAction;
    private State state = State.Active;

    TemporaryScreenTransition(Runnable abandonedAction) {
        this.abandonedAction = Objects.requireNonNull(abandonedAction, "abandonedAction");
    }

    synchronized boolean suppressOwnerRemoval() {
        return this.state == State.Active;
    }

    synchronized boolean complete() {
        if (this.state != State.Active) {
            return false;
        }
        this.state = State.Completed;
        return true;
    }

    void abandon() {
        synchronized (this) {
            if (this.state != State.Active) {
                return;
            }
            this.state = State.Abandoned;
        }
        this.abandonedAction.run();
    }

    private enum State {
        Active,
        Completed,
        Abandoned
    }
}
