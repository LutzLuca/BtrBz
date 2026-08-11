package com.github.lutzluca.btrbz.core.widgets.config;

import java.util.Objects;
import java.util.function.Consumer;

/** A replacement-aware, manager-owned binding to one persisted widget config. */
public final class WidgetConfigBinding<C> {
    private final WidgetConfigHandle<C> handle;
    private final Runnable changed;

    public WidgetConfigBinding(
        WidgetConfigHandle<C> handle,
        Runnable changed
    ) {
        this.handle = Objects.requireNonNull(handle, "handle");
        this.changed = Objects.requireNonNull(changed, "changed");
    }

    public C current() {
        return this.handle.current();
    }

    public C defaults() {
        return this.handle.defaults();
    }

    public WidgetFrameConfig frame() {
        return this.handle.frame();
    }

    public WidgetFrameConfig defaultFrame() {
        return this.handle.defaultFrame();
    }

    public void mutate(Consumer<C> mutation) {
        this.handle.mutate("widget setting changed", mutation);
        this.changed.run();
    }

    public void resetPreferences() {
        this.handle.resetPreferences("widget preferences reset");
        this.changed.run();
    }
}
