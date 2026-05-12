package com.github.lutzluca.btrbz.utils.slot;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.github.lutzluca.btrbz.utils.ClickOutcome;
import com.github.lutzluca.btrbz.utils.MinecraftTestBootstrap;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;

class SlotInterceptorManagerTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.bootstrap();
    }

    @BeforeEach
    void clearRegistrations() {
        SlotInterceptorManager.clearRegistrations();
        SlotObserverManager.clearObservers();
    }

    @Nested
    @DisplayName("applyItemOverride")
    class ApplyItemOverride {

        @Test
        void ignoresClickOnlyRegistrations() {
            var matcherCalls = new AtomicInteger();
            SlotInterceptorManager.register(
                SlotInterceptorRegistration
                    .named("click-only")
                    .matches(ctx -> {
                        matcherCalls.incrementAndGet();
                        return true;
                    })
                    .onClick(ctx -> ClickOutcome.Pass)
                    .build()
            );

            var slot = createSlot();
            var rawItem = slot.getItem();

            var result = SlotInterceptorManager.applyItemOverride(
                new ScreenInfoHelper.ScreenInfo(null),
                new ScreenInfoHelper.ScreenInfo(null),
                slot,
                rawItem
            );

            Assertions.assertSame(rawItem, result);
            Assertions.assertEquals(0, matcherCalls.get());
        }

        @Test
        void returnsFirstReplacementWithoutEvaluatingLaterOverrides() {
            var secondCalls = new AtomicInteger();
            var replacement = new ItemStack(Items.STONE);

            SlotInterceptorManager.register(
                SlotInterceptorRegistration
                    .named("first-override")
                    .overrideItem(ctx -> Optional.of(replacement))
                    .build()
            );
            SlotInterceptorManager.register(
                SlotInterceptorRegistration
                    .named("second-override")
                    .matches(ctx -> {
                        secondCalls.incrementAndGet();
                        return true;
                    })
                    .overrideItem(ctx -> Optional.of(ItemStack.EMPTY))
                    .build()
            );

            var slot = createSlot();
            var result = SlotInterceptorManager.applyItemOverride(
                new ScreenInfoHelper.ScreenInfo(null),
                new ScreenInfoHelper.ScreenInfo(null),
                slot,
                slot.getItem()
            );

            Assertions.assertSame(replacement, result);
            Assertions.assertEquals(0, secondCalls.get());
        }
    }

    @Nested
    @DisplayName("createClickContext")
    class CreateClickContext {

        @Test
        void preservesRawItemAndUsesOverriddenDisplayItem() {
            var replacement = new ItemStack(Items.BOOK);
            SlotInterceptorManager.register(
                SlotInterceptorRegistration
                    .named("override-display")
                    .overrideItem(ctx -> Optional.of(replacement))
                    .build()
            );

            var slot = createFilledSlot();

            var ctx = SlotInterceptorManager.createClickContext(
                new ScreenInfoHelper.ScreenInfo(null),
                new ScreenInfoHelper.ScreenInfo(null),
                slot,
                1,
                ClickType.PICKUP
            );

            Assertions.assertSame(slot, ctx.slot());
            Assertions.assertEquals(Items.STONE, ctx.rawItem().getItem());
            Assertions.assertSame(replacement, ctx.displayItem());
            Assertions.assertEquals(1, ctx.button());
            Assertions.assertEquals(ClickType.PICKUP, ctx.actionType());
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

            SlotInterceptorManager.register(
                SlotInterceptorRegistration
                    .named("handled")
                    .onClick(ctx -> {
                        firstCalls.incrementAndGet();
                        return ClickOutcome.Handled;
                    })
                    .build()
            );
            SlotInterceptorManager.register(
                SlotInterceptorRegistration
                    .named("cancel")
                    .onClick(ctx -> {
                        secondCalls.incrementAndGet();
                        return ClickOutcome.Cancel;
                    })
                    .build()
            );
            SlotInterceptorManager.register(
                SlotInterceptorRegistration
                    .named("after-cancel")
                    .onClick(ctx -> {
                        thirdCalls.incrementAndGet();
                        return ClickOutcome.Handled;
                    })
                    .build()
            );

            var slot = createSlot();
            var ctx = new SlotClickContext(
                new ScreenInfoHelper.ScreenInfo(null),
                new ScreenInfoHelper.ScreenInfo(null),
                slot,
                slot.getItem(),
                slot.getItem().copy(),
                0,
                ClickType.PICKUP,
                new SlotInputModifiers(false, false, false)
            );

            var outcome = SlotInterceptorManager.handleClick(ctx);

            Assertions.assertEquals(ClickOutcome.Cancel, outcome);
            Assertions.assertEquals(1, firstCalls.get());
            Assertions.assertEquals(1, secondCalls.get());
            Assertions.assertEquals(0, thirdCalls.get());
        }

        @Test
        void returnsPassWhenNoRegistrationMatches() {
            var handlerCalls = new AtomicInteger();

            SlotInterceptorManager.register(
                SlotInterceptorRegistration
                    .named("non-matching")
                    .matches(ctx -> false)
                    .onClick(ctx -> {
                        handlerCalls.incrementAndGet();
                        return ClickOutcome.Cancel;
                    })
                    .build()
            );

            var outcome = SlotInterceptorManager.handleClick(createClickContext(createSlot()));

            Assertions.assertEquals(ClickOutcome.Pass, outcome);
            Assertions.assertEquals(0, handlerCalls.get());
        }
    }

    @Nested
    @DisplayName("handleClickAndObserve")
    class HandleClickAndObserve {

        @Test
        void doesNotObserveCancelledClicks() {
            var observerCalls = new AtomicInteger();

            SlotInterceptorManager.register(
                SlotInterceptorRegistration
                    .named("cancel")
                    .onClick(ctx -> ClickOutcome.Cancel)
                    .build()
            );
            SlotObserverManager.register(new SlotObserverManager.SlotObserver() {
                @Override
                public boolean matches(SlotClickContext ctx) {
                    return true;
                }

                @Override
                public void onClick(SlotClickContext ctx) {
                    observerCalls.incrementAndGet();
                }
            });

            var outcome = SlotInterceptorManager.handleClickAndObserve(createClickContext(createSlot()));

            Assertions.assertEquals(ClickOutcome.Cancel, outcome);
            Assertions.assertEquals(0, observerCalls.get());
        }

        @Test
        void observesPassOutcome() {
            var observerCalls = new AtomicInteger();

            SlotInterceptorManager.register(
                SlotInterceptorRegistration
                    .named("pass")
                    .onClick(ctx -> ClickOutcome.Pass)
                    .build()
            );
            SlotObserverManager.register(new SlotObserverManager.SlotObserver() {
                @Override
                public boolean matches(SlotClickContext ctx) {
                    return true;
                }

                @Override
                public void onClick(SlotClickContext ctx) {
                    observerCalls.incrementAndGet();
                }
            });

            var outcome = SlotInterceptorManager.handleClickAndObserve(createClickContext(createSlot()));

            Assertions.assertEquals(ClickOutcome.Pass, outcome);
            Assertions.assertEquals(1, observerCalls.get());
        }

        @Test
        void observesHandledOutcome() {
            var observerCalls = new AtomicInteger();

            SlotInterceptorManager.register(
                SlotInterceptorRegistration
                    .named("handled")
                    .onClick(ctx -> ClickOutcome.Handled)
                    .build()
            );
            SlotObserverManager.register(new SlotObserverManager.SlotObserver() {
                @Override
                public boolean matches(SlotClickContext ctx) {
                    return true;
                }

                @Override
                public void onClick(SlotClickContext ctx) {
                    observerCalls.incrementAndGet();
                }
            });

            var outcome = SlotInterceptorManager.handleClickAndObserve(createClickContext(createSlot()));

            Assertions.assertEquals(ClickOutcome.Handled, outcome);
            Assertions.assertEquals(1, observerCalls.get());
        }

        @Test
        void doesNotObserveCancelledOverriddenClicks() {
            var observerCalls = new AtomicInteger();
            var rawSlot = createFilledSlot();

            SlotInterceptorManager.register(
                SlotInterceptorRegistration
                    .named("override-and-cancel")
                    .overrideItem(ctx -> Optional.of(new ItemStack(Items.BOOK)))
                    .onClick(ctx -> ClickOutcome.Cancel)
                    .build()
            );
            SlotObserverManager.register(new SlotObserverManager.SlotObserver() {
                @Override
                public boolean matches(SlotClickContext ctx) {
                    return !ctx.rawItem().isEmpty();
                }

                @Override
                public void onClick(SlotClickContext ctx) {
                    observerCalls.incrementAndGet();
                }
            });

            var displayItem = SlotInterceptorManager.applyItemOverride(
                new ScreenInfoHelper.ScreenInfo(null),
                new ScreenInfoHelper.ScreenInfo(null),
                rawSlot,
                rawSlot.getItem()
            );
            var ctx = new SlotClickContext(
                new ScreenInfoHelper.ScreenInfo(null),
                new ScreenInfoHelper.ScreenInfo(null),
                rawSlot,
                rawSlot.getItem(),
                displayItem,
                0,
                ClickType.PICKUP,
                SlotInputModifiers.none()
            );

            var outcome = SlotInterceptorManager.handleClickAndObserve(ctx);

            Assertions.assertEquals(ClickOutcome.Cancel, outcome);
            Assertions.assertEquals(0, observerCalls.get());
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