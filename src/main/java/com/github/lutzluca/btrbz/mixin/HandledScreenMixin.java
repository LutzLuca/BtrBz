package com.github.lutzluca.btrbz.mixin;

import com.github.lutzluca.btrbz.HighlightManager;
import com.github.lutzluca.btrbz.Notifier;
import com.github.lutzluca.btrbz.OrderParser;
import com.github.lutzluca.btrbz.OutstandingOrders;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin<T extends ScreenHandler> {

    @Inject(method = "onMouseClick(Lnet/minecraft/screen/slot/Slot;IILnet/minecraft/screen/slot/SlotActionType;)V", at = @At("HEAD"))
    private void afterHandleMouseClick(
        Slot slot,
        int slotId,
        int button,
        SlotActionType actionType,
        CallbackInfo ci
    ) {
        var client = MinecraftClient.getInstance();
        if (!(client.currentScreen instanceof HandledScreen<?> screen)) { return; }

        var title = screen.getTitle().getString();
        if (!title.equals("Confirm Buy Order") && !title.equals("Confirm Sell Offer")) {
            return;
        }
        if (slot == null || slot.getIndex() != 13) { return; }

        OrderParser.parseConfirmItem(slot.getStack()).onSuccess((setOrderInfo) -> {
            OutstandingOrders.add(setOrderInfo);
            Notifier.logInfo("Stored outstanding order for {}x {}", setOrderInfo.productName(),
                setOrderInfo.productName()
            );
        }).onFailure((err) -> {
            Notifier.logWarn("Failed to parse confirm item: {}", err);
        });
    }

    @Inject(method = "drawSlot", at = @At("TAIL"))
    private void afterDrawSlot(DrawContext context, Slot slot, CallbackInfo ci) {
        var client = MinecraftClient.getInstance();
        if (!(client.currentScreen instanceof HandledScreen<?> screen)) {
            return;
        }

        if (!screen.getTitle().getString().equals("Your Bazaar Orders")) {
            return;
        }
        if (slot.getStack().isEmpty()) {
            return;
        }

        var player = MinecraftClient.getInstance().player;
        if (player != null && slot.inventory == player.getInventory()) {
            return;
        }

        var x = slot.x;
        var y = slot.y;
        var idx = slot.getIndex();

        HighlightManager.getHighlight(idx).ifPresent(color -> {
            context.fill(x, y, x + 16, y + 16, color);
        });
    }
}
