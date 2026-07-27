package com.github.lutzluca.btrbz.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.Slot;
import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import com.github.lutzluca.btrbz.utils.slot.VirtualSlotProjection;
import com.github.lutzluca.btrbz.core.widgets.WidgetCanvas;
import com.github.lutzluca.btrbz.core.widgets.WidgetHostOptions;
import com.github.lutzluca.btrbz.core.widgets.WidgetHost;
import com.github.lutzluca.btrbz.core.widgets.WidgetHostOwner;
import net.minecraft.client.Minecraft;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin implements WidgetHostOwner {
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

    @Inject(method = "extractSlot", at = @At("HEAD"))
    private void renderOrderHighlight(
        GuiGraphicsExtractor context,
        Slot slot,
        int mouseX,
        int mouseY,
        CallbackInfo ci
    )
    {
        if (!ScreenInfoHelper.inMenu(ScreenInfoHelper.BazaarMenuType.Orders)) {
            return;
        }

        var rawStack = VirtualSlotProjection.withProjectionSuppressed(slot::getItem);
        if (rawStack.isEmpty() || GameUtils.isPlayerInventorySlot(slot)) {
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
        if (this.btrbz$widgetHost().keyPressed(event)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void onMouseScrolled(double mouseX, double mouseY, double hAmt, double vAmt, CallbackInfoReturnable<Boolean> cir) {
        if (this.btrbz$widgetHost().mouseScrolled(mouseX, mouseY, hAmt, vAmt)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (this.btrbz$widgetHost().mouseClicked(event, doubleClick)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void onMouseReleased(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (this.btrbz$widgetHost().mouseReleased(event)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void onMouseDragged(MouseButtonEvent event, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
        if (this.btrbz$widgetHost().mouseDragged(event, deltaX, deltaY)) {
            cir.setReturnValue(true);
        }
    }
}
