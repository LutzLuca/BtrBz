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

/** Two-line HUD row with categorical state above order identity and market position. */
final class BazaarHudOrderRowComponent extends BaseParentUIComponent {
    static final int ICON_SIZE = 16;
    static final int LEFT_PADDING = WidgetLayoutTokens.ROW_HORIZONTAL_PADDING - 1;
    static final int RIGHT_PADDING = WidgetLayoutTokens.ROW_HORIZONTAL_PADDING - 1;
    static final int ICON_CELL_WIDTH = LEFT_PADDING
        + ICON_SIZE + WidgetLayoutTokens.ORDER_TEXT_GAP;
    static final int HEIGHT = 20;

    private BazaarWidgetViewData.Order order;
    private BazaarOrdersWidgetConfig options;
    private Component productName;
    private @Nullable ItemComponent item;

    BazaarHudOrderRowComponent(BazaarWidgetViewData.Order order, BazaarOrdersWidgetConfig options) {
        super(Sizing.fill(100), Sizing.fixed(HEIGHT));
        this.allowOverflow(true);
        this.update(order, options);
    }

    void update(BazaarWidgetViewData.Order order, BazaarOrdersWidgetConfig options) {
        this.order = order;
        this.options = options;
        this.productName = order.formattedItemName(options.abbreviateEnchanted);
        this.item = BazaarUi.reconcileItem(this.item, order.itemStack(), ICON_SIZE);
        this.updateLayout();
    }

    @Override
    public void layout(Size space) {
        if (this.item == null) return;
        this.itemComponent().inflate(Size.of(ICON_SIZE, ICON_SIZE));
        int iconX = this.x + LEFT_PADDING;
        int iconY = this.y + (HEIGHT - ICON_SIZE) / 2;
        this.itemComponent().mount(this, iconX, iconY);
    }

    @Override public List<UIComponent> children() {
        return this.item == null ? List.of() : List.of(this.itemComponent());
    }

    @Override
    public ParentUIComponent removeChild(UIComponent child) {
        throw new UnsupportedOperationException("HUD order row owns its item component");
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        super.draw(graphics, mouseX, mouseY, partialTicks, delta);
        var font = Minecraft.getInstance().font;
        int x = this.x + LEFT_PADDING;
        if (this.item != null) {
            this.drawChildren(graphics, mouseX, mouseY, partialTicks, delta, List.of(this.itemComponent()));
            x += ICON_SIZE + WidgetLayoutTokens.ORDER_TEXT_GAP;
        }

        var side = Component.literal(this.order.side().label()).withStyle(ChatFormatting.BOLD);
        var status = Component.literal(this.order.status().label()).withStyle(ChatFormatting.BOLD);
        int right = this.x + this.width - RIGHT_PADDING;
        int sideX = right - font.width(side);
        int statusX = sideX - WidgetLayoutTokens.ORDER_TEXT_GAP - font.width(status);
        graphics.text(font, ellipsize(this.productName, Math.max(
            0,
            statusX - WidgetLayoutTokens.ORDER_TEXT_GAP - x
        )),
            x, this.y + 1, BazaarStyles.PRIMARY_TEXT, false);
        graphics.text(font, status, statusX, this.y + 1, this.order.status().color(), false);
        graphics.text(font, side, sideX, this.y + 1, this.order.side().accentColor(), false);

        int secondY = this.y + 10;
        String identity = BazaarOrderText.orderIdentity(this.order);
        var marketCandidates = BazaarOrderText.marketPositionCandidates(
            this.order, this.options.showQueue, this.options.showUndercutGap
        );
        String marketText = BazaarUi.firstFittingText(
            marketCandidates,
            Math.max(0, right - x - font.width(identity) - WidgetLayoutTokens.ORDER_TEXT_GAP)
        );
        int marketX = marketText.isBlank() ? right : right - font.width(marketText);

        if (!identity.isBlank()) {
            graphics.text(
                font,
                ellipsize(Component.literal(identity), Math.max(
                    0,
                    marketX - WidgetLayoutTokens.ORDER_TEXT_GAP - x
                )),
                x,
                secondY,
                BazaarStyles.SECONDARY_TEXT,
                false
            );
        }
        if (!marketText.isBlank()) {
            graphics.text(
                font,
                Component.literal(marketText),
                marketX,
                secondY,
                BazaarStyles.SECONDARY_TEXT,
                false
            );
        }
    }

    private ItemComponent itemComponent() {
        return Objects.requireNonNull(this.item, "item");
    }
}
