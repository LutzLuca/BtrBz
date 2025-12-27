package com.github.lutzluca.btrbz.mixin;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.ScreenActionManager;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
@Slf4j
public abstract class HandledScreenMixin<T extends AbstractContainerMenu> extends Screen {

    //@Shadow
    //@Final
    //protected T handler;

    protected HandledScreenMixin(Component title) { super(title); }

    @Inject(method = "onClose", at = @At("HEAD"))
    private void onClose(CallbackInfo ci) {
        ScreenInfoHelper.get().getInventoryWatcher().onCloseScreen();
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void onMouseScrolled(
        double mouseX,
        double mouseY,
        double horizontalAmount,
        double verticalAmount,
        CallbackInfoReturnable<Boolean> cir
    ) {
        for (GuiEventListener child : this.children()) {
            if (child.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
                cir.setReturnValue(true);
                return;
            }
        }
    }


    @Inject(method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ClickType;)V", at = @At("HEAD"), cancellable = true)
    private void onHandledMouseClick(
        Slot slot,
        int slotId,
        int button,
        ClickType actionType,
        CallbackInfo ci
    ) {
        var cancelled = ScreenActionManager.handleClick(
            ScreenInfoHelper.get().getCurrInfo(),
            slot,
            button
        );

        if (cancelled) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSlot", at = @At("HEAD"))
    //? if >=1.21.11 {
    /*private void afterRenderSlot(
        GuiGraphics context,
        Slot slot,
        int mouseX,
        int mouseY,
        CallbackInfo ci
    )
    *///?} else {
    private void afterRenderSlot(
        GuiGraphics context,
        Slot slot,
        CallbackInfo ci
    )
    //?}
    {
        if (!ScreenInfoHelper.inMenu(ScreenInfoHelper.BazaarMenuType.Orders)) {
            return;
        }
        if (slot.getItem().isEmpty() || GameUtils.isPlayerInventorySlot(slot)) {
            return;
        }

        var x = slot.x;
        var y = slot.y;
        var idx = slot.getContainerSlot();

        BtrBz
            .highlightManager()
            .getHighlight(idx)
            .ifPresent(color -> context.fill(x, y, x + 16, y + 16, color));
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        for (GuiEventListener child : this.children()) {
            if (child.keyPressed(event)) {
                cir.setReturnValue(true);
                return;
            }
        }
    }


    //@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/ingame/HandledScreen;drawSlots(Lnet/minecraft/client/gui/DrawContext;)V", shift = At.Shift.AFTER))
    //private void afterRenderSlot(
    //    DrawContext context,
    //    int mouseX,
    //    int mouseY,
    //    float deltaTicks,
    //    CallbackInfo ci
    //) {
    //    if (!ScreenInfoHelper.inMenu(ScreenInfoHelper.BazaarMenuType.Orders)) {
    //        return;
    //    }
    //
    //    var manager = BtrBz.highlightManager();
    //
    //    for (Slot slot : this.handler.slots) {
    //        if (slot.getStack().getItem() || GameUtils.isPlayerInventorySlot(slot)) {
    //            continue;
    //        }
    //
    //        manager
    //            .getHighlight(slot.getIndex())
    //            .ifPresent(color -> context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, color));
    //    }
    //}
}
