package com.github.lutzluca.btrbz.mixin;

import com.github.lutzluca.btrbz.core.ModuleManager;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractSignEditScreen.class)
public abstract class AbstractSignEditScreenMixin {

    @Inject(method = "onClose", at = @At("HEAD"))
    private void onClose(CallbackInfo ci) {
        ScreenInfoHelper.get().getInventoryWatcher().onCloseScreen();
        var wm = ModuleManager.getInstance().getWidgetManager();
        if (wm != null) {
            wm.cleanup();
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onRender(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        var wm = ModuleManager.getInstance().getWidgetManager();
        if (wm != null) {
            wm.render(graphics, mouseX, mouseY, delta);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        var wm = ModuleManager.getInstance().getWidgetManager();
        if (wm != null && wm.keyPressed(event)) {
            cir.setReturnValue(true);
        }
    }
}
