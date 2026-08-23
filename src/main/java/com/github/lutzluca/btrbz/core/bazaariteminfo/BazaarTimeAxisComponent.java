package com.github.lutzluca.btrbz.core.bazaariteminfo;

import com.github.lutzluca.btrbz.core.widgets.ui.BazaarStyles;
import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import java.time.ZoneId;
import java.util.Objects;
import net.minecraft.client.Minecraft;

/** Shared X-axis labels rendered below the final visible History plot. */
public final class BazaarTimeAxisComponent extends BaseUIComponent {
    private final BazaarHistoryPanelController controller;

    public BazaarTimeAxisComponent(Sizing horizontal, Sizing vertical, BazaarHistoryPanelController controller) {
        this.controller = Objects.requireNonNull(controller, "controller");
        this.sizing(horizontal, vertical);
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        if (!this.controller.showBuy() && !this.controller.showSell()) {
            return;
        }
        var font = Minecraft.getInstance().font;
        for (var tick : this.controller.ticks(this.x, this.width, ZoneId.systemDefault())) {
            int x = Math.max(this.x, Math.min(this.x + this.width - font.width(tick.label()),
                tick.x() - font.width(tick.label()) / 2));
            graphics.text(font, tick.label(), x, this.y, BazaarStyles.MUTED_TEXT, false);
        }
    }
}
