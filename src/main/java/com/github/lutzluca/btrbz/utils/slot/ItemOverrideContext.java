package com.github.lutzluca.btrbz.utils.slot;

import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public record ItemOverrideContext(
    ScreenInfo currInfo,
    ScreenInfo prevInfo,
    @Nullable Slot slot,
    ItemStack rawItem
) implements SlotBehaviorContext {

    public ItemOverrideContext {
        rawItem = rawItem == null ? ItemStack.EMPTY : rawItem;
    }
}