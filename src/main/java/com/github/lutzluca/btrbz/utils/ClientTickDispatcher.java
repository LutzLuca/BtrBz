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

    private static final List<TickRegistration> LISTENERS = new CopyOnWriteArrayList<>();
    private static final Queue<ScheduledTask> TASKS = new ConcurrentLinkedDeque<>();

    static {
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickDispatcher::onEndTick);
    }


    private static void onEndTick(Minecraft client) {
        LISTENERS.forEach(registration -> Try
            .run(() -> registration.listener.onEndTick(client))
            .onFailure(err -> log.warn("Exception in client end tick listener", err)));

        var it = TASKS.iterator();
        while (it.hasNext()) {
            var task = it.next();

            switch (task) {
                case OneShotTask oneShotTask -> {
                    if (--oneShotTask.ticks <= 0) {
                        it.remove();
                        runTask(oneShotTask.callback, client);
                    }
                }
                case IntervalTask intervalTask -> {
                    if (--intervalTask.ticks <= 0) {
                        intervalTask.ticks = intervalTask.intervalTicks;
                        runTask(intervalTask.callback, client);
                    }
                }
            }
        }
    }

    private static void runTask(Consumer<Minecraft> task, Minecraft client) {
        Try
            .run(() -> task.accept(client))
            .onFailure(err -> log.warn("Exception in client tick task", err));
    }

    public static TaskHandle onEachTick(ClientTickEvents.EndTick listener) {
        var registration = new TickRegistration(listener);
        LISTENERS.add(registration);
        return () -> LISTENERS.remove(registration);
    }

    public static TaskHandle scheduleEvery(
        int ticks,
        Consumer<Minecraft> task
    ) {
        if (ticks <= 0) {
            throw new IllegalArgumentException("ticks must be positive");
        }

        var scheduled = new IntervalTask(ticks, ticks, task);
        TASKS.add(scheduled);
        return () -> TASKS.remove(scheduled);
    }

    public static void scheduleAfter(Consumer<Minecraft> task, int ticks) {
        if (ticks <= 0) {
            throw new IllegalArgumentException("ticks must be positive");
        }

        TASKS.add(new OneShotTask(ticks, task));
    }

    private sealed interface ScheduledTask permits OneShotTask, IntervalTask {}

    @AllArgsConstructor
    private static final class TickRegistration {
        private final ClientTickEvents.EndTick listener;
    }

    @AllArgsConstructor
    private static final class OneShotTask implements ScheduledTask {
        private int ticks;
        private final Consumer<Minecraft> callback;
    }

    @AllArgsConstructor
    private static final class IntervalTask implements ScheduledTask {
        private int ticks;
        private final int intervalTicks;
        private final Consumer<Minecraft> callback;
    }

    @FunctionalInterface
    public interface TaskHandle extends AutoCloseable {
        @Override
        void close();
    }
}
