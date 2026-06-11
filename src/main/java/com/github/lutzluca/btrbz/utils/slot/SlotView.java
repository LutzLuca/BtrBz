package com.github.lutzluca.btrbz.utils.slot;

import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public record SlotView(
    @NotNull ScreenInfo currInfo,
    @NotNull ScreenInfo prevInfo,
    @NotNull Slot slot,
    @NotNull ItemStack rawStack,
    boolean playerInventorySlot
) {

    public int slotIdx() {
        return this.slot.getContainerSlot();
    }
}