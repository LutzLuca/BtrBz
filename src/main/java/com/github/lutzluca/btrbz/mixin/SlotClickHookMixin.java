package com.github.lutzluca.btrbz.mixin;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.screen.ScreenTracker;
import com.github.lutzluca.btrbz.screen.slot.SlotHookRegistry;
import com.github.lutzluca.btrbz.screen.slot.SlotClickContext;
import com.github.lutzluca.btrbz.screen.slot.VirtualSlotProjection;
import com.github.lutzluca.btrbz.screen.slot.SlotView;
import com.github.lutzluca.btrbz.screen.slot.SlotInputModifiers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

@Mixin(AbstractContainerScreen.class)
public abstract class SlotClickHookMixin {

    @Inject(
        method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V",
        at = @At("HEAD"),
        cancellable = true)
    private void onSlotClicked(
        Slot slot,
        int slotId,
        int button,
        ContainerInput type,
        CallbackInfo ci
    ) {
        if (!BtrBz.isActive() || slot == null) {
            return;
        }

        if (this.btrbz$handleSlotHook(slot, button, type)) {
            ci.cancel();
        }
    }

    @Unique
    private boolean btrbz$handleSlotHook(Slot slot, int button, ContainerInput type) {
        var raw = VirtualSlotProjection.withProjectionSuppressed(slot::getItem);
        var helper = ScreenTracker.get();

        var ctx = new SlotClickContext(
            new SlotView(helper.getCurrInfo(), helper.getPrevInfo(), slot, raw),
            type,
            button,
            SlotInputModifiers.from(Minecraft.getInstance()));
        return SlotHookRegistry.handleClick(ctx);
    }
}
