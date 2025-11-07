package com.github.lutzluca.btrbz.mixin;

import com.github.lutzluca.btrbz.BtrBz;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >=1.21.6 {
/*import net.minecraft.client.gl.RenderPipelines;
 *///?} else {
import net.minecraft.client.render.RenderLayer;
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

    @Inject(method = "drawItem(Lnet/minecraft/item/ItemStack;III)V", at = @At("TAIL"))
    private void drawBookmarkIndicator(ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
        var isBookmarked = stack.get(BtrBz.BOOKMARKED);
        if (isBookmarked == null) {
            return;
        }

        DrawContext context = (DrawContext) (Object) this;
        int iconSize = 8;
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
}