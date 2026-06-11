package com.github.lutzluca.btrbz.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import com.github.lutzluca.btrbz.utils.slot.SlotClickContext;
import com.github.lutzluca.btrbz.utils.slot.SlotHookRegistry;
import com.github.lutzluca.btrbz.utils.slot.SlotInputModifiers;
import com.github.lutzluca.btrbz.utils.slot.SlotRenderContext;
import com.github.lutzluca.btrbz.utils.slot.SlotView;

@Mixin(AbstractContainerScreen.class)
public abstract class VirtualSlotScreenMixin {

    /*
     * virtual slot hooks intentionally run before vanilla slot rendering and click dispatch.
     * `Catharsis` wraps the item draw inside `renderSlot` and records the current `Slot` while
     * resolving item models. If `BtrBz` sends a virtual stack through that path, `Catharsis`
     * can still replace or hide the model. Rendering at `renderSlot` HEAD and cancelling for
     * virtual stacks keeps rendering owned by the slot render phase while bypassing that
     * wrapped draw call. 
     * `Catharsis` also wraps `slotClicked` and may return before calling the
     * original for non-clickable layout slots, so `BtrBz` intercepts the `mouseClicked` ->
     * `slotClicked` call site instead of injecting into `slotClicked` directly.
     */

    @Inject(method = "renderSlot", at = @At("HEAD"), cancellable = true)
    //? if >=1.21.11 {
    /*private void renderVirtualSlot(
        GuiGraphics graphics,
        Slot slot,
        int mouseX,
        int mouseY,
        CallbackInfo ci
    )
    *///?} else {
    private void renderVirtualSlot(
        GuiGraphics graphics,
        Slot slot,
        CallbackInfo ci
    )
    //?}
    {
        var raw = slot.getItem();
        var display = this.getDisplayStack(slot, raw);
        if (display == raw) {
            return;
        }

        graphics.renderItem(display, slot.x, slot.y);
        graphics.renderItemDecorations(Minecraft.getInstance().font, display, slot.x, slot.y);
        ci.cancel();
    }

    @WrapOperation(
        method = "mouseClicked",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ClickType;)V"
        )
    )
    private void dispatchSlotClick(
        AbstractContainerScreen<?> screen,
        Slot slot,
        int slotId,
        int button,
        ClickType type,
        Operation<Void> original
    ) {
        if (slot != null && this.handleSlotHook(slot, button, type)) {
            return;
        }

        original.call(screen, slot, slotId, button, type);
    }

    @ModifyVariable(method = "renderTooltip", at = @At("STORE"), ordinal = 0)
    private ItemStack modifyTooltipStack(ItemStack rawStack, GuiGraphics graphics, int mouseX, int mouseY) {
        var hoveredSlot = ((AbstractContainerScreenAccessor) this).getHoveredSlot();
        if (hoveredSlot == null) {
            return rawStack;
        }

        return this.getDisplayStack(hoveredSlot, rawStack);
    }

    private boolean handleSlotHook(Slot slot, int button, ClickType type) {
        var context = new SlotClickContext(
            this.createSlotView(slot, slot.getItem()),
            type,
            button,
            SlotInputModifiers.from(Minecraft.getInstance())
        );

        return SlotHookRegistry.handleClick(context);
    }

    private ItemStack getDisplayStack(Slot slot, ItemStack rawStack) {
        return SlotHookRegistry.getDisplayStack(
            new SlotRenderContext(this.createSlotView(slot, rawStack))
        );
    }

    private SlotView createSlotView(Slot slot, ItemStack rawStack) {
        var helper = ScreenInfoHelper.get();
        return new SlotView(
            helper.getCurrInfo(),
            helper.getPrevInfo(),
            slot,
            rawStack,
            GameUtils.isPlayerInventorySlot(slot)
        );
    }
}
