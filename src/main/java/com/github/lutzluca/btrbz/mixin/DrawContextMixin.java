package com.github.lutzluca.btrbz.mixin;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.OrderProtectionManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphics.class)
public class DrawContextMixin {

    @Unique
    private static final ResourceLocation BOOKMARK_ICON = ResourceLocation.fromNamespaceAndPath(
        BtrBz.MOD_ID,
        "textures/bookmark.png"
    );
    @Unique
    private static final ResourceLocation BOOKMARK_STAR = ResourceLocation.fromNamespaceAndPath(
        BtrBz.MOD_ID,
        "textures/bookmark-star.png"
    );

    @Unique
    private static final ResourceLocation GREEN_CHECK = ResourceLocation.fromNamespaceAndPath(
        BtrBz.MOD_ID,
        "textures/green-check.png"
    );
    @Unique
    private static final ResourceLocation RED_CROSS = ResourceLocation.fromNamespaceAndPath(
        BtrBz.MOD_ID,
        "textures/red-cross.png"
    );

    @Inject(method = "renderItem(Lnet/minecraft/world/item/ItemStack;III)V", at = @At("TAIL"))
    private void drawIndicator(ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
        @Nullable var isBookmarked = stack.get(BtrBz.BOOKMARKED);

        GuiGraphics context = (GuiGraphics) (Object) this;
        int iconSize = 8;

        if (isBookmarked != null) {
            var texture = isBookmarked ? BOOKMARK_STAR : BOOKMARK_ICON;
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
                iconSize
            );
        }

        var info = OrderProtectionManager.getInstance().getVisualOrderInfo(stack);
        if (info.isPresent()) {
            var pending = info.get();
            var overridden = pending.getRight();
            var blocked = pending.getLeft().validationResult().protect();

            var texture = !blocked || overridden ? GREEN_CHECK : RED_CROSS;
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
                iconSize
            );
        }
    }
}