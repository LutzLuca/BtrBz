package com.github.lutzluca.btrbz.core.widgets;

import com.github.lutzluca.btrbz.widgets.framework.ui.WidgetSurfaceText;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import net.minecraft.network.chat.Component;

final class BazaarLabelComponent extends LabelComponent {
    BazaarLabelComponent(Component text) {
        super(text);
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        this.drawText((renderX, renderY, text, shadow, color) -> WidgetSurfaceText.draw(
            graphics,
            this.textRenderer,
            text,
            renderX,
            renderY,
            color.argb(),
            shadow
        ));
    }
}
