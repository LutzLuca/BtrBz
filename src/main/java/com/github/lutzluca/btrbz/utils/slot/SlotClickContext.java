package com.github.lutzluca.btrbz.utils.slot;

import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public record SlotClickContext(
    ScreenInfo currInfo,
    ScreenInfo prevInfo,
    @Nullable Slot slot,
    ItemStack rawItem,
    ItemStack displayItem,
    int button,
    ClickType actionType,
    SlotInputModifiers modifiers
) implements SlotBehaviorContext {

    public SlotClickContext {
        rawItem = rawItem == null ? ItemStack.EMPTY : rawItem;
        displayItem = displayItem == null ? ItemStack.EMPTY : displayItem;
        modifiers = modifiers == null ? SlotInputModifiers.none() : modifiers;
    }
}