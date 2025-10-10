package com.github.lutzluca.btrbz.utils;

import java.util.ArrayDeque;
import java.util.Deque;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

@Slf4j
public class MessageQueue {

    private static final Deque<Entry> messages = new ArrayDeque<>();

    static {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            MessageQueue.flush(client);
        });
    }

    public static void sendOrQueue(String message) { sendOrQueue(message, Level.Info); }

    public static void sendOrQueue(String message, Level level) {
        var msg = Notifier.prefix().append(Text.literal(message).formatted(level.color));

        if (Notifier.notifyPlayer(msg)) {
            return;
        }

        synchronized (messages) {
            messages.addLast(new Entry(message, level));
        }
        log.debug("Queued message: '{}'", message);
    }

    public static void flush(MinecraftClient client) {
        log.info("Flushing queued messages");

        if (client.player == null) {
            return;
        }

        synchronized (messages) {
            if (messages.isEmpty()) {
                return;
            }

            messages.forEach(entry -> {
                var msg = Notifier
                    .prefix()
                    .append(Text.literal(entry.msg).formatted(entry.level.color));

                client.player.sendMessage(msg, false);
            });

            log.info("Flushed {} queued messages to player", messages.size());
            messages.clear();
        }
    }

    @AllArgsConstructor
    public enum Level {
        Info(Formatting.WHITE), Warn(Formatting.YELLOW), Error(Formatting.RED);

        public final Formatting color;
    }

    private record Entry(String msg, Level level) { }
}
