package com.github.lutzluca.btrbz.screen;

import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ScreenInventoryTrackerTest {
    @Test
    @DisplayName("empty slots count as received without retaining stale entries")
    void emptySlotsClearEntriesAndCompleteLoading() {
        var inventory = new ScreenInventoryTracker.Inventory(1, "Bazaar", 2);
        inventory.items.put(0, ItemStack.EMPTY);

        Assertions.assertFalse(inventory.updateSlot(0, ItemStack.EMPTY));
        Assertions.assertFalse(inventory.hasItem(0));
        Assertions.assertTrue(inventory.getItem(0).isEmpty());
        Assertions.assertFalse(inventory.updateSlot(0, ItemStack.EMPTY));
        Assertions.assertTrue(inventory.updateSlot(1, ItemStack.EMPTY));
        Assertions.assertTrue(inventory.items.isEmpty());
    }
}
