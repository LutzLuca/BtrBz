package com.github.lutzluca.btrbz.core;

import java.util.Optional;
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
        this.changeConnection(true);
    }

    void endConnection() {
        this.changeConnection(false);
    }

    private void changeConnection(boolean connected) {
        // Configuration INIT can run on Netty too. Invalidate queued packets immediately,
        // then change feature state on the client thread.
        long generation = this.connectionGeneration.incrementAndGet();
        this.clientExecutor.accept(() -> {
            if (generation == this.connectionGeneration.get()) {
                this.connected = connected;
                log.debug("Hypixel connection changed: connected={}, generation={}", connected, generation);
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
