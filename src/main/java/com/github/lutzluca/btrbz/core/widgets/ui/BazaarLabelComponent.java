package com.github.lutzluca.btrbz.core.widgets.ui;

import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

final class BazaarLabelComponent extends LabelComponent {
    private final RetainedTextRow retainedText = new RetainedTextRow();

    BazaarLabelComponent(Component text) {
        super(text);
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        // owo accounts for GUI scale, while widgets add another matrix scale.
        // Derive the local offset which still maps to one framebuffer pixel.
        double verticalScale = Math.hypot(graphics.pose().m10(), graphics.pose().m11());
        double pixelOffset = 1.0 / (Minecraft.getInstance().getWindow().getGuiScale() * verticalScale);

        // drawText invokes the callback once per wrapped line, in order.
        this.retainedText.begin();

        graphics.push();
        try {
            graphics.translate(0, pixelOffset);
            this.drawText((renderX, renderY, text, shadow, color) -> this.retainedText.draw(
                graphics, this.textRenderer, text, renderX, renderY, color.argb(), shadow));
        } finally {
            graphics.pop();
        }
    }
}
