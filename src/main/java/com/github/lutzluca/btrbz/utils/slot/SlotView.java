package com.github.lutzluca.btrbz.utils.slot;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import org.jetbrains.annotations.NotNull;
import lombok.Getter;

@Getter
public class SlotView {
    private ScreenInfo currInfo;
    private ScreenInfo prevInfo;
    private Slot slot;
    private ItemStack rawStack;

    private SlotView() {}

    public SlotView(
        @NotNull ScreenInfo currInfo,
        @NotNull ScreenInfo prevInfo,
        @NotNull Slot slot,
        @NotNull ItemStack rawStack
    ) {
        this.currInfo = currInfo;
        this.prevInfo = prevInfo;
        this.slot = slot;
        this.rawStack = rawStack;
    }

    //creates an uninitialized SlotView for use in the render-path instance
    //must be populated via update() before use.
    public static SlotView createUnitialized() {
        return new SlotView();
    }

    public void update(
        @NotNull ScreenInfo currInfo,
        @NotNull ScreenInfo prevInfo,
        @NotNull Slot slot,
        @NotNull ItemStack rawStack
    ) {
        this.currInfo = currInfo;
        this.prevInfo = prevInfo;
        this.slot = slot;
        this.rawStack = rawStack;
    }

    public int slotIdx() {
        return this.slot.getContainerSlot();
    }

    public boolean playerInventorySlot() {
        return GameUtils.isPlayerInventorySlot(this.slot);
    }
}
