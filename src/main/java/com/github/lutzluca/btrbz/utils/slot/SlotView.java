package com.github.lutzluca.btrbz.utils.slot;

import org.jetbrains.annotations.NotNull;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;

public record SlotView(
    @NotNull ScreenInfo currInfo,
    @NotNull ScreenInfo prevInfo,
    @NotNull Slot slot,
    @NotNull ItemStack rawStack
) {

    public int slotIdx() {
        return this.slot.getContainerSlot();
    }

    public boolean playerInventorySlot() {
        return GameUtils.isPlayerInventorySlot(this.slot);
    }
}
