package com.github.lutzluca.btrbz.core;

import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

/** Client-thread activation: the user's setting and the server's location are independent. */
public final class Activation {
    private final Consumer<Boolean> onChange;
    private final BooleanSupplier enabled;
    private boolean active;
    private boolean skyBlockConfirmed;
    private long generation;

    public Activation(BooleanSupplier enabled, Consumer<Boolean> onChange) {
        this.enabled = enabled;
        this.onChange = onChange;
    }

    public boolean isEnabled() {
        return this.enabled.getAsBoolean();
    }

    public boolean isActive() {
        return this.active;
    }

    public long generation() {
        return this.generation;
    }

    /** Reevaluate after changing the external enabled setting. */
    public void refresh() {
        boolean nextActive = this.skyBlockConfirmed && this.isEnabled();
        if (this.active != nextActive) {
            this.active = nextActive;
            this.generation++;
            this.onChange.accept(nextActive);
        }
    }

    public void setSkyBlockConfirmed(boolean confirmed) {
        this.skyBlockConfirmed = confirmed;
        this.refresh();
    }

    public String description() {
        if (!this.isEnabled()) {
            return "Disabled manually.";
        }
        return this.isActive()
            ? "Enabled; active in SkyBlock."
            : "Enabled; inactive outside SkyBlock or awaiting confirmation.";
    }

}
