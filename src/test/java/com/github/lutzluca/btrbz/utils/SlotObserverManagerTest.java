package com.github.lutzluca.btrbz.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.lutzluca.btrbz.utils.slot.SlotClickContext;
import com.github.lutzluca.btrbz.utils.slot.SlotInputModifiers;
import com.github.lutzluca.btrbz.utils.slot.SlotObserverManager;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SlotObserverManagerTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.bootstrap();
    }

    @BeforeEach
    void clearObservers() throws Exception {
        clearStaticList(SlotObserverManager.class, "OBSERVERS");
    }

    @Test
    @DisplayName("observeClick runs only matching observers")
    void observeClickRunsOnlyMatchingObservers() {
        var matchingCalls = new AtomicInteger();
        var skippedCalls = new AtomicInteger();

        SlotObserverManager.register(new SlotObserverManager.SlotObserver() {
            @Override
            public boolean matches(SlotClickContext context) {
                return true;
            }

            @Override
            public void onClick(SlotClickContext context) {
                matchingCalls.incrementAndGet();
            }
        });
        SlotObserverManager.register(new SlotObserverManager.SlotObserver() {
            @Override
            public boolean matches(SlotClickContext context) {
                return false;
            }

            @Override
            public void onClick(SlotClickContext context) {
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

        assertEquals(1, matchingCalls.get());
        assertEquals(0, skippedCalls.get());
    }

    private static Slot createSlot() {
        var container = new SimpleContainer(1);
        return new Slot(container, 0, 0, 0);
    }

    @SuppressWarnings("unchecked")
    private static void clearStaticList(Class<?> type, String fieldName) throws Exception {
        Field field = type.getDeclaredField(fieldName);
        field.setAccessible(true);
        ((List<Object>) field.get(null)).clear();
    }
}