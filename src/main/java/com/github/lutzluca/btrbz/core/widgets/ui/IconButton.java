package com.github.lutzluca.btrbz.core.widgets.ui;

import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

/** Texture button with a tooltip and an accessible description. */
public final class IconButton extends ButtonComponent {
    private final Component description;

    public IconButton(Identifier texture, Component description, Runnable action, int sourceSize, Renderer background) {
        super(Component.empty(), _ -> action.run());
        this.description = description;
        this.tooltip(description);
        this.sizing(Sizing.fixed(22));
        this.renderer((graphics, button, delta) -> {
            background.draw(graphics, button, delta);
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture, button.getX() + 3, button.getY() + 3,
                0, 0, 16, 16, sourceSize, sourceSize, sourceSize, sourceSize);
        });
    }

    @Override
    protected MutableComponent createNarrationMessage() {
        return Component.translatable("gui.narrate.button", this.description);
    }
}
