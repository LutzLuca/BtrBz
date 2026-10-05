package com.github.lutzluca.btrbz.core.runtime;

import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/** Client-thread activation derived from the user's settings and the server's location. */
@Slf4j
public final class Activation {
    private final Consumer<Boolean> onChange;
    private final BooleanSupplier enabled;
    private final BooleanSupplier alwaysActive;
    @Getter
    private boolean active;
    private boolean skyBlockConfirmed;

    public Activation(BooleanSupplier enabled, BooleanSupplier alwaysActive, Consumer<Boolean> onChange) {
        this.enabled = enabled;
        this.alwaysActive = alwaysActive;
        this.onChange = onChange;
    }

    public boolean isEnabled() {
        return this.enabled.getAsBoolean();
    }

    public boolean isAlwaysActive() {
        return this.alwaysActive.getAsBoolean();
    }

    /** Reevaluate after changing either external activation setting. */
    public void refresh() {
        boolean enabledNow = this.isEnabled();
        boolean alwaysActiveNow = this.isAlwaysActive();
        boolean nextActive = enabledNow && (alwaysActiveNow || this.skyBlockConfirmed);
        if (this.active != nextActive) {
            this.active = nextActive;
            log.debug(
                "Activation changed: active={}, enabled={}, alwaysActive={}, skyBlockConfirmed={}",
                nextActive,
                enabledNow,
                alwaysActiveNow,
                this.skyBlockConfirmed);
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
