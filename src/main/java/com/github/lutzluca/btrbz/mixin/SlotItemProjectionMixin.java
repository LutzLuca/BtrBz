package com.github.lutzluca.btrbz.mixin;

import com.github.lutzluca.btrbz.utils.slot.SlotRenderContext;
import com.github.lutzluca.btrbz.utils.slot.SlotView;
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

    @Unique
    private static final SlotView btrbz$SHARED_VIEW = SlotView.createUnitialized();
    @Unique
    private static final SlotRenderContext btrbz$SHARED_CONTEXT = new SlotRenderContext(btrbz$SHARED_VIEW);

    @Inject(method = "getItem", at = @At("RETURN"), cancellable = true)
    private void projectItem(CallbackInfoReturnable<ItemStack> cir) {
        if (!Minecraft.getInstance()
            .isSameThread()) {
            return;
        }

        var slot = (Slot) (Object) this;
        if (!this.btrbz$isCurrentMenuSlot(slot)) {
            return;
        }

        var raw = cir.getReturnValue();
        var proj = VirtualSlotProjection.project(slot, raw, btrbz$SHARED_VIEW, btrbz$SHARED_CONTEXT);

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
