package com.github.lutzluca.btrbz.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import com.github.lutzluca.btrbz.utils.slot.SlotClickContext;
import com.github.lutzluca.btrbz.utils.slot.SlotHookRegistry;
import com.github.lutzluca.btrbz.utils.slot.SlotInputModifiers;
import com.github.lutzluca.btrbz.utils.slot.VirtualSlotProjection;

@Mixin(AbstractContainerScreen.class)
public abstract class SlotClickHookMixin {

    @Inject(
        method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ClickType;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onSlotClicked(
        Slot slot,
        int slotId,
        int button,
        ClickType type,
        CallbackInfo ci
    ) {
        if (slot == null) {
            return;
        }

        if (this.btrbz$handleSlotHook(slot, button, type)) {
            ci.cancel();
        }
    }

    @Unique
    private boolean btrbz$handleSlotHook(Slot slot, int button, ClickType type) {
        var raw = VirtualSlotProjection.withProjectionSuppressed(slot::getItem);

        var ctx = new SlotClickContext(
            VirtualSlotProjection.createSlotView(slot, raw),
            type,
            button,
            SlotInputModifiers.from(Minecraft.getInstance())
        );

        return SlotHookRegistry.handleClick(ctx);
    }
}
