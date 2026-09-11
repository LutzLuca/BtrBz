package com.github.lutzluca.btrbz.core;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.hypixel.data.type.GameType;
import net.hypixel.data.type.ServerType;
import net.hypixel.modapi.HypixelModAPI;
import net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket;
import net.minecraft.client.Minecraft;

@Slf4j
public final class SkyBlockDetector {
    private final Activation activation;
    private final Consumer<Runnable> clientExecutor;
    private final AtomicBoolean confirmationResetPending = new AtomicBoolean(true);
    private final AtomicLong connectionGeneration = new AtomicLong();
    private boolean connected;

    public SkyBlockDetector(Activation activation) {
        this(activation, Minecraft.getInstance()::execute);
    }

    SkyBlockDetector(Activation activation, Consumer<Runnable> clientExecutor) {
        this.activation = activation;
        this.clientExecutor = clientExecutor;
    }

    public void register() {
        ClientConfigurationConnectionEvents.INIT.register((handler, client) -> this.beginConnection());
        ClientConfigurationConnectionEvents.DISCONNECT.register((handler, client) -> this.endConnection());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> this.endConnection());

        var api = HypixelModAPI.getInstance();
        api.createHandler(ClientboundLocationPacket.class, packet -> this.onLocation(packet.getServerType()))
            .onError(error -> {
                log.warn("Hypixel location update failed: {}", error);
                this.onLocation(Optional.empty());
            });
        api.subscribeToEventPacket(ClientboundLocationPacket.class);
    }

    void beginConnection() {
        // Configuration INIT can run on Netty and can repeat during a Hypixel server transfer.
        // Advance the generation immediately so queued location packets from the previous
        // configuration cannot affect the new one, while retaining confirmed SkyBlock state
        // until a location packet says that the game type changed.
        long generation = this.connectionGeneration.incrementAndGet();
        this.clientExecutor.accept(() -> {
            if (generation == this.connectionGeneration.get()) {
                this.connected = true;
                boolean resetConfirmation = this.confirmationResetPending.getAndSet(false);
                log.debug(
                    "Hypixel configuration initialized: resetConfirmation={}, generation={}",
                    resetConfirmation,
                    generation);
                if (resetConfirmation) {
                    this.activation.setSkyBlockConfirmed(false);
                }
            }
        });
    }

    void endConnection() {
        this.confirmationResetPending.set(true);
        long generation = this.connectionGeneration.incrementAndGet();
        this.clientExecutor.accept(() -> {
            if (generation == this.connectionGeneration.get()) {
                this.connected = false;
                log.debug("Hypixel connection ended: generation={}", generation);
                this.activation.setSkyBlockConfirmed(false);
            }
        });
    }

    void onLocation(Optional<ServerType> serverType) {
        long generation = this.connectionGeneration.get();
        boolean skyBlock = serverType.orElse(null) == GameType.SKYBLOCK;
        // The official Fabric implementation also receives location packets on the configuration
        // network thread. A queued update must not outlive the connection that received it.
        this.clientExecutor.accept(() -> {
            if (this.connected && generation == this.connectionGeneration.get()) {
                log.debug(
                    "Hypixel location classified: serverType={}, skyBlock={}, connectionGeneration={}",
                    serverType.map(Object::toString).orElse("unknown"),
                    skyBlock,
                    generation);
                this.activation.setSkyBlockConfirmed(skyBlock);
            } else {
                log.trace(
                    "Ignored obsolete Hypixel location: connected={}, packetGeneration={}, currentGeneration={}",
                    this.connected,
                    generation,
                    this.connectionGeneration.get());
            }
        });
    }
}
