package com.github.lutzluca.btrbz.core.widgets.hud;

import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarOrderText;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarStyles;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetLayoutTokens;
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
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

import static com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi.ellipsize;

/** Two-line, freshness-safe HUD row: identity first, status and optional facts second. */
final class BazaarHudOrderRowComponent extends BaseParentUIComponent {
    static final int ICON_SIZE = 16;
    static final int HEIGHT = 21;

    private static final int TEXT_GAP = 4;
    private static final int ICON_GAP = 3;

    private BazaarWidgetViewData.Order order;
    private BazaarOrdersWidgetConfig options;
    private Component productName;
    private @Nullable ItemComponent item;
    private boolean showItem;

    BazaarHudOrderRowComponent(BazaarWidgetViewData.Order order, BazaarOrdersWidgetConfig options) {
        super(Sizing.fill(100), Sizing.fixed(HEIGHT));
        this.allowOverflow(true);
        this.update(order, options);
    }

    void update(BazaarWidgetViewData.Order order, BazaarOrdersWidgetConfig options) {
        this.order = order;
        this.options = options;
        this.productName = order.formattedItemName(options.abbreviateEnchanted);
        var itemStack = order.itemStack();
        this.showItem = options.showItem && itemStack.isPresent();
        if (itemStack.isPresent()) {
            if (this.item == null) {
                this.item = BazaarUi.item(itemStack.orElseThrow(), ICON_SIZE);
            } else {
                this.item.stack(itemStack.orElseThrow());
            }
        }
        this.updateLayout();
    }

    @Override
    public void layout(Size space) {
        if (!this.showItem) return;
        this.itemComponent().inflate(Size.of(ICON_SIZE, ICON_SIZE));
        this.itemComponent().mount(this, this.x + WidgetLayoutTokens.ROW_HORIZONTAL_PADDING, this.y + 1);
    }

    @Override public List<UIComponent> children() {
        return this.showItem ? List.of(this.itemComponent()) : List.of();
    }

    @Override
    public ParentUIComponent removeChild(UIComponent child) {
        throw new UnsupportedOperationException("HUD order row owns its item component");
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        super.draw(graphics, mouseX, mouseY, partialTicks, delta);
        var font = Minecraft.getInstance().font;
        int x = this.x + WidgetLayoutTokens.ROW_HORIZONTAL_PADDING;
        if (this.showItem) {
            this.drawChildren(graphics, mouseX, mouseY, partialTicks, delta, List.of(this.itemComponent()));
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
            this.order, this.options.showVolume, this.options.priceDisplay, false
        ));
        var prefix = details.isBlank() ? Component.empty() : Component.literal(" · ");
        int leftDetailWidth = font.width(prefix) + font.width(details);
        var marketCandidates = BazaarOrderText.hudMarketCandidates(
            this.order, this.options.queueDisplay, this.options.undercutDetail
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

    private ItemComponent itemComponent() {
        return Objects.requireNonNull(this.item, "item");
    }
}
