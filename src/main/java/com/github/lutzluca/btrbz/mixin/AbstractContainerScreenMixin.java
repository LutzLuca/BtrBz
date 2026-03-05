package com.github.lutzluca.btrbz.mixin;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.ScreenActionManager;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import com.github.lutzluca.btrbz.core.ModuleManager;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

    @Inject(method = "onClose", at = @At("HEAD"))
    private void onClose(CallbackInfo ci) {
        ScreenInfoHelper.get().getInventoryWatcher().onCloseScreen();
        var wm = ModuleManager.getInstance().getWidgetManager();
        if (wm != null) {
            wm.cleanup();
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void onMouseScrolled(double mouseX, double mouseY, double hAmt, double vAmt, CallbackInfoReturnable<Boolean> cir) {
        var wm = ModuleManager.getInstance().getWidgetManager();
        if (wm != null && wm.mouseScrolled(mouseX, mouseY, hAmt, vAmt)) {
            cir.setReturnValue(true);
        }
    }


    @Inject(method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ClickType;)V", at = @At("HEAD"), cancellable = true)
    private void onSlotClicked(
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
        var wm = ModuleManager.getInstance().getWidgetManager();
        if (wm != null && wm.keyPressed(event)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        var wm = ModuleManager.getInstance().getWidgetManager();
        if (wm != null) {
            wm.render(graphics, mouseX, mouseY, delta);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        var wm = ModuleManager.getInstance().getWidgetManager();
        if (wm != null && wm.mouseClicked(event, doubleClick)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void onMouseReleased(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        var wm = ModuleManager.getInstance().getWidgetManager();
        if (wm != null && wm.mouseReleased(event)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void onMouseDragged(MouseButtonEvent event, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
        var wm = ModuleManager.getInstance().getWidgetManager();
        if (wm != null && wm.mouseDragged(event, deltaX, deltaY)) {
            cir.setReturnValue(true);
        }
    }
}
