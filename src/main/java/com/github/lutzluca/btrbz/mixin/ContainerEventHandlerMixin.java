package com.github.lutzluca.btrbz.mixin;

import com.github.lutzluca.btrbz.core.ModuleManager;
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
// wm != null is the only guard needed it is non-null only when a module is active for the current screen.
@Mixin(ContainerEventHandler.class)
public interface ContainerEventHandlerMixin {

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        var wm = ModuleManager.getInstance().getWidgetManager();
        if (wm != null && wm.keyPressed(event)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void onMouseScrolled(double mouseX, double mouseY, double hAmt, double vAmt,
            CallbackInfoReturnable<Boolean> cir) {
        var wm = ModuleManager.getInstance().getWidgetManager();
        if (wm != null && wm.mouseScrolled(mouseX, mouseY, hAmt, vAmt)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(MouseButtonEvent event, boolean doubleClick,
            CallbackInfoReturnable<Boolean> cir) {
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
    private void onMouseDragged(MouseButtonEvent event, double deltaX, double deltaY,
            CallbackInfoReturnable<Boolean> cir) {
        var wm = ModuleManager.getInstance().getWidgetManager();
        if (wm != null && wm.mouseDragged(event, deltaX, deltaY)) {
            cir.setReturnValue(true);
        }
    }
}
