package com.github.lutzluca.btrbz.mixin;

import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Slf4j
@Mixin(ClientPacketListener.class)
public class ClientPlayNetworkHandlerMixin {

    @Inject(method = "handleOpenScreen", at = @At("RETURN"))
    private void onOpenScreen(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
        var screenInfoHelper = ScreenInfoHelper.get();
        if (!screenInfoHelper.isContainerActive(packet.getContainerId())) {
            log.debug(
                "Skipping inventory tracking for inactive container {} ('{}')",
                packet.getContainerId(),
                packet.getTitle().getString());
            return;
        }

        screenInfoHelper.getInventoryWatcher().onPacketReceived(packet);
    }

    @Inject(method = "handleContainerSetSlot", at = @At("RETURN"))
    private void onScreenHandlerSlotUpdate(
        ClientboundContainerSetSlotPacket packet,
        CallbackInfo ci
    ) {
        ScreenInfoHelper.get().getInventoryWatcher().onPacketReceived(packet);
    }
}
