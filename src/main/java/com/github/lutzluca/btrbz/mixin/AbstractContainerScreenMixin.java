package com.github.lutzluca.btrbz.mixin;

import java.util.IdentityHashMap;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.ModuleManager;
import com.github.lutzluca.btrbz.utils.ClickOutcome;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import com.github.lutzluca.btrbz.utils.slot.SlotDisplaySnapshot;
import com.github.lutzluca.btrbz.utils.slot.SlotInterceptorManager;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

    @Shadow
    @Nullable
    protected Slot hoveredSlot;

    @Unique
    private SlotDisplaySnapshot btrbz$renderDisplaySnapshot = SlotDisplaySnapshot.EMPTY;

    @Unique
    private SlotDisplaySnapshot btrbz$tooltipDisplaySnapshot = SlotDisplaySnapshot.EMPTY;

    @Unique
    private final Map<Slot, SlotDisplaySnapshot> btrbz$displaySnapshots = new IdentityHashMap<>();

    @Inject(method = "onClose", at = @At("HEAD"))
    private void onClose(CallbackInfo ci) {
        this.btrbz$displaySnapshots.clear();
        ScreenInfoHelper.get().getInventoryWatcher().onCloseScreen();
        var wm = ModuleManager.getInstance().getWidgetManager();
        if (wm != null) {
            wm.cleanup();
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        var wm = ModuleManager.getInstance().getWidgetManager();
        if (wm != null) {
            wm.render(graphics, mouseX, mouseY, delta);
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void btrbz$beginRenderDisplayCache(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        this.btrbz$displaySnapshots.clear();
    }

    @Inject(method = "renderSlot", at = @At("HEAD"))
    //? if >=1.21.11 {
    /*private void btrbz$captureRenderDisplaySnapshot(
        GuiGraphics context,
        Slot slot,
        int mouseX,
        int mouseY,
        CallbackInfo ci
    )
    *///?} else {
    private void btrbz$captureRenderDisplaySnapshot(
        GuiGraphics context,
        Slot slot,
        CallbackInfo ci
    )
    //?}
    {
        this.btrbz$renderDisplaySnapshot = this.btrbz$createDisplaySnapshot(slot);
    }

    @Redirect(
        method = "renderSlot",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/Slot;getItem()Lnet/minecraft/world/item/ItemStack;"
        )
    )
    private ItemStack useDisplayItemForRenderedSlot(Slot slot) {
        return this.btrbz$renderDisplaySnapshot.displayItem();
    }

    @Inject(method = "renderSlot", at = @At("RETURN"))
    //? if >=1.21.11 {
    /*private void btrbz$clearRenderDisplaySnapshot(
        GuiGraphics context,
        Slot slot,
        int mouseX,
        int mouseY,
        CallbackInfo ci
    )
    *///?} else {
    private void btrbz$clearRenderDisplaySnapshot(
        GuiGraphics context,
        Slot slot,
        CallbackInfo ci
    )
    //?}
    {
        this.btrbz$renderDisplaySnapshot = SlotDisplaySnapshot.EMPTY;
    }

    @Inject(method = "renderTooltip", at = @At("HEAD"))
    private void btrbz$captureTooltipDisplaySnapshot(
        GuiGraphics context,
        int mouseX,
        int mouseY,
        CallbackInfo ci
    ) {
        this.btrbz$tooltipDisplaySnapshot = this.btrbz$createDisplaySnapshot(this.hoveredSlot);
    }

    @Redirect(
        method = "renderTooltip",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/Slot;hasItem()Z")
    )
    private boolean useDisplayPresenceForRenderedTooltip(Slot slot) {
        return this.btrbz$tooltipDisplaySnapshot.hasDisplayItem();
    }

    @Redirect(
        method = "renderTooltip",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/Slot;getItem()Lnet/minecraft/world/item/ItemStack;")
    )
    private ItemStack useDisplayItemForRenderedTooltip(Slot slot) {
        return this.btrbz$tooltipDisplaySnapshot.displayItem();
    }

    @Inject(method = "renderTooltip", at = @At("RETURN"))
    private void btrbz$clearTooltipDisplaySnapshot(
        GuiGraphics context,
        int mouseX,
        int mouseY,
        CallbackInfo ci
    ) {
        this.btrbz$tooltipDisplaySnapshot = SlotDisplaySnapshot.EMPTY;
    }

    @Inject(method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ClickType;)V", at = @At("HEAD"), cancellable = true)
    private void onSlotClicked(
        Slot slot,
        int slotId,
        int button,
        ClickType actionType,
        CallbackInfo ci
    ) {
        var ctx = SlotInterceptorManager.createClickContext(
            ScreenInfoHelper.get().getCurrInfo(),
            ScreenInfoHelper.get().getPrevInfo(),
            slot,
            button,
            actionType
        );

        var outcome = SlotInterceptorManager.handleClickAndObserve(ctx);

        if (outcome == ClickOutcome.Cancel) {
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

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void onMouseScrolled(double mouseX, double mouseY, double hAmt, double vAmt, CallbackInfoReturnable<Boolean> cir) {
        var wm = ModuleManager.getInstance().getWidgetManager();
        if (wm != null && wm.mouseScrolled(mouseX, mouseY, hAmt, vAmt)) {
            cir.setReturnValue(true);
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

    @Unique
    private SlotDisplaySnapshot btrbz$createDisplaySnapshot(@Nullable Slot slot) {
        if (slot == null) {
            return SlotDisplaySnapshot.EMPTY;
        }

        return this.btrbz$displaySnapshots.computeIfAbsent(
            slot,
            key -> SlotInterceptorManager.createDisplaySnapshot(
                ScreenInfoHelper.get().getCurrInfo(),
                ScreenInfoHelper.get().getPrevInfo(),
                key
            )
        );
    }
}
