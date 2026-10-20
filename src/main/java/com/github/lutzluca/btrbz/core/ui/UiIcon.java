package com.github.lutzluca.btrbz.core.ui;

import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/** Texture artwork shared by decorative icons, descriptive help and buttons. */
public record UiIcon(Identifier texture, int sourceSize) {
    public void draw(GuiGraphicsExtractor graphics, int x, int y, int size) {
        this.draw(graphics, x, y, size, 0xFFFFFFFF);
    }

    public void draw(GuiGraphicsExtractor graphics, int x, int y, int size, int color) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, this.texture, x, y,
            0, 0, size, size, this.sourceSize, this.sourceSize, this.sourceSize, this.sourceSize, color);
    }

    /** Displays untinted artwork without a tooltip or keyboard or mouse focus. */
    public UIComponent component(int size) {
        return this.component(size, null);
    }

    /** A description opts into help text, theme tint and keyboard or mouse focus. */
    public UIComponent component(int size, @Nullable Component description) {
        var icon = new BaseUIComponent() {
            @Override
            public boolean canFocus(FocusSource source) {
                return description != null;
            }

            @Override
            public void drawFocusHighlight(
                OwoUIGraphics graphics,
                int mouseX,
                int mouseY,
                float partialTicks,
                float delta
            ) {
                graphics.drawRectOutline(this.x(), this.y(), this.width(), this.height(), UiStyles.palette().accent());
                if (!this.isInBoundingBox(mouseX, mouseY)) {
                    graphics.drawTooltip(Minecraft.getInstance().font,
                        this.x() + this.width(), this.y() + this.height(), this.tooltip());
                }
            }

            @Override
            public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
                UiIcon.this.draw(graphics, this.x(), this.y(), size,
                    description == null ? 0xFFFFFFFF : UiStyles.palette().textSecondary());
            }
        };
        icon.sizing(Sizing.fixed(size));
        if (description != null) {
            icon.tooltip(description);
        }
        return icon;
    }
}
