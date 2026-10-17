package com.github.lutzluca.btrbz.mixin;

import com.github.lutzluca.btrbz.Assets;
import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.config.ConfigStore;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphicsExtractor.class)
public class DrawContextMixin {

    @Inject(method = "item(Lnet/minecraft/world/item/ItemStack;III)V", at = @At("TAIL"))
    private void drawIndicator(ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
        if (!BtrBz.isRunning()) {
            return;
        }

        GuiGraphicsExtractor context = (GuiGraphicsExtractor) (Object) this;
        int iconSize = 8;
        var claimable = ConfigStore.get().config().claimableOrderIndicator;
        if (claimable.enabled && Boolean.TRUE.equals(stack.get(BtrBz.CLAIMABLE))) {
            context.blit(RenderPipelines.GUI_TEXTURED, Assets.CLAIMABLE, x, y, 0, 0,
                iconSize, iconSize, iconSize, iconSize);
        }

        if (!BtrBz.isActive()) {
            return;
        }

        @Nullable
        var isBookmarked = stack.get(BtrBz.BOOKMARKED);

        if (isBookmarked != null && ConfigStore.get().config().widgets.bookmarks.frame.enabled) {
            var texture = isBookmarked ? Assets.BOOKMARK_STAR : Assets.BOOKMARK_ICON;
            context.blit(
                RenderPipelines.GUI_TEXTURED,
                texture,
                x,
                y,
                0,
                0,
                iconSize,
                iconSize,
                iconSize,
                iconSize);
        }

        var info = BtrBz.orderProtectionManager().getVisualOrderInfo(stack);
        if (info.isPresent()) {
            var pending = info.get();
            var overridden = pending.getRight();
            var blocked = pending.getLeft().protect();

            var texture = !blocked || overridden ? Assets.GREEN_CHECK : Assets.RED_CROSS;
            int iconX = x + 16 - iconSize;
            int iconY = y;

            context.blit(
                RenderPipelines.GUI_TEXTURED,
                texture,
                iconX,
                iconY,
                0,
                0,
                iconSize,
                iconSize,
                iconSize,
                iconSize);
        }
    }
}
