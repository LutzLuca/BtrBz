package com.github.lutzluca.btrbz.utils;

import java.util.ArrayDeque;
import java.util.Deque;
import lombok.extern.slf4j.Slf4j;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import com.github.lutzluca.btrbz.core.appearance.UiColor;
import com.github.lutzluca.btrbz.core.ui.UiStyles;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

@Slf4j
public class MessageQueue {

    private static final Deque<Entry> MESSAGES = new ArrayDeque<>();

    static {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> MessageQueue.flush(client));
    }

    public static void sendOrQueue(String message) {
        sendOrQueue(message, Level.Info);
    }

    public static void sendOrQueue(String message, Level level) {
        var msg = Notifier.prefix().append(Component.literal(message).withStyle(UiStyles.color(level.role)));

        if (Notifier.notifyPlayer(msg)) {
            return;
        }

        synchronized (MESSAGES) {
            MESSAGES.addLast(new Entry(message, level));
        }
        log.debug("Queued message: '{}'", message);
    }

    public static void flush(Minecraft client) {
        log.info("Flushing queued messages");

        if (client.player == null) {
            return;
        }

        synchronized (MESSAGES) {
            if (MESSAGES.isEmpty()) {
                return;
            }

            MESSAGES.forEach(entry -> {
                var msg = Notifier
                    .prefix()
                    .append(Component.literal(entry.msg).withStyle(UiStyles.color(entry.level.role)));

                client.player.sendSystemMessage(msg);
            });

            log.info("Flushed {} queued messages to player", MESSAGES.size());
            MESSAGES.clear();
        }
    }

    public enum Level {
        Info(UiColor.TextPrimary),
        Warn(UiColor.Warning),
        Error(UiColor.Error);

        private final UiColor role;

        Level(UiColor role) {
            this.role = role;
        }

    }

    private record Entry(String msg, Level level) {}
}
