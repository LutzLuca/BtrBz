package com.github.lutzluca.btrbz.core;

import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import lombok.extern.slf4j.Slf4j;

/** Client-thread activation derived from the user's settings and the server's location. */
@Slf4j
public final class Activation {
    private final Consumer<Boolean> onChange;
    private final BooleanSupplier enabled;
    private final BooleanSupplier alwaysActive;
    private boolean active;
    private boolean skyBlockConfirmed;
    private long generation;

    public Activation(BooleanSupplier enabled, BooleanSupplier alwaysActive, Consumer<Boolean> onChange) {
        this.enabled = enabled;
        this.alwaysActive = alwaysActive;
        this.onChange = onChange;
    }

    public boolean isEnabled() {
        return this.enabled.getAsBoolean();
    }

    public boolean isActive() {
        return this.active;
    }

    public boolean isAlwaysActive() {
        return this.alwaysActive.getAsBoolean();
    }

    public long generation() {
        return this.generation;
    }

    /** Reevaluate after changing either external activation setting. */
    public void refresh() {
        boolean enabledNow = this.isEnabled();
        boolean alwaysActiveNow = this.isAlwaysActive();
        boolean nextActive = enabledNow && (alwaysActiveNow || this.skyBlockConfirmed);
        if (this.active != nextActive) {
            this.active = nextActive;
            this.generation++;
            log.debug(
                "Activation changed: active={}, enabled={}, alwaysActive={}, skyBlockConfirmed={}, generation={}",
                nextActive,
                enabledNow,
                alwaysActiveNow,
                this.skyBlockConfirmed,
                this.generation);
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
        if (this.isAlwaysActive()) {
            return "Enabled; always active.";
        }
        return this.isActive()
            ? "Enabled; active in SkyBlock."
            : "Enabled; inactive outside SkyBlock or awaiting confirmation.";
    }

}
