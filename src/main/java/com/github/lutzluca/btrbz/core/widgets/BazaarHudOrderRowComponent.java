package com.github.lutzluca.btrbz.core.widgets;

import com.github.lutzluca.btrbz.widgets.framework.ui.WidgetLayoutTokens;
import io.wispforest.owo.ui.base.BaseParentUIComponent;
import io.wispforest.owo.ui.component.ItemComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.ParentUIComponent;
import io.wispforest.owo.ui.core.Size;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;

import static com.github.lutzluca.btrbz.core.widgets.BazaarUi.ellipsize;

/** Two-line, freshness-safe HUD row: identity first, status and optional facts second. */
final class BazaarHudOrderRowComponent extends BaseParentUIComponent {
    static final int ICON_SIZE = 16;
    static final int HEIGHT = 21;

    private static final int TEXT_GAP = 4;
    private static final int ICON_GAP = 3;

    private final BazaarData.Order order;
    private final BazaarWidgetOptions.Hud options;
    private final Component productName;
    private final ItemComponent item;
    private final List<UIComponent> children;

    BazaarHudOrderRowComponent(BazaarData.Order order, BazaarWidgetOptions.Hud options) {
        super(Sizing.fill(100), Sizing.fixed(HEIGHT));
        this.order = order;
        this.options = options;
        this.productName = order.formattedItemName(options.abbreviateEnchanted());
        this.item = options.showItem() ? BazaarUi.item(order.iconCopy(), ICON_SIZE) : null;
        this.children = this.item == null ? List.of() : List.of(this.item);
        this.allowOverflow(true);
    }

    @Override
    public void layout(Size space) {
        if (this.item == null) return;
        this.item.inflate(Size.of(ICON_SIZE, ICON_SIZE));
        this.item.mount(this, this.x + WidgetLayoutTokens.ROW_HORIZONTAL_PADDING, this.y + 1);
    }

    @Override public List<UIComponent> children() { return this.children; }

    @Override
    public ParentUIComponent removeChild(UIComponent child) {
        throw new UnsupportedOperationException("HUD order row owns its item component");
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        super.draw(graphics, mouseX, mouseY, partialTicks, delta);
        var font = Minecraft.getInstance().font;
        int x = this.x + WidgetLayoutTokens.ROW_HORIZONTAL_PADDING;
        if (this.item != null) {
            this.drawChildren(graphics, mouseX, mouseY, partialTicks, delta, this.children);
            x += ICON_SIZE + ICON_GAP;
        }

        var side = Component.literal(this.order.side().label()).withStyle(ChatFormatting.BOLD);
        int right = this.x + this.width - WidgetLayoutTokens.ROW_HORIZONTAL_PADDING;
        int sideX = right - font.width(side);
        graphics.text(font, ellipsize(this.productName, Math.max(0, sideX - TEXT_GAP - x)),
            x, this.y + 1, BazaarStyles.PRIMARY_TEXT, false);
        graphics.text(font, side, sideX, this.y + 1, this.order.side().accentColor(), false);

        var status = Component.literal(this.order.status().label()).withStyle(ChatFormatting.BOLD);
        int secondY = this.y + 1 + font.lineHeight + WidgetLayoutTokens.LINE_GAP;
        graphics.text(font, status, x, secondY, this.order.status().color(), false);
        int detailX = x + font.width(status);
        var details = BazaarOrderText.joined(BazaarOrderText.optionalDetails(
            this.order, this.options.showVolume(), this.options.priceDisplay(), false
        ));
        var prefix = details.isBlank() ? Component.empty() : Component.literal(" · ");
        int leftDetailWidth = font.width(prefix) + font.width(details);
        var marketCandidates = BazaarOrderText.hudMarketCandidates(
            this.order, this.options.queueDisplay(), this.options.undercutDetail()
        );
        String marketText = firstFittingMarketText(
            marketCandidates,
            Math.max(0, right - detailX - leftDetailWidth - TEXT_GAP)
        );
        int marketX = marketText.isBlank() ? right : right - font.width(marketText);

        if (!details.isBlank()) {
            graphics.text(font, prefix, detailX, secondY, BazaarStyles.MUTED_TEXT, false);
            detailX += font.width(prefix);
            graphics.text(font, ellipsize(Component.literal(details), Math.max(0, marketX - TEXT_GAP - detailX)),
                detailX, secondY, BazaarStyles.SECONDARY_TEXT, false);
        }
        if (!marketText.isBlank()) {
            graphics.text(font, Component.literal(marketText), marketX, secondY, BazaarStyles.SECONDARY_TEXT, false);
        }
    }

    private static String firstFittingMarketText(List<String> candidates, int availableWidth) {
        var font = Minecraft.getInstance().font;
        for (var candidate : candidates) {
            if (font.width(candidate) <= availableWidth) return candidate;
        }
        return "";
    }
}
