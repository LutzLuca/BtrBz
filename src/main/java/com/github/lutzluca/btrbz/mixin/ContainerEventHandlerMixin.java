package com.github.lutzluca.btrbz.mixin;

import com.github.lutzluca.btrbz.BtrBz;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// We inject into ContainerEventHandler rather than a concrete screen subclass
// because Mixin targets declared bytecode, not inherited dispatch. Screen and
// AbstractSignEditScreen don't override these mouse methods the bytecode lives here.
// Safe against double-firing: any screen that does override (e.g. AbstractContainerScreen)
// never dispatches to this bytecode, so these injections never run for those screens.
@Mixin(ContainerEventHandler.class)
public interface ContainerEventHandlerMixin {

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (Minecraft.getInstance().screen instanceof SignEditScreen
            && BtrBz.screenWidgetHost().keyPressed(event)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void onMouseScrolled(double mouseX, double mouseY, double hAmt, double vAmt,
            CallbackInfoReturnable<Boolean> cir) {
        if (Minecraft.getInstance().screen instanceof SignEditScreen
            && BtrBz.screenWidgetHost().mouseScrolled(mouseX, mouseY, hAmt, vAmt)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(MouseButtonEvent event, boolean doubleClick,
            CallbackInfoReturnable<Boolean> cir) {
        if (Minecraft.getInstance().screen instanceof SignEditScreen
            && BtrBz.screenWidgetHost().mouseClicked(event, doubleClick)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void onMouseReleased(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (Minecraft.getInstance().screen instanceof SignEditScreen
            && BtrBz.screenWidgetHost().mouseReleased(event)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void onMouseDragged(MouseButtonEvent event, double deltaX, double deltaY,
            CallbackInfoReturnable<Boolean> cir) {
        if (Minecraft.getInstance().screen instanceof SignEditScreen
            && BtrBz.screenWidgetHost().mouseDragged(event, deltaX, deltaY)) {
            cir.setReturnValue(true);
        }
    }
}
