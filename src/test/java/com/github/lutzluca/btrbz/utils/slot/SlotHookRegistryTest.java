package com.github.lutzluca.btrbz.utils.slot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.Bootstrap;
import net.minecraft.SharedConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SlotHookRegistryTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void clearRegistry() throws ReflectiveOperationException {
        Field hooksField = SlotHookRegistry.class.getDeclaredField("HOOKS");
        hooksField.setAccessible(true);
        ((List<?>) hooksField.get(null)).clear();
    }

    @Nested
    @DisplayName("getDisplayStack")
    class GetDisplayStack {

        @Test
        void returnsFirstNonNullMatchingDisplayStack() {
            var rawStack = new ItemStack(Items.STONE);
            var firstDisplay = new ItemStack(Items.BOOK);
            var secondDisplay = new ItemStack(Items.PAPER);
            var context = new SlotRenderContext(createSlotView(rawStack));

            SlotHookRegistry.register(new SlotHook() {
                @Override
                public boolean matches(SlotView view) {
                    return true;
                }

                @Override
                public ItemStack createDisplayStack(SlotRenderContext ignored) {
                    return firstDisplay;
                }
            });
            SlotHookRegistry.register(new SlotHook() {
                @Override
                public boolean matches(SlotView view) {
                    return true;
                }

                @Override
                public ItemStack createDisplayStack(SlotRenderContext ignored) {
                    return secondDisplay;
                }
            });

            assertSame(firstDisplay, SlotHookRegistry.getDisplayStack(context));
        }

        @Test
        void fallsBackToRawStackWhenNoHookSuppliesDisplay() {
            var rawStack = new ItemStack(Items.STONE);
            var context = new SlotRenderContext(createSlotView(rawStack));

            SlotHookRegistry.register(new SlotHook() {
                @Override
                public boolean matches(SlotView view) {
                    return true;
                }
            });

            assertSame(rawStack, SlotHookRegistry.getDisplayStack(context));
        }
    }

    @Nested
    @DisplayName("handleClick")
    class HandleClick {

        @Test
        void runsMatchingHooksInRegistrationOrderUntilOneConsumes() {
            var events = new ArrayList<String>();
            var context = new SlotClickContext(
                createSlotView(new ItemStack(Items.STONE)),
                ClickType.PICKUP,
                0,
                new SlotInputModifiers(true, false, false)
            );

            SlotHookRegistry.register(new RecordingHook("first", events, SlotClickResult.Pass));
            SlotHookRegistry.register(new RecordingHook("second", events, SlotClickResult.Consume));
            SlotHookRegistry.register(new RecordingHook("third", events, SlotClickResult.Pass));

            assertTrue(SlotHookRegistry.handleClick(context));
            assertEquals(
                List.of(
                    "click:first",
                    "click:second"
                ),
                events
            );
        }

        @Test
        void passHookMustBeRegisteredBeforeConsumerToRun() {
            var events = new ArrayList<String>();
            var context = new SlotClickContext(
                createSlotView(new ItemStack(Items.STONE)),
                ClickType.PICKUP,
                0,
                new SlotInputModifiers(false, false, false)
            );

            SlotHookRegistry.register(new RecordingHook("pass-before", events, SlotClickResult.Pass));
            SlotHookRegistry.register(new RecordingHook("consume", events, SlotClickResult.Consume));
            SlotHookRegistry.register(new RecordingHook("pass-after", events, SlotClickResult.Pass));

            assertTrue(SlotHookRegistry.handleClick(context));
            assertEquals(List.of("click:pass-before", "click:consume"), events);
        }

        @Test
        void returnsFalseWhenNoHookConsumes() {
            var events = new ArrayList<String>();
            var context = new SlotClickContext(
                createSlotView(new ItemStack(Items.STONE)),
                ClickType.PICKUP,
                1,
                new SlotInputModifiers(false, true, false)
            );

            SlotHookRegistry.register(new RecordingHook("one", events, SlotClickResult.Pass));
            SlotHookRegistry.register(new RecordingHook("two", events, SlotClickResult.Pass));

            assertFalse(SlotHookRegistry.handleClick(context));
            assertEquals(
                List.of(
                    "click:one",
                    "click:two"
                ),
                events
            );
        }
    }

    private static SlotView createSlotView(ItemStack rawStack) {
        var container = new SimpleContainer(rawStack);
        Slot slot = new Slot(container, 0, 0, 0);
        return new SlotView(new ScreenInfo(null), new ScreenInfo(null), slot, rawStack, false);
    }

    private static final class RecordingHook implements SlotHook {

        private final String name;
        private final List<String> events;
        private final SlotClickResult result;

        private RecordingHook(String name, List<String> events, SlotClickResult result) {
            this.name = name;
            this.events = events;
            this.result = result;
        }

        @Override
        public boolean matches(SlotView view) {
            return true;
        }

        @Override
        public SlotClickResult onClick(SlotClickContext ctx) {
            this.events.add("click:" + this.name);
            return this.result;
        }
    }
}