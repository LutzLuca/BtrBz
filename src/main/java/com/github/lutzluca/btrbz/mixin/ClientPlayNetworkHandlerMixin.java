package com.github.lutzluca.btrbz.mixin;

import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.CloseScreenS2CPacket;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {

    @Inject(method = "onOpenScreen", at = @At("RETURN"))
    private void onOpenScreen(OpenScreenS2CPacket packet, CallbackInfo ci) {
        ScreenInfoHelper.get().getInventoryWatcher().onPacketReceived(packet);
    }

    @Inject(method = "onScreenHandlerSlotUpdate", at = @At("RETURN"))
    private void onScreenHandlerSlotUpdate(
        ScreenHandlerSlotUpdateS2CPacket packet,
        CallbackInfo ci
    ) {
        ScreenInfoHelper.get().getInventoryWatcher().onPacketReceived(packet);
    }

    @Inject(method = "onCloseScreen", at = @At("RETURN"))
    private void onCloseScreen(CloseScreenS2CPacket packet, CallbackInfo ci) {
        ScreenInfoHelper.get().getInventoryWatcher().onPacketReceived(packet);
    }
}
