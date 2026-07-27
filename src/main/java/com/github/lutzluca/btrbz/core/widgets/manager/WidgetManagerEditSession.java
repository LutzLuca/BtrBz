package com.github.lutzluca.btrbz.core.widgets.manager;

import java.util.Objects;

/** Owns the manager's single commit boundary independently from the screen lifecycle API. */
final class WidgetManagerEditSession implements AutoCloseable {
    private final Runnable save;
    private boolean dirty;
    private boolean closed;

    WidgetManagerEditSession(Runnable save) {
        this.save = Objects.requireNonNull(save, "save");
    }

    void markDirty() {
        if (this.closed) throw new IllegalStateException("Widget manager edit session is closed");
        this.dirty = true;
    }

    @Override
    public void close() {
        if (this.closed) return;
        this.closed = true;
        if (this.dirty) this.save.run();
    }
}
