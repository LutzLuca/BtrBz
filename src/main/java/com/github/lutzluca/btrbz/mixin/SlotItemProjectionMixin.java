package com.github.lutzluca.btrbz.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import com.github.lutzluca.btrbz.utils.slot.VirtualSlotProjection;

@Mixin(Slot.class)
public abstract class SlotItemProjectionMixin {

    @Inject(method = "getItem", at = @At("RETURN"), cancellable = true)
    private void projectItem(CallbackInfoReturnable<ItemStack> cir) {
        if (VirtualSlotProjection.isProjectionSuppressed()) {
            return;
        }

        var slot = (Slot) (Object) this;
        if (!this.btrbz$isCurrentMenuSlot(slot)) {
            return;
        }

        var raw = cir.getReturnValue();
        var proj = VirtualSlotProjection.project(slot, raw);

        if (proj != raw) {
            cir.setReturnValue(proj);
        }
    }

    @Unique
    private boolean btrbz$isCurrentMenuSlot(Slot slot) {
        var screen = Minecraft.getInstance().screen;

        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
            return false;
        }

        var slots = containerScreen.getMenu().slots;
        int idx = slot.index;

        return idx < slots.size() && slots.get(idx) == slot;
    }
}
