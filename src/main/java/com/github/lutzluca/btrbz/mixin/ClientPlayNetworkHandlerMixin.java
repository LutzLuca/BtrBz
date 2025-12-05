package com.github.lutzluca.btrbz.mixin;

import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
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
        ScreenInfoHelper.get().getInventoryWatcher().onPacketReceived(packet);
    }

    @Inject(method = "handleContainerSetSlot", at = @At("RETURN"))
    private void onScreenHandlerSlotUpdate(
        ClientboundContainerSetSlotPacket packet,
        CallbackInfo ci
    ) {
        ScreenInfoHelper.get().getInventoryWatcher().onPacketReceived(packet);
    }

    @Inject(method = "handleContainerClose", at = @At("RETURN"))
    private void onCloseScreen(ClientboundContainerClosePacket packet, CallbackInfo ci) {
        ScreenInfoHelper.get().getInventoryWatcher().onPacketReceived(packet);
    }
}
