package com.github.lutzluca.btrbz.utils;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Transient, passive BtrBz toasts. Content is supplied by the caller at the event. */
public final class ToastNotifications {

    private final BooleanSupplier active;
    private final AtomicLong session = new AtomicLong();

    public ToastNotifications(BooleanSupplier active) {
        this.active = Objects.requireNonNull(active, "active cannot be null");
    }

    public void show(Component message) {
        this.show(message, List.of(), Optional.empty());
    }

    public void show(Component title, List<Component> lines, Optional<ItemStack> icon) {
        Objects.requireNonNull(title, "title cannot be null");
        var preparedTitle = title.copy();
        List<Component> preparedLines = lines.stream().<Component>map(Component::copy).toList();
        var preparedIcon = icon.map(ItemStack::copy);
        long currentSession = this.session.get();

        Minecraft.getInstance().execute(() -> {
            var client = Minecraft.getInstance();
            BooleanSupplier valid = () -> this.session.get() == currentSession && this.active.getAsBoolean()
                && client.player != null;
            if (!valid.getAsBoolean()) {
                return;
            }
            toastManager(client)
                .addToast(new BtrBzToast(client.font, preparedTitle, preparedLines, preparedIcon, valid));
        });
    }

    /** Invalidates pending submissions and lets existing toasts expire through Minecraft's normal lifecycle. */
    public void invalidate() {
        this.session.incrementAndGet();
    }

    private static ToastManager toastManager(Minecraft client) {
        //? if <26.2 {
        return client.getToastManager();
        //?} else {
        /*return client.gui.toastManager();
        *///?}
    }
}
