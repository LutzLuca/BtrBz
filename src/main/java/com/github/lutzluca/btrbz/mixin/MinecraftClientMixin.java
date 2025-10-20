package com.github.lutzluca.btrbz.mixin;

import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    // @formatter:off
    /**
     * Dual injection into MinecraftClient#setScreen to support screen-aware rendering and safe callbacks.
     *
     * Why both HEAD and TAIL?
     *
     * - @HEAD: Updates ScreenInfo early so widgets see the correct screen during init.
     *   • Example issue: When rendering a module based on the current screen's menu (e.g. BazaarMenuType.Main
     *     in OrderLimitModule), injecting only at TAIL causes stale screen info — nothing renders in the main
     *     menu, but switching to another screen and back makes it appear unexpectedly.
     *
     * - @TAIL: Fires callbacks after the screen is fully initialized.
     *   • Example issue: If callbacks run too early (e.g. in FlipHelper), calling `close()` on the screen
     *     causes a NPE on `this.client`, since the screen isn't fully set up yet.
     * 
     * This is kinda wierd and sucks
     */
    // @formatter:on

    @Inject(method = "setScreen(Lnet/minecraft/client/gui/screen/Screen;)V", at = @At("HEAD"))
    private void onSetScreenHead(@Nullable Screen screen, CallbackInfo ci) {
        ScreenInfoHelper.get().setScreen(screen);
    }

    @Inject(method = "setScreen(Lnet/minecraft/client/gui/screen/Screen;)V", at = @At("TAIL"))
    private void onSetScreenTail(@Nullable Screen screen, CallbackInfo ci) {
        ScreenInfoHelper.get().fireScreenSwitchCallbacks();
    }
}
