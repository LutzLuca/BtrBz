package com.github.lutzluca.btrbz.core;

import com.github.lutzluca.btrbz.core.trackedorders.TrackedOrderManager;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.BazaarData.MarketSnapshot;
import io.vavr.control.Try;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply.Product;

/** Client-thread owner of the surviving session and its market publication boundary. */
@Slf4j
public final class FeatureRuntime {
    private final Activation activation;
    private final BazaarData market;
    private final TrackedOrderManager orders;
    private final Runnable startSession;
    private final Runnable suspendFeatures;
    private final Runnable endSession;
    private State state = State.Inactive;

    public FeatureRuntime(
        Activation activation,
        BazaarData market,
        TrackedOrderManager orders,
        Runnable startSession,
        Runnable suspendFeatures,
        Runnable endSession
    ) {
        this.activation = activation;
        this.market = market;
        this.orders = orders;
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
        Try.run(() -> {
            this.orders.baselineMarket(candidate);
            this.market.installSnapshot(candidate);
            this.state = State.Active;
        }).onFailure(error -> log.error("Failed to restore Bazaar market features", error));
        if (this.isActive()) {
            this.market.notifyListeners();
        }
    }

    /** Ordinary successful replies retain their existing notification behavior. */
    public void onMarketUpdate(Map<String, Product> products) {
        if (this.isActive()) {
            this.market.onUpdate(products);
        }
    }

    public void deactivate() {
        if (!this.isRunning()) {
            return;
        }
        this.state = State.Inactive;
        this.endSession.run();
        this.orders.cancelOutstandingOrders();
        this.orders.resetTrackedOrders();
        this.market.clearMarketData();
    }

    private enum State {
        Inactive,
        Hibernating,
        Active
    }
}
