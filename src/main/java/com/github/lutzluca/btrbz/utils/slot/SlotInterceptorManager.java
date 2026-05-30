package com.github.lutzluca.btrbz.utils.slot;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import io.vavr.control.Try;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import com.github.lutzluca.btrbz.mixin.SlotAccessor;
import com.github.lutzluca.btrbz.utils.ClickOutcome;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;

@Slf4j
public final class SlotInterceptorManager {

    private static final List<SlotInterceptorRegistration> REGISTRATIONS = new ArrayList<>();
    private static final ThreadLocal<Boolean> RESOLVING_DISPLAY = ThreadLocal.withInitial(() -> false);

    private SlotInterceptorManager() { }

    /**
     * Registers a slot interceptor in evaluation order.
     * <p>
     * Display override conflicts are resolved purely by registration order: the first matching
     * registration that returns a replacement wins, and there is no automatic conflict resolution.
     */
    public static void register(SlotInterceptorRegistration registration) {
        REGISTRATIONS.add(registration);
    }

    static void clearRegistrations() {
        REGISTRATIONS.clear();
    }

    public static ItemStack resolveDisplayItem(ScreenInfo currInfo, ScreenInfo prevInfo, Slot slot) {
        return createDisplaySnapshot(currInfo, prevInfo, slot).displayItem();
    }

    public static boolean hasDisplayItem(ScreenInfo currInfo, ScreenInfo prevInfo, Slot slot) {
        return createDisplaySnapshot(currInfo, prevInfo, slot).hasDisplayItem();
    }

    public static SlotDisplaySnapshot createDisplaySnapshot(
        ScreenInfo currInfo,
        ScreenInfo prevInfo,
        @Nullable Slot slot
    ) {
        if (slot == null) {
            return SlotDisplaySnapshot.EMPTY;
        }

        var rawItem = resolveRawItem(slot);
        var displayItem = resolveDisplayItem(currInfo, prevInfo, slot, rawItem);
        return new SlotDisplaySnapshot(slot, rawItem, displayItem);
    }

    /**
     * Applies the first matching display override for the given slot context.
     * <p>
     * Registration order determines precedence: first registered wins, and later matching
     * overrides are not evaluated once a replacement is returned.
     */
    public static ItemStack resolveDisplayItem(ScreenInfo currentInfo, ScreenInfo previousInfo, Slot slot, ItemStack rawItem) {
        if (RESOLVING_DISPLAY.get()) {
            return rawItem;
        }

        RESOLVING_DISPLAY.set(true);
        try {
            return resolveDisplayItemInternal(currentInfo, previousInfo, slot, rawItem);
        } finally {
            RESOLVING_DISPLAY.set(false);
        }
    }

    public static ItemStack applyItemOverride(ScreenInfo currentInfo, ScreenInfo previousInfo, Slot slot, ItemStack rawItem) {
        return resolveDisplayItem(currentInfo, previousInfo, slot, rawItem);
    }

    private static ItemStack resolveDisplayItemInternal(ScreenInfo currentInfo, ScreenInfo previousInfo, Slot slot, ItemStack rawItem) {
        var ctx = new ItemOverrideContext(currentInfo, previousInfo, slot, rawItem);

        for (SlotInterceptorRegistration registration : REGISTRATIONS) {
            if (registration.itemOverrideHandler() == null) {
                continue;
            }

            var matches = Try.of(() -> registration.matcher().matches(ctx))
                .onFailure(err -> log.error(
                    "Slot interceptor '{}' failed while matching override screen '{}' slot '{}'",
                    registration.name(),
                    currentInfo.containerName().orElse("<unknown>"),
                    ctx.containerSlot(),
                    err
                ))
                .getOrElse(false);

            if (!matches) {
                continue;
            }

            var replacement = Try.of(() -> registration.itemOverrideHandler().override(ctx))
                .onFailure(err -> log.error(
                    "Slot interceptor '{}' failed while overriding screen '{}' slot '{}'",
                    registration.name(),
                    currentInfo.containerName().orElse("<unknown>"),
                    ctx.containerSlot(),
                    err
                ))
                .getOrElse(Optional.empty());

            if (replacement.isPresent()) {
                return replacement.get();
            }
        }

        return rawItem;
    }

    public static SlotClickContext createClickContext(
        ScreenInfo currInfo,
        ScreenInfo prevInfo,
        @Nullable Slot slot,
        int button,
        ClickType actionType
    ) {
        var snapshot = createDisplaySnapshot(currInfo, prevInfo, slot);
        var client = Minecraft.getInstance();
        var modifiers = client == null
            ? SlotInputModifiers.none()
            : SlotInputModifiers.from(client);

        return new SlotClickContext(
            currInfo,
            prevInfo,
            slot,
            snapshot.rawItem(),
            snapshot.displayItem(),
            button,
            actionType,
            modifiers
        );
    }

    public static ClickOutcome handleClick(SlotClickContext ctx) {
        if (ctx.slot() == null) {
            return ClickOutcome.Pass;
        }

        ClickOutcome aggregate = ClickOutcome.Pass;

        for (SlotInterceptorRegistration registration : REGISTRATIONS) {
            if (registration.clickHandler() == null) {
                continue;
            }

            var matches = Try.of(() -> registration.matcher().matches(ctx))
                .onFailure(err -> log.error(
                    "Slot interceptor '{}' failed while matching click screen '{}' slot '{}' action '{}'",
                    registration.name(),
                    ctx.currInfo().containerName().orElse("<unknown>"),
                    ctx.containerSlot(),
                    ctx.actionType(),
                    err
                ))
                .getOrElse(false);

            if (!matches) {
                continue;
            }

            var outcome = Try.of(() -> registration.clickHandler().onClick(ctx))
                .onFailure(err -> log.error(
                    "Slot interceptor '{}' failed while handling click screen '{}' slot '{}' action '{}'",
                    registration.name(),
                    ctx.currInfo().containerName().orElse("<unknown>"),
                    ctx.containerSlot(),
                    ctx.actionType(),
                    err
                ))
                .getOrElse(ClickOutcome.Pass);

            if (outcome == ClickOutcome.Cancel) {
                return outcome;
            }

            if (outcome == ClickOutcome.Handled) {
                aggregate = ClickOutcome.Handled;
            }
        }

        return aggregate;
    }

    public static ClickOutcome handleClickAndObserve(SlotClickContext ctx) {
        var outcome = handleClick(ctx);
        if (outcome != ClickOutcome.Cancel) {
            // NOTE: Observers currently fire before vanilla slot handling. Moving this to a
            // post-vanilla hook may make more sense if observers should only see clicks that
            // vanilla actually processes
            SlotObserverManager.observeClick(ctx);
        }

        return outcome;
    }

    static ItemStack resolveRawItem(@Nullable Slot slot) {
        if (slot == null) {
            return ItemStack.EMPTY;
        }

        if (!(slot instanceof SlotAccessor accessor)) {
            return slot.getItem();
        }

        var container = accessor.getContainer();
        return container.getItem(accessor.getSlotIndex());
    }
}
