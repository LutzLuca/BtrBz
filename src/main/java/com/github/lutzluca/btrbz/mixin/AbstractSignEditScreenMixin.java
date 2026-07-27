package com.github.lutzluca.btrbz.mixin;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.widgets.WidgetCanvas;
import com.github.lutzluca.btrbz.core.widgets.WidgetHostOptions;
import com.github.lutzluca.btrbz.core.widgets.WidgetHost;
import com.github.lutzluca.btrbz.core.widgets.WidgetHostOwner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractSignEditScreen.class)
public abstract class AbstractSignEditScreenMixin implements WidgetHostOwner {
    @Unique
    private WidgetHost btrbz$host;

    @Override
    public WidgetHost btrbz$widgetHost() {
        if (this.btrbz$host == null) this.btrbz$host = BtrBz.widgetRuntime().createScreenHost();
        return this.btrbz$host;
    }

    @Inject(method = "onClose", at = @At("HEAD"))
    private void onClose(CallbackInfo ci) {
        if (this.btrbz$host != null) this.btrbz$host.dispose();
    }

    @Inject(method = "removed", at = @At("HEAD"), require = 0)
    private void onRemoved(CallbackInfo ci) {
        if (this.btrbz$host != null) this.btrbz$host.dispose();
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onRender(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        var client = Minecraft.getInstance();
        this.btrbz$widgetHost().render(
            graphics, mouseX, mouseY, delta,
            new WidgetCanvas(0, 0, client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight()),
            WidgetHostOptions.runtime(true), client.screen
        );
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (this.btrbz$widgetHost().keyPressed(event)) {
            cir.setReturnValue(true);
        }
    }
}
