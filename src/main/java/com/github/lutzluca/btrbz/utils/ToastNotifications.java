package com.github.lutzluca.btrbz.utils;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Transient, passive BtrBz toasts. Content is supplied by the caller at the event. */
public final class ToastNotifications {

    private final BooleanSupplier active;
    private final LongSupplier generation;
    private final AtomicLong session = new AtomicLong();

    public ToastNotifications(BooleanSupplier active, LongSupplier generation) {
        this.active = Objects.requireNonNull(active, "active cannot be null");
        this.generation = Objects.requireNonNull(generation, "generation cannot be null");
    }

    public long session() {
        return this.session.get();
    }

    public void show(Component title, List<Component> lines, Optional<ItemStack> icon) {
        Objects.requireNonNull(title, "title cannot be null");
        var preparedTitle = title.copy();
        List<Component> preparedLines = lines.stream().<Component>map(Component::copy).toList();
        var preparedIcon = icon.map(ItemStack::copy);
        long currentSession = this.session.get();
        long currentGeneration = this.generation.getAsLong();
        Minecraft.getInstance().execute(() -> {
            var client = Minecraft.getInstance();
            if (this.session.get() != currentSession || !this.active.getAsBoolean()
                || this.generation.getAsLong() != currentGeneration
                || client.player == null) {
                return;
            }
            toastManager(client).addToast(new BtrBzToast(client.font, preparedTitle, preparedLines, preparedIcon));
        });
    }

    /** Invalidates queued client work and removes only this mod's queued or visible toasts. */
    public void clear() {
        this.session.incrementAndGet();
        Minecraft.getInstance().execute(() -> {
            var manager = toastManager(Minecraft.getInstance());
            ((BtrBzToastManager) manager).btrbz$removeToasts();
        });
    }

    private static ToastManager toastManager(Minecraft client) {
        //? if <26.2 {
        return client.getToastManager();
        //?} else {
        /*return client.gui.toastManager();
        *///?}
    }
}
