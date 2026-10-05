package com.github.lutzluca.btrbz.core.ui;

import io.wispforest.owo.mixin.ui.access.AbstractWidgetAccessor;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** A native button with one centered icon and text group. */
final class IconTextButton extends ButtonComponent {
    private static final int ICON_SIZE = 16;
    private static final int ICON_GAP = 4;
    private final @Nullable ItemStack item;
    private final @Nullable Identifier texture;
    private final int sourceSize;

    IconTextButton(String label, ItemStack item, Runnable action) {
        this(label, item.copy(), null, 0, action);
    }

    IconTextButton(String label, Identifier texture, int sourceSize, Runnable action) {
        this(label, null, texture, sourceSize, action);
    }

    private IconTextButton(
        String label,
        @Nullable ItemStack item,
        @Nullable Identifier texture,
        int sourceSize,
        Runnable action
    ) {
        super(Component.literal(label), _ -> action.run());
        this.item = item;
        this.texture = texture;
        this.sourceSize = sourceSize;
        this.textShadow(false);
        this.sizing(Sizing.fixed(Minecraft.getInstance().font.width(this.getMessage()) + ICON_SIZE + ICON_GAP + 16),
            Sizing.fixed(22));
        this.renderer(UiControls.buttonRenderer(false, false));
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        var context = (OwoUIGraphics) graphics;
        this.renderer.draw(context, this, delta);
        var font = Minecraft.getInstance().font;
        int groupWidth = ICON_SIZE + ICON_GAP + font.width(this.getMessage());
        int iconX = this.getX() + (this.getWidth() - groupWidth) / 2;
        int iconY = this.getY() + (this.getHeight() - ICON_SIZE) / 2;
        if (this.item != null) {
            graphics.item(this.item, iconX, iconY);
        } else if (this.texture != null) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, this.texture, iconX, iconY, 0, 0, ICON_SIZE, ICON_SIZE,
                this.sourceSize, this.sourceSize, this.sourceSize, this.sourceSize);
        }
        graphics.text(font, this.getMessage(), iconX + ICON_SIZE + ICON_GAP,
            this.getY() + (this.getHeight() - 8) / 2, this.active() ? 0xFFFFFFFF : 0xFFA0A0A0, this.textShadow());

        // Keep the native ButtonComponent tooltip path alongside the custom content.
        var tooltip = ((AbstractWidgetAccessor) (Object) this).owo$getTooltip();
        if (this.isHovered() && tooltip.get() != null) {
            graphics.setTooltipForNextFrame(font, tooltip.get().toCharSequence(Minecraft.getInstance()),
                DefaultTooltipPositioner.INSTANCE, mouseX, mouseY, false);
        }
    }
}
