package com.github.lutzluca.btrbz.core.bazaariteminfo;

import com.github.lutzluca.btrbz.core.widgets.ui.BazaarStyles;
import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import java.util.Objects;
import net.minecraft.client.Minecraft;

/** Enlarged side title and full-side totals kept together above one Order Book table. */
final class BazaarItemInfoOrderSideHeaderComponent extends BaseUIComponent {
    private final String title;
    private final int accent;
    private String totals = "";
    private int rowHeight;

    BazaarItemInfoOrderSideHeaderComponent(String title, int accent, int rowHeight) {
        this.title = Objects.requireNonNull(title, "title");
        this.accent = accent;
        this.rowHeight = rowHeight;
        this.sizing(Sizing.fill(100), Sizing.fixed(rowHeight * 2));
    }

    void update(String totals, int rowHeight) {
        this.totals = Objects.requireNonNull(totals, "totals");
        this.rowHeight = rowHeight;
        this.verticalSizing(Sizing.fixed(rowHeight * 2));
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        var font = Minecraft.getInstance().font;
        int lineHeight = BazaarItemInfoOrderText.lineHeight(font.lineHeight);
        int titleY = this.y + Math.max(0, (this.rowHeight - lineHeight) / 2);
        int totalsY = this.y + this.rowHeight + Math.max(0, (this.rowHeight - lineHeight) / 2);
        BazaarItemInfoOrderText.draw(graphics, font, this.title, this.x + 3, titleY, this.accent);
        BazaarItemInfoOrderText.draw(
            graphics, font, this.totals, this.x + 3, totalsY, BazaarStyles.MUTED_TEXT);
    }
}
