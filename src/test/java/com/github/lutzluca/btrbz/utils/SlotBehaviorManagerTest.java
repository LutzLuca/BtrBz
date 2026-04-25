package com.github.lutzluca.btrbz.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.lutzluca.btrbz.utils.slot.SlotBehaviorManager;
import com.github.lutzluca.btrbz.utils.slot.SlotBehaviorRegistration;
import com.github.lutzluca.btrbz.utils.slot.SlotClickContext;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SlotBehaviorManagerTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.bootstrap();
    }

    @BeforeEach
    void clearRegistrations() throws Exception {
        clearStaticList(SlotBehaviorManager.class, "REGISTRATIONS");
    }

    @Nested
    @DisplayName("applyItemOverride")
    class ApplyItemOverride {

        @Test
        void ignoresClickOnlyRegistrations() {
            var matcherCalls = new AtomicInteger();
            SlotBehaviorManager.register(
                SlotBehaviorRegistration
                    .named("click-only")
                    .matches(context -> {
                        matcherCalls.incrementAndGet();
                        return true;
                    })
                    .onClick(context -> ClickOutcome.Pass)
                    .build()
            );

                    var slot = createSlot();
            var rawItem = slot.getItem();

            var result = SlotBehaviorManager.applyItemOverride(
                new ScreenInfoHelper.ScreenInfo(null),
                new ScreenInfoHelper.ScreenInfo(null),
                slot,
                rawItem
            );

            assertSame(rawItem, result);
            assertEquals(0, matcherCalls.get());
        }

        @Test
        void returnsFirstReplacementWithoutEvaluatingLaterOverrides() {
            var secondCalls = new AtomicInteger();
            var replacement = ItemStack.EMPTY;

            SlotBehaviorManager.register(
                SlotBehaviorRegistration
                    .named("first-override")
                    .overrideItem(context -> Optional.of(replacement))
                    .build()
            );
            SlotBehaviorManager.register(
                SlotBehaviorRegistration
                    .named("second-override")
                    .matches(context -> {
                        secondCalls.incrementAndGet();
                        return true;
                    })
                    .overrideItem(context -> Optional.of(ItemStack.EMPTY))
                    .build()
            );

            var slot = createSlot();
            var result = SlotBehaviorManager.applyItemOverride(
                new ScreenInfoHelper.ScreenInfo(null),
                new ScreenInfoHelper.ScreenInfo(null),
                slot,
                slot.getItem()
            );

            assertSame(replacement, result);
            assertEquals(0, secondCalls.get());
        }
    }

    @Nested
    @DisplayName("handleClick")
    class HandleClick {

        @Test
        void aggregatesHandledAndShortCircuitsCancel() {
            var firstCalls = new AtomicInteger();
            var secondCalls = new AtomicInteger();
            var thirdCalls = new AtomicInteger();

            SlotBehaviorManager.register(
                SlotBehaviorRegistration
                    .named("handled")
                    .onClick(context -> {
                        firstCalls.incrementAndGet();
                        return ClickOutcome.Handled;
                    })
                    .build()
            );
            SlotBehaviorManager.register(
                SlotBehaviorRegistration
                    .named("cancel")
                    .onClick(context -> {
                        secondCalls.incrementAndGet();
                        return ClickOutcome.Cancel;
                    })
                    .build()
            );
            SlotBehaviorManager.register(
                SlotBehaviorRegistration
                    .named("after-cancel")
                    .onClick(context -> {
                        thirdCalls.incrementAndGet();
                        return ClickOutcome.Handled;
                    })
                    .build()
            );

                    var slot = createSlot();
            var context = new SlotClickContext(
                new ScreenInfoHelper.ScreenInfo(null),
                new ScreenInfoHelper.ScreenInfo(null),
                slot,
                slot.getItem(),
                slot.getItem().copy(),
                0,
                ClickType.PICKUP,
                false,
                false,
                false
            );

            var outcome = SlotBehaviorManager.handleClick(context);

            assertEquals(ClickOutcome.Cancel, outcome);
            assertEquals(1, firstCalls.get());
            assertEquals(1, secondCalls.get());
            assertEquals(0, thirdCalls.get());
        }
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