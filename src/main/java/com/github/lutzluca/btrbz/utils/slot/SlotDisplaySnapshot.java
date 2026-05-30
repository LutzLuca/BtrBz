package com.github.lutzluca.btrbz.utils.slot;

import org.jetbrains.annotations.Nullable;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public record SlotDisplaySnapshot(
    @Nullable Slot slot,
    ItemStack rawItem,
    ItemStack displayItem
) {

    public static final SlotDisplaySnapshot EMPTY = new SlotDisplaySnapshot(
        null,
        ItemStack.EMPTY,
        ItemStack.EMPTY
    );

    public SlotDisplaySnapshot {
        rawItem = rawItem == null ? ItemStack.EMPTY : rawItem;
        displayItem = displayItem == null ? ItemStack.EMPTY : displayItem;
    }

    public boolean hasDisplayItem() {
        return !this.displayItem.isEmpty();
    }
}
