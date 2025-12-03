package com.github.lutzluca.btrbz.utils;

import io.vavr.control.Try;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

@Slf4j
public final class ClientTickDispatcher {

    private static final List<ClientTickEvents.EndTick> LISTENERS = new CopyOnWriteArrayList<>();
    private static final Queue<ScheduledTask> TASKS = new ConcurrentLinkedDeque<>();

    static {
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickDispatcher::onEndTick);
    }


    private static void onEndTick(Minecraft client) {
        LISTENERS.forEach(listener -> Try
            .run(() -> listener.onEndTick(client))
            .onFailure(err -> log.warn("Exception in client end tick listener", err)));

        var it = TASKS.iterator();
        while (it.hasNext()) {
            var task = it.next();
            if (--task.ticks <= 0) {
                it.remove();
                Try
                    .run(() -> task.callback.accept(client))
                    .onFailure(err -> log.warn("Exception in client tick task", err));
            }
        }
    }

    public static void register(ClientTickEvents.EndTick listener) {
        LISTENERS.add(listener);
    }

    public static void unregister(ClientTickEvents.EndTick listener) {
        LISTENERS.remove(listener);
    }

    public static void submit(Consumer<Minecraft> task) {
        TASKS.add(new ScheduledTask(0, task));
    }

    public static void submit(Consumer<Minecraft> task, int ticks) {
        TASKS.add(new ScheduledTask(ticks, task));
    }


    @AllArgsConstructor
    private static class ScheduledTask {

        int ticks;
        Consumer<Minecraft> callback;
    }
}
