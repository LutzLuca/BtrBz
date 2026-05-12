package com.github.lutzluca.btrbz.mixin;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Slot.class)
public interface SlotAccessor {
    @Accessor("container")
    Container getContainer();

    @Accessor("slot")
    int getSlotIndex();
}