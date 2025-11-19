package com.github.lutzluca.btrbz.mixin;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.OrderProtectionManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?}

@Mixin(DrawContext.class)
public class DrawContextMixin {

    @Unique
    private static final Identifier BOOKMARK_ICON = Identifier.of(
        BtrBz.MOD_ID,
        "textures/bookmark.png"
    );
    @Unique
    private static final Identifier BOOKMARK_STAR = Identifier.of(
        BtrBz.MOD_ID,
        "textures/bookmark-star.png"
    );

    @Unique
    private static final Identifier GREEN_CHECK = Identifier.of(
        BtrBz.MOD_ID,
        "textures/green-check.png"
    );
    @Unique
    private static final Identifier RED_CROSS = Identifier.of(
        BtrBz.MOD_ID,
        "textures/red-cross.png"
    );

    @Inject(method = "drawItem(Lnet/minecraft/item/ItemStack;III)V", at = @At("TAIL"))
    private void drawIndicator(ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
        @Nullable var isBookmarked = stack.get(BtrBz.BOOKMARKED);

        DrawContext context = (DrawContext) (Object) this;
        int iconSize = 8;

        if (isBookmarked != null) {
            var texture = isBookmarked ? BOOKMARK_STAR : BOOKMARK_ICON;
            //? if >=1.21.6 {
            /*context.drawTexture(
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
        *///?} else {
            context.drawTexture(
                RenderLayer::getGuiTexturedOverlay,
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
            //?}
        }

        var info = OrderProtectionManager.getInstance().getVisualOrderInfo(stack);
        if (info.isPresent()) {
            var pending = info.get();
            var overridden = pending.getRight();
            var blocked = pending.getLeft().validationResult().protect();

            var texture = !blocked || overridden ? GREEN_CHECK : RED_CROSS;
            int iconX = x + 16 - iconSize;
            int iconY = y;

            //? if >=1.21.6 {
            /*context.drawTexture(
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
        *///?} else {
            context.drawTexture(
                RenderLayer::getGuiTexturedOverlay,
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
            //?}
        }
    }
}