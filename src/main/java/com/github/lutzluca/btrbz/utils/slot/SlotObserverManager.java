package com.github.lutzluca.btrbz.utils.slot;

import io.vavr.control.Try;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class SlotObserverManager {

    private static final List<SlotObserver> OBSERVERS = new ArrayList<>();

    private SlotObserverManager() { }

    public static void register(SlotObserver observer) {
        OBSERVERS.add(observer);
    }

    static void clearObservers() {
        OBSERVERS.clear();
    }

    public static void observeClick(SlotClickContext ctx) {
        if (ctx.slot() == null) {
            return;
        }

        for (SlotObserver observer : OBSERVERS) {
            var matches = Try.of(() -> observer.matches(ctx))
                .onFailure(err -> log.error(
                    "Slot observer '{}' failed while matching screen '{}' slot '{}' action '{}'",
                    observer.getClass().getName(),
                    ctx.currInfo().containerName().orElse("<unknown>"),
                    ctx.containerSlot(),
                    ctx.actionType(),
                    err
                ))
                .getOrElse(false);

            if (!matches) {
                continue;
            }

            Try.run(() -> observer.onClick(ctx))
                .onFailure(err -> log.error(
                    "Slot observer '{}' failed while handling screen '{}' slot '{}' action '{}'",
                    observer.getClass().getName(),
                    ctx.currInfo().containerName().orElse("<unknown>"),
                    ctx.containerSlot(),
                    ctx.actionType(),
                    err
                ));
        }
    }

    public interface SlotObserver {

        boolean matches(SlotClickContext ctx);

        void onClick(SlotClickContext ctx);
    }
}