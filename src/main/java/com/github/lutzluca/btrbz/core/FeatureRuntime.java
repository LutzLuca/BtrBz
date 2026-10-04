package com.github.lutzluca.btrbz.core;

import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.BazaarData.MarketSnapshot;
import com.github.lutzluca.btrbz.data.BazaarPoller.MarketReply;
import lombok.Getter;
import lombok.experimental.Accessors;

/** Client-thread owner of the surviving session and its market publication boundary. */
public final class FeatureRuntime {
    private final Activation activation;
    private final BazaarData market;
    private final Runnable startSession;
    private final Runnable suspendFeatures;
    private final Runnable endSession;
    private final Runnable resetSessionState;
    @Getter
    @Accessors(fluent = true)
    private long sessionGeneration;
    private State state = State.Inactive;

    public FeatureRuntime(
        Activation activation,
        BazaarData market,
        Runnable startSession,
        Runnable suspendFeatures,
        Runnable endSession,
        Runnable resetSessionState
    ) {
        this.activation = activation;
        this.market = market;
        this.startSession = startSession;
        this.suspendFeatures = suspendFeatures;
        this.endSession = endSession;
        this.resetSessionState = resetSessionState;
    }

    public boolean isRunning() {
        return this.state != State.Inactive;
    }

    public boolean isActive() {
        return this.state == State.Active;
    }

    public boolean isHibernating() {
        return this.state == State.Hibernating;
    }

    /** Invalidate old work and clear session facts without restarting shared services or market gates. */
    public void resetSession() {
        this.sessionGeneration++;
        this.resetSessionState.run();
    }

    public void activate() {
        if (this.isRunning()) {
            return;
        }
        this.sessionGeneration++;
        this.state = this.activation.isAlwaysActive() ? State.Active : State.Hibernating;
        this.startSession.run();
    }

    /**
     * Pauses features requiring current market data: quotes, order books, market-derived order
     * statuses, pricing input, protection, and their UI. Unsent automation is cancelled, while
     * session facts and passive observations/accounting are kept. Inventory-only reopening,
     * copying remaining amounts, and lore-based filled/expired highlights remain usable.
     */
    public void hibernate() {
        if (!this.isActive()) {
            return;
        }
        this.state = State.Hibernating;
        this.suspendFeatures.run();
        this.market.clearMarketData();
    }

    private void recover(MarketSnapshot candidate) {
        if (!this.isHibernating() || candidate == null || !candidate.available()) {
            return;
        }
        this.state = State.Active;
        this.market.publishSnapshot(candidate);
    }

    /** The poller validates source age before this ordered client-thread publication/recovery. */
    public void onMarketReply(MarketReply reply) {
        if (this.isHibernating()) {
            this.recover(reply.snapshot());
        } else if (this.isActive() && reply.advanced()) {
            this.market.publishSnapshot(reply.snapshot());
        }
    }

    public void deactivate() {
        if (!this.isRunning()) {
            return;
        }
        this.sessionGeneration++;
        this.state = State.Inactive;
        this.endSession.run();
        this.market.clearMarketData();
    }

    private enum State {
        Inactive,
        Hibernating,
        Active
    }
}
