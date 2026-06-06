package com.github.lutzluca.btrbz.utils.slot;

import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public record SlotView(
    ScreenInfo currInfo,
    @Nullable ScreenInfo prevInfo,
    Slot slot,
    ItemStack rawStack,
    boolean playerInventorySlot
) {

    public int slotIndex() {
        return this.slot.getContainerSlot();
    }
}