package com.github.lutzluca.btrbz.core.widgets.hud;

import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarOrderText;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarStyles;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi;
import com.github.lutzluca.btrbz.core.widgets.ui.RetainedTextRow;
import com.github.lutzluca.btrbz.core.widgets.ui.TextRenderRevision;
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
import net.minecraft.util.FormattedCharSequence;
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

    private final RetainedTextRow retainedText = new RetainedTextRow();

    private BazaarWidgetViewData.Order order;
    private BazaarOrdersWidgetConfig options;
    private Component productName;
    private String identity;
    private List<String> marketCandidates;

    private @Nullable ItemComponent item;
    private @Nullable LayoutKey lastLayoutKey;
    private @Nullable DrawLayout drawLayout;
    private int drawLayoutWidth = -1;
    private long drawLayoutRevision = -1;

    BazaarHudOrderRowComponent(BazaarWidgetViewData.Order order, BazaarOrdersWidgetConfig options) {
        super(Sizing.fill(100), Sizing.fixed(HEIGHT));
        this.allowOverflow(true);
        this.update(order, options);
    }

    void update(BazaarWidgetViewData.Order order, BazaarOrdersWidgetConfig options) {
        var productName = order.formattedItemName(options.abbreviateEnchanted);
        var itemStack = order.itemStack();
        var identity = BazaarOrderText.orderIdentity(order);
        var marketCandidates = BazaarOrderText.marketPositionCandidates(
            order, options.showQueue, options.showUndercutGap);

        var layoutKey = new LayoutKey(
            productName, itemStack.isPresent(), order.side(), order.status(),
            identity, marketCandidates);

        boolean layoutChanged = !layoutKey.equals(this.lastLayoutKey);

        this.order = order;
        this.options = options;
        this.productName = productName;
        this.identity = identity;
        this.marketCandidates = marketCandidates;
        this.item = BazaarUi.reconcileItem(this.item, itemStack, ICON_SIZE);

        if (layoutChanged) {
            this.lastLayoutKey = layoutKey;
            this.drawLayout = null;
        }

        this.updateLayout();
    }

    @Override
    public void layout(Size space) {
        if (this.item == null) {
            return;
        }

        this.itemComponent().inflate(Size.of(ICON_SIZE, ICON_SIZE));
        int iconX = this.x + LEFT_PADDING;
        int iconY = this.y + (HEIGHT - ICON_SIZE) / 2;
        this.itemComponent().mount(this, iconX, iconY);
    }

    @Override
    public List<UIComponent> children() {
        return this.item == null ? List.of() : List.of(this.itemComponent());
    }

    @Override
    public ParentUIComponent removeChild(UIComponent child) {
        throw new UnsupportedOperationException("HUD order row owns its item component");
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        super.draw(graphics, mouseX, mouseY, partialTicks, delta);

        if (this.item != null) {
            this.drawChildren(graphics, mouseX, mouseY, partialTicks, delta, List.of(this.itemComponent()));
        }

        long revision = TextRenderRevision.current();

        if (this.drawLayout == null || this.drawLayoutWidth != this.width || this.drawLayoutRevision != revision) {
            this.drawLayout = this.computeDrawLayout();
            this.drawLayoutWidth = this.width;
            this.drawLayoutRevision = revision;
        }

        var layout = this.drawLayout;
        var font = Minecraft.getInstance().font;
        int firstY = this.y + 1;
        int secondY = this.y + 10;

        this.retainedText.begin();

        this.retainedText.draw(graphics, font, layout.productName(),
            this.x + layout.productX(), firstY, BazaarStyles.PRIMARY_TEXT, false);
        this.retainedText.draw(graphics, font, layout.status(),
            this.x + layout.statusX(), firstY, this.order.status().color(), false);
        this.retainedText.draw(graphics, font, layout.side(),
            this.x + layout.sideX(), firstY, this.order.side().accentColor(), false);

        if (layout.identity() != null) {
            this.retainedText.draw(graphics, font, layout.identity(),
                this.x + layout.identityX(), secondY, BazaarStyles.SECONDARY_TEXT, false);
        }

        if (layout.market() != null) {
            this.retainedText.draw(graphics, font, layout.market(),
                this.x + layout.marketX(), secondY, BazaarStyles.SECONDARY_TEXT, false);
        }
    }

    private DrawLayout computeDrawLayout() {
        var font = Minecraft.getInstance().font;
        int x = LEFT_PADDING;

        if (this.item != null) {
            x += ICON_SIZE + WidgetLayoutTokens.ORDER_TEXT_GAP;
        }

        var side = Component.literal(this.order.side().label()).withStyle(ChatFormatting.BOLD);
        var status = Component.literal(this.order.status().label()).withStyle(ChatFormatting.BOLD);
        int right = this.width - RIGHT_PADDING;
        int sideX = right - font.width(side);
        int statusX = sideX - WidgetLayoutTokens.ORDER_TEXT_GAP - font.width(status);

        String identity = this.identity;
        String marketText = BazaarUi.firstFittingText(
            this.marketCandidates,
            Math.max(0, right - x - font.width(identity) - WidgetLayoutTokens.ORDER_TEXT_GAP));
        int marketX = marketText.isBlank() ? right : right - font.width(marketText);

        return new DrawLayout(
            ellipsize(this.productName, Math.max(
                0, statusX - WidgetLayoutTokens.ORDER_TEXT_GAP - x)),
            x,
            status.getVisualOrderText(), statusX,
            side.getVisualOrderText(), sideX,
            identity.isBlank()
                ? null : ellipsize(Component.literal(identity), Math.max(
                    0, marketX - WidgetLayoutTokens.ORDER_TEXT_GAP - x)),
            x,
            marketText.isBlank() ? null : Component.literal(marketText).getVisualOrderText(), marketX);
    }

    private ItemComponent itemComponent() {
        return Objects.requireNonNull(this.item, "item");
    }

    private record DrawLayout(
        FormattedCharSequence productName, int productX,
        FormattedCharSequence status, int statusX,
        FormattedCharSequence side, int sideX,
        @Nullable FormattedCharSequence identity, int identityX,
        @Nullable FormattedCharSequence market, int marketX
    ) {}

    private record LayoutKey(
        Component productName,
        boolean hasItem,
        BazaarWidgetViewData.OrderSide side,
        BazaarWidgetViewData.OrderStatus status,
        String identity,
        List<String> marketCandidates
    ) {
        private LayoutKey {
            productName = productName.copy();
            marketCandidates = List.copyOf(marketCandidates);
        }
    }
}
