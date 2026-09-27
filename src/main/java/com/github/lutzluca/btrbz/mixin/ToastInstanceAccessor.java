package com.github.lutzluca.btrbz.mixin;

import net.minecraft.client.gui.components.toasts.Toast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.gui.components.toasts.ToastManager$ToastInstance")
public interface ToastInstanceAccessor {

    @Accessor("toast")
    Toast btrbz$getToast();

    @Accessor("firstSlotIndex")
    int btrbz$getFirstSlotIndex();

    @Accessor("occupiedSlotCount")
    int btrbz$getOccupiedSlotCount();
}
