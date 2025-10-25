package com.github.lutzluca.btrbz.mixin;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.ModuleManager;
import com.github.lutzluca.btrbz.utils.ScreenActionManager;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
@Slf4j
public abstract class HandledScreenMixin<T extends ScreenHandler> extends Screen {

    protected HandledScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void onClose(CallbackInfo ci) {
        ScreenInfoHelper.get().getInventoryWatcher().onCloseScreen();
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void addCustomWidgets(CallbackInfo ci) {
        var moduleManager = ModuleManager.getInstance();
        var widgets = moduleManager.getWidgets();
        log.trace("Adding {} custom widgets", widgets.size());
        widgets.forEach(this::addDrawableChild);

        moduleManager.onRevalidation((revalidatedWidgets) -> {
            log.trace(
                "Adding {} custom widgets after revalidation (inventory loaded)",
                revalidatedWidgets.size()
            );
            revalidatedWidgets.forEach(this::addDrawableChild);
        });
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void onMouseScrolled(
        double mouseX,
        double mouseY,
        double horizontalAmount,
        double verticalAmount,
        CallbackInfoReturnable<Boolean> cir
    ) {
        for (Element child : this.children()) {
            if (child.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
                cir.setReturnValue(true);
                return;
            }
        }
    }

    @Inject(method = "onMouseClick(Lnet/minecraft/screen/slot/Slot;IILnet/minecraft/screen/slot/SlotActionType;)V", at = @At("HEAD"), cancellable = true)
    private void onHandledMouseClick(
        Slot slot,
        int slotId,
        int button,
        SlotActionType actionType,
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

    @Inject(method = "drawSlot", at = @At("TAIL"))
    private void afterDrawSlot(DrawContext context, Slot slot, CallbackInfo ci) {
        if (!ScreenInfoHelper.inMenu(ScreenInfoHelper.BazaarMenuType.Orders)) {
            return;
        }
        if (slot.getStack().isEmpty()) {
            return;
        }

        var player = MinecraftClient.getInstance().player;
        if (player == null || slot.inventory == player.getInventory()) {
            return;
        }

        var x = slot.x;
        var y = slot.y;
        var idx = slot.getIndex();

        BtrBz
            .highlightManager()
            .getHighlight(idx)
            .ifPresent(color -> context.fill(x, y, x + 16, y + 16, color));
    }
}
