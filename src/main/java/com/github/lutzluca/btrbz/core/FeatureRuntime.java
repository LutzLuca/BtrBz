package com.github.lutzluca.btrbz.core;

import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.BazaarData.MarketSnapshot;
import java.util.Map;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply.Product;

/** Client-thread owner of the surviving session and its market publication boundary. */
public final class FeatureRuntime {
    private final Activation activation;
    private final BazaarData market;
    private final Runnable startSession;
    private final Runnable suspendFeatures;
    private final Runnable endSession;
    private State state = State.Inactive;

    public FeatureRuntime(
        Activation activation,
        BazaarData market,
        Runnable startSession,
        Runnable suspendFeatures,
        Runnable endSession
    ) {
        this.activation = activation;
        this.market = market;
        this.startSession = startSession;
        this.suspendFeatures = suspendFeatures;
        this.endSession = endSession;
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

    public long sessionGeneration() {
        return this.activation.generation();
    }

    public void activate() {
        if (this.isRunning()) {
            return;
        }
        this.state = State.Active;
        this.startSession.run();
    }

    public void hibernate() {
        if (!this.isActive()) {
            return;
        }
        this.state = State.Hibernating;
        this.suspendFeatures.run();
        this.market.clearMarketData();
    }

    public void recover(MarketSnapshot candidate) {
        if (!this.isHibernating() || candidate == null || !candidate.available()) {
            return;
        }
        this.state = State.Active;
        this.market.publishSnapshot(candidate);
    }

    /** Ordinary successful replies retain their existing notification behavior. */
    public void onMarketUpdate(Map<String, Product> products) {
        if (this.isActive()) {
            this.market.publishSnapshot(MarketSnapshot.fromProducts(products));
        }
    }

    public void deactivate() {
        if (!this.isRunning()) {
            return;
        }
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
