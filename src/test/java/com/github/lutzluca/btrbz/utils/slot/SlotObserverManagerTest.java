package com.github.lutzluca.btrbz.utils.slot;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import com.github.lutzluca.btrbz.utils.MinecraftTestBootstrap;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;

class SlotObserverManagerTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.bootstrap();
    }

    @BeforeEach
    void clearObservers() {
        SlotObserverManager.clearObservers();
    }

    @Test
    @DisplayName("observeClick runs only matching observers")
    void observeClickRunsOnlyMatchingObservers() {
        var matchingCalls = new AtomicInteger();
        var skippedCalls = new AtomicInteger();

        SlotObserverManager.register(new SlotObserverManager.SlotObserver() {
            @Override
            public boolean matches(SlotClickContext ctx) {
                return true;
            }

            @Override
            public void onClick(SlotClickContext ctx) {
                matchingCalls.incrementAndGet();
            }
        });
        SlotObserverManager.register(new SlotObserverManager.SlotObserver() {
            @Override
            public boolean matches(SlotClickContext ctx) {
                return false;
            }

            @Override
            public void onClick(SlotClickContext ctx) {
                skippedCalls.incrementAndGet();
            }
        });

        SlotObserverManager.observeClick(new SlotClickContext(
            new ScreenInfoHelper.ScreenInfo(null),
            new ScreenInfoHelper.ScreenInfo(null),
            createSlot(),
            ItemStack.EMPTY,
            ItemStack.EMPTY,
            0,
            ClickType.PICKUP,
            new SlotInputModifiers(false, false, false)
        ));

        Assertions.assertEquals(1, matchingCalls.get());
        Assertions.assertEquals(0, skippedCalls.get());
    }

    @Test
    @DisplayName("observeClick notifies all matching observers in registration order")
    void observeClickNotifiesAllMatchingObserversInRegistrationOrder() {
        var calls = new ArrayList<String>();

        SlotObserverManager.register(new SlotObserverManager.SlotObserver() {
            @Override
            public boolean matches(SlotClickContext ctx) {
                return true;
            }

            @Override
            public void onClick(SlotClickContext ctx) {
                calls.add("first");
            }
        });
        SlotObserverManager.register(new SlotObserverManager.SlotObserver() {
            @Override
            public boolean matches(SlotClickContext ctx) {
                return true;
            }

            @Override
            public void onClick(SlotClickContext ctx) {
                calls.add("second");
            }
        });

        SlotObserverManager.observeClick(new SlotClickContext(
            new ScreenInfoHelper.ScreenInfo(null),
            new ScreenInfoHelper.ScreenInfo(null),
            createSlot(),
            ItemStack.EMPTY,
            ItemStack.EMPTY,
            0,
            ClickType.PICKUP,
            SlotInputModifiers.none()
        ));

        Assertions.assertEquals(List.of("first", "second"), calls);
    }

    @Test
    @DisplayName("observeClick isolates throwing observers")
    void observeClickIsolatesThrowingObservers() {
        var firstCalls = new AtomicInteger();
        var throwingMatchCalls = new AtomicInteger();
        var addedObserveCalls = new AtomicInteger();
        var throwingClickCalls = new AtomicInteger();
        var lastCalls = new AtomicInteger();

        SlotObserverManager.register(new SlotObserverManager.SlotObserver() {
            @Override
            public boolean matches(SlotClickContext ctx) {
                return true;
            }

            @Override
            public void onClick(SlotClickContext ctx) {
                firstCalls.incrementAndGet();
            }
        });
        SlotObserverManager.register(new SlotObserverManager.SlotObserver() {
            @Override
            public boolean matches(SlotClickContext ctx) {
                throwingMatchCalls.incrementAndGet();
                throw new IllegalStateException("match failure");
            }

            @Override
            public void onClick(SlotClickContext ctx) {
                addedObserveCalls.incrementAndGet();
            }
        });
        SlotObserverManager.register(new SlotObserverManager.SlotObserver() {
            @Override
            public boolean matches(SlotClickContext ctx) {
                return true;
            }

            @Override
            public void onClick(SlotClickContext ctx) {
                throwingClickCalls.incrementAndGet();
                throw new IllegalStateException("click failure");
            }
        });
        SlotObserverManager.register(new SlotObserverManager.SlotObserver() {
            @Override
            public boolean matches(SlotClickContext ctx) {
                return true;
            }

            @Override
            public void onClick(SlotClickContext ctx) {
                lastCalls.incrementAndGet();
            }
        });

        Assertions.assertDoesNotThrow(() -> SlotObserverManager.observeClick(new SlotClickContext(
            new ScreenInfoHelper.ScreenInfo(null),
            new ScreenInfoHelper.ScreenInfo(null),
            createSlot(),
            ItemStack.EMPTY,
            ItemStack.EMPTY,
            0,
            ClickType.PICKUP,
            new SlotInputModifiers(false, false, false)
        )));

        Assertions.assertEquals(1, firstCalls.get());
        Assertions.assertEquals(1, throwingMatchCalls.get());
        Assertions.assertEquals(0, addedObserveCalls.get());
        Assertions.assertEquals(1, throwingClickCalls.get());
        Assertions.assertEquals(1, lastCalls.get());
    }

    private static Slot createSlot() {
        var container = new SimpleContainer(1);
        return new Slot(container, 0, 0, 0);
    }
}