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
    private final String relationship;
    private final int accent;
    private String totals = "";
    private int rowHeight;

    BazaarItemInfoOrderSideHeaderComponent(String title, String relationship, int accent, int rowHeight) {
        this.title = Objects.requireNonNull(title, "title");
        this.relationship = Objects.requireNonNull(relationship, "relationship");
        this.accent = accent;
        this.rowHeight = rowHeight;
        this.sizing(Sizing.fill(100), Sizing.fixed(rowHeight * 3));
    }

    void update(String totals, int rowHeight) {
        this.totals = Objects.requireNonNull(totals, "totals");
        this.rowHeight = rowHeight;
        this.verticalSizing(Sizing.fixed(rowHeight * 3));
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        var font = Minecraft.getInstance().font;
        int lineHeight = BazaarItemInfoOrderText.lineHeight(font.lineHeight);
        int titleY = this.y + Math.max(0, (this.rowHeight - lineHeight) / 2);
        int relationshipY = this.y + this.rowHeight + Math.max(0, (this.rowHeight - lineHeight) / 2);
        int totalsY = this.y + this.rowHeight * 2 + Math.max(0, (this.rowHeight - lineHeight) / 2);
        graphics.enableScissor(this.x, this.y, this.x + this.width, this.y + this.height);
        BazaarItemInfoOrderText.draw(graphics, font, this.title, this.x + 3, titleY, this.accent);
        BazaarItemInfoOrderText.draw(
            graphics, font, this.relationship, this.x + 3, relationshipY, BazaarStyles.SECONDARY_TEXT);
        BazaarItemInfoOrderText.draw(
            graphics, font, this.totals, this.x + 3, totalsY, BazaarStyles.MUTED_TEXT);
        graphics.disableScissor();
    }
}
