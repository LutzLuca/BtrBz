package com.github.lutzluca.btrbz.mixin;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.screen.ScreenTracker;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPlayNetworkHandlerMixin {

    @Inject(method = "handleOpenScreen", at = @At("RETURN"))
    private void onOpenScreen(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
        var screenTracker = ScreenTracker.get();
        if (!screenTracker.isContainerActive(packet.getContainerId())) {
            return;
        }

        screenTracker.onOpenScreen(packet);
    }

    @Inject(method = "handleContainerSetSlot", at = @At("RETURN"))
    private void onScreenHandlerSlotUpdate(
        ClientboundContainerSetSlotPacket packet,
        CallbackInfo ci
    ) {
        if (!BtrBz.isActive()) {
            return;
        }
        ScreenTracker.get().onSlotUpdate(packet);
    }
}
