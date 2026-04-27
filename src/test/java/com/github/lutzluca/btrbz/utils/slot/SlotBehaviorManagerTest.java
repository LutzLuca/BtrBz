package com.github.lutzluca.btrbz.utils.slot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.lutzluca.btrbz.utils.ClickOutcome;
import com.github.lutzluca.btrbz.utils.MinecraftTestBootstrap;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
    void clearRegistrations() {
        SlotBehaviorManager.clearRegistrations();
        SlotObserverManager.clearObservers();
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
                new SlotInputModifiers(false, false, false)
            );

            var outcome = SlotBehaviorManager.handleClick(context);

            assertEquals(ClickOutcome.Cancel, outcome);
            assertEquals(1, firstCalls.get());
            assertEquals(1, secondCalls.get());
            assertEquals(0, thirdCalls.get());
        }
    }

    @Nested
    @DisplayName("handleClickAndObserve")
    class HandleClickAndObserve {

        @Test
        void doesNotObserveCancelledClicks() {
            var observerCalls = new AtomicInteger();

            SlotBehaviorManager.register(
                SlotBehaviorRegistration
                    .named("cancel")
                    .onClick(context -> ClickOutcome.Cancel)
                    .build()
            );
            SlotObserverManager.register(new SlotObserverManager.SlotObserver() {
                @Override
                public boolean matches(SlotClickContext context) {
                    return true;
                }

                @Override
                public void onClick(SlotClickContext context) {
                    observerCalls.incrementAndGet();
                }
            });

            var outcome = SlotBehaviorManager.handleClickAndObserve(createClickContext(createSlot()));

            assertEquals(ClickOutcome.Cancel, outcome);
            assertEquals(0, observerCalls.get());
        }

        @Test
        void observesPassOutcome() {
            var observerCalls = new AtomicInteger();

            SlotBehaviorManager.register(
                SlotBehaviorRegistration
                    .named("pass")
                    .onClick(context -> ClickOutcome.Pass)
                    .build()
            );
            SlotObserverManager.register(new SlotObserverManager.SlotObserver() {
                @Override
                public boolean matches(SlotClickContext context) {
                    return true;
                }

                @Override
                public void onClick(SlotClickContext context) {
                    observerCalls.incrementAndGet();
                }
            });

            var outcome = SlotBehaviorManager.handleClickAndObserve(createClickContext(createSlot()));

            assertEquals(ClickOutcome.Pass, outcome);
            assertEquals(1, observerCalls.get());
        }

        @Test
        void observesHandledOutcome() {
            var observerCalls = new AtomicInteger();

            SlotBehaviorManager.register(
                SlotBehaviorRegistration
                    .named("handled")
                    .onClick(context -> ClickOutcome.Handled)
                    .build()
            );
            SlotObserverManager.register(new SlotObserverManager.SlotObserver() {
                @Override
                public boolean matches(SlotClickContext context) {
                    return true;
                }

                @Override
                public void onClick(SlotClickContext context) {
                    observerCalls.incrementAndGet();
                }
            });

            var outcome = SlotBehaviorManager.handleClickAndObserve(createClickContext(createSlot()));

            assertEquals(ClickOutcome.Handled, outcome);
            assertEquals(1, observerCalls.get());
        }

        @Test
        void doesNotObserveCancelledOverriddenClicks() {
            var observerCalls = new AtomicInteger();
            var rawSlot = createFilledSlot();

            SlotBehaviorManager.register(
                SlotBehaviorRegistration
                    .named("override-and-cancel")
                    .overrideItem(context -> Optional.of(new ItemStack(Items.BOOK)))
                    .onClick(context -> ClickOutcome.Cancel)
                    .build()
            );
            SlotObserverManager.register(new SlotObserverManager.SlotObserver() {
                @Override
                public boolean matches(SlotClickContext context) {
                    return !context.rawItem().isEmpty();
                }

                @Override
                public void onClick(SlotClickContext context) {
                    observerCalls.incrementAndGet();
                }
            });

            var displayItem = SlotBehaviorManager.applyItemOverride(
                new ScreenInfoHelper.ScreenInfo(null),
                new ScreenInfoHelper.ScreenInfo(null),
                rawSlot,
                rawSlot.getItem()
            );
            var context = new SlotClickContext(
                new ScreenInfoHelper.ScreenInfo(null),
                new ScreenInfoHelper.ScreenInfo(null),
                rawSlot,
                rawSlot.getItem(),
                displayItem,
                0,
                ClickType.PICKUP,
                SlotInputModifiers.none()
            );

            var outcome = SlotBehaviorManager.handleClickAndObserve(context);

            assertEquals(ClickOutcome.Cancel, outcome);
            assertEquals(0, observerCalls.get());
        }
    }

    private static Slot createSlot() {
        var container = new SimpleContainer(1);
        return new Slot(container, 0, 0, 0);
    }

    private static Slot createFilledSlot() {
        var container = new SimpleContainer(1);
        container.setItem(0, new ItemStack(Items.STONE));
        return new Slot(container, 0, 0, 0);
    }

    private static SlotClickContext createClickContext(Slot slot) {
        return new SlotClickContext(
            new ScreenInfoHelper.ScreenInfo(null),
            new ScreenInfoHelper.ScreenInfo(null),
            slot,
            slot.getItem(),
            slot.getItem().copy(),
            0,
            ClickType.PICKUP,
            SlotInputModifiers.none()
        );
    }
}