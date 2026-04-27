package com.github.lutzluca.btrbz.utils.slot;

import com.github.lutzluca.btrbz.mixin.SlotAccessor;
import com.github.lutzluca.btrbz.utils.ClickOutcome;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import io.vavr.control.Try;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

@Slf4j
public final class SlotBehaviorManager {

    private static final List<SlotBehaviorRegistration> REGISTRATIONS = new ArrayList<>();

    private SlotBehaviorManager() { }

    /**
     * Registers a slot behavior in evaluation order.
     *
     * Item override conflicts are resolved purely by registration order: the first matching
     * registration that returns a replacement wins, and there is no automatic conflict resolution.
     */
    public static void register(SlotBehaviorRegistration registration) {
        REGISTRATIONS.add(registration);
    }

    /**
     * Applies the first matching item override for the given slot context.
     *
     * Registration order determines precedence: first registered wins, and later matching
     * overrides are not evaluated once a replacement is returned.
     */
    public static ItemStack applyItemOverride(ScreenInfo currentInfo, ScreenInfo previousInfo, Slot slot, ItemStack rawItem) {
        var context = new ItemOverrideContext(currentInfo, previousInfo, slot, rawItem);

        for (SlotBehaviorRegistration registration : REGISTRATIONS) {
            if (registration.itemOverrideHandler() == null) {
                continue;
            }

            var matches = Try.of(() -> registration.matcher().matches(context))
                .onFailure(err -> log.error(
                    "Slot behavior '{}' failed while matching override screen '{}' slot '{}'",
                    registration.name(),
                    currentInfo.containerName().orElse("<unknown>"),
                    context.containerSlot(),
                    err
                ))
                .getOrElse(false);

            if (!matches) {
                continue;
            }

            var replacement = Try.of(() -> registration.itemOverrideHandler().override(context))
                .onFailure(err -> log.error(
                    "Slot behavior '{}' failed while overriding screen '{}' slot '{}'",
                    registration.name(),
                    currentInfo.containerName().orElse("<unknown>"),
                    context.containerSlot(),
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
        var rawItem = resolveRawItem(slot);
        var displayItem = slot == null
            ? ItemStack.EMPTY
            : applyItemOverride(currInfo, prevInfo, slot, rawItem);
        var modifiers = SlotInputModifiers.from(Minecraft.getInstance());

        return new SlotClickContext(
            currInfo,
            prevInfo,
            slot,
            rawItem,
            displayItem,
            button,
            actionType,
            modifiers
        );
    }

    public static ClickOutcome handleClick(SlotClickContext context) {
        if (context.slot() == null) {
            return ClickOutcome.Pass;
        }

        ClickOutcome aggregate = ClickOutcome.Pass;

        for (SlotBehaviorRegistration registration : REGISTRATIONS) {
            if (registration.clickHandler() == null) {
                continue;
            }

            var matches = Try.of(() -> registration.matcher().matches(context))
                .onFailure(err -> log.error(
                    "Slot behavior '{}' failed while matching click screen '{}' slot '{}' action '{}'",
                    registration.name(),
                    context.currInfo().containerName().orElse("<unknown>"),
                    context.containerSlot(),
                    context.actionType(),
                    err
                ))
                .getOrElse(false);

            if (!matches) {
                continue;
            }

            var outcome = Try.of(() -> registration.clickHandler().onClick(context))
                .onFailure(err -> log.error(
                    "Slot behavior '{}' failed while handling click screen '{}' slot '{}' action '{}'",
                    registration.name(),
                    context.currInfo().containerName().orElse("<unknown>"),
                    context.containerSlot(),
                    context.actionType(),
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

    public static ClickOutcome handleClickAndObserve(SlotClickContext context) {
        var outcome = handleClick(context);
        if (outcome != ClickOutcome.Cancel) {
            SlotObserverManager.observeClick(context);
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