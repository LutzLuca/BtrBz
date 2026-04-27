package com.github.lutzluca.btrbz.utils.slot;

import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public interface SlotBehaviorContext {

    ScreenInfo currInfo();

    ScreenInfo prevInfo();

    @Nullable Slot slot();

    ItemStack rawItem();

    default boolean inMenu(BazaarMenuType... menuTypes) {
        return this.currInfo().inMenu(menuTypes);
    }

    default int containerSlot() {
        var slot = this.slot();
        return slot == null ? -1 : slot.getContainerSlot();
    }

    default boolean isPlayerInventorySlot() {
        return GameUtils.isPlayerInventorySlot(this.slot());
    }

    default SlotInputModifiers modifiers() {
        return SlotInputModifiers.none();
    }
}