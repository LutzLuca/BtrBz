package com.github.lutzluca.btrbz.mixin;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.widgets.layout.WidgetCanvas;
import com.github.lutzluca.btrbz.core.widgets.runtime.WidgetHostOptions;
import com.github.lutzluca.btrbz.core.widgets.runtime.WidgetHost;
import com.github.lutzluca.btrbz.core.widgets.runtime.WidgetHostOwner;
import com.github.lutzluca.btrbz.core.widgets.manager.SignEditScreenTransitionState;
import com.github.lutzluca.btrbz.core.widgets.manager.WidgetManagerLauncher;
import com.github.lutzluca.btrbz.core.widgets.manager.WidgetManagerLauncherOwner;
import com.github.lutzluca.btrbz.utils.GameUtils;
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
public abstract class AbstractSignEditScreenMixin implements WidgetHostOwner, WidgetManagerLauncherOwner {
    @Unique
    private WidgetHost btrbz$host;
    @Unique
    private WidgetManagerLauncher btrbz$managerLauncher;
    @Unique
    private final SignEditScreenTransitionState btrbz$managerTransition = new SignEditScreenTransitionState();

    @Override
    public WidgetHost btrbz$widgetHost() {
        if (this.btrbz$host == null) {
            this.btrbz$host = BtrBz.widgetRuntime().createScreenHost();
        }
        return this.btrbz$host;
    }

    @Override
    public WidgetManagerLauncher btrbz$managerLauncher() {
        if (this.btrbz$managerLauncher == null) {
            this.btrbz$managerLauncher = new WidgetManagerLauncher(BtrBz.widgetRuntime());
        }
        return this.btrbz$managerLauncher;
    }

    @Override
    public void btrbz$prepareManagerTransition() {
        this.btrbz$managerTransition.suspendNextRemoval();
    }

    @Inject(method = "onClose", at = @At("HEAD"))
    private void onClose(CallbackInfo ci) {
        if (this.btrbz$host != null) {
            this.btrbz$host.dispose();
        }
        if (this.btrbz$managerLauncher != null) {
            this.btrbz$managerLauncher.dispose();
        }
    }

    @Inject(method = "removed", at = @At("HEAD"), require = 0, cancellable = true)
    private void onRemoved(CallbackInfo ci) {
        if (this.btrbz$managerTransition.consumeSuspendedRemoval()) {
            ci.cancel();
            return;
        }
        if (this.btrbz$host != null) {
            this.btrbz$host.dispose();
        }
        if (this.btrbz$managerLauncher != null) {
            this.btrbz$managerLauncher.dispose();
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onRender(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        var client = Minecraft.getInstance();
        var canvas = new WidgetCanvas(
            0, 0, client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());

        this.btrbz$widgetHost().render(
            graphics, mouseX, mouseY, delta,
            canvas,
            WidgetHostOptions.runtime(true), GameUtils.screen());

        this.btrbz$managerLauncher().render(
            graphics, mouseX, mouseY, delta, canvas, (net.minecraft.client.gui.screens.Screen) (Object) this);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (this.btrbz$widgetHost().keyPressed(event)) {
            cir.setReturnValue(true);
        }
    }
}
