package com.github.lutzluca.btrbz.core.widgets.trackedorders;

import com.github.lutzluca.btrbz.core.widgets.action.BazaarAction;
import com.github.lutzluca.btrbz.core.widgets.config.BazaarWidgetOptions;
import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarOrderText;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarStyles;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi;
import com.github.lutzluca.btrbz.widgets.framework.ui.PersistentMouseCapture;
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
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import static com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi.ellipsize;

/** Full-name tracked-order row with an optional, layout-stable live fill bar. */
final class BazaarTrackedOrderRowComponent extends BaseParentUIComponent implements PersistentMouseCapture {
    static final int STANDARD_HEIGHT = 24;
    static final int COMPACT_HEIGHT = 16;
    private static final int STANDARD_ICON_SIZE = 16;
    private static final int COMPACT_ICON_SIZE = 12;
    private static final int TEXT_GAP = 3;
    private static final int MINIMUM_PRODUCT_WIDTH = 36;
    private static final int STANDARD_PROGRESS_HEIGHT = 2;
    private static final int COMPACT_PROGRESS_HEIGHT = 1;

    private final BazaarWidgetViewData.Order order;
    private final BazaarWidgetOptions.TrackedOrders options;
    private final Component productName;
    private final ItemComponent item;
    private final List<UIComponent> children;
    private final int index;
    private final boolean reorderable;
    private final boolean interactive;
    private final TrackedOrderDragController drag;
    private final TrackedOrderHoverController hover;
    private final IntConsumer pointerMoved;
    private final Consumer<BazaarAction> actions;

    BazaarTrackedOrderRowComponent(
        BazaarWidgetViewData.Order order,
        BazaarWidgetOptions.TrackedOrders options,
        List<Component> tooltip,
        int index,
        boolean interactive,
        TrackedOrderDragController drag,
        TrackedOrderHoverController hover,
        IntConsumer pointerMoved,
        Consumer<BazaarAction> actions
    ) {
        super(Sizing.fill(100), Sizing.fixed(
            options.layout() == BazaarWidgetOptions.TrackedLayout.Compact ? COMPACT_HEIGHT : STANDARD_HEIGHT
        ));
        this.order = order;
        this.options = options;
        this.productName = order.formattedItemName(options.abbreviateEnchanted());
        int iconSize = options.layout() == BazaarWidgetOptions.TrackedLayout.Compact
            ? COMPACT_ICON_SIZE : STANDARD_ICON_SIZE;
        this.item = options.showItem() ? BazaarUi.item(order.iconCopy(), iconSize) : null;
        this.children = this.item == null ? List.of() : List.of(this.item);
        this.index = index;
        this.reorderable = interactive && options.sort() == BazaarWidgetOptions.TrackedSort.Manual;
        this.interactive = interactive;
        this.drag = drag;
        this.hover = hover;
        this.pointerMoved = pointerMoved;
        this.actions = actions;
        if (interactive && !tooltip.isEmpty()) this.tooltip(tooltip);
        this.allowOverflow(true);
    }

    @Override
    public void layout(Size space) {
        if (this.item == null) return;
        boolean compact = this.options.layout() == BazaarWidgetOptions.TrackedLayout.Compact;
        int iconSize = compact ? COMPACT_ICON_SIZE : STANDARD_ICON_SIZE;
        int progressHeight = compact ? COMPACT_PROGRESS_HEIGHT : STANDARD_PROGRESS_HEIGHT;
        int iconX = this.x + WidgetLayoutTokens.ROW_HORIZONTAL_PADDING;
        if (compact) {
            var font = Minecraft.getInstance().font;
            iconX += font.width(Component.literal(this.order.status().label()).withStyle(ChatFormatting.BOLD))
                + TEXT_GAP
                + font.width(Component.literal(this.order.side().label()).withStyle(ChatFormatting.BOLD))
                + TEXT_GAP;
        }
        this.item.inflate(Size.of(iconSize, iconSize));
        this.item.mount(
            this,
            iconX,
            this.y + Math.max(0, (this.height - progressHeight - iconSize) / 2)
        );
    }

    @Override public List<UIComponent> children() { return this.children; }

    @Override
    public ParentUIComponent removeChild(UIComponent child) {
        throw new UnsupportedOperationException("Tracked row owns its item icon");
    }

    @Override
    public boolean canFocus(FocusSource source) {
        return this.reorderable && source == FocusSource.MOUSE_CLICK;
    }

    @Override
    public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
        if (!this.reorderable || click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.onMouseDown(click, doubled);
        }
        this.drag.start(this.order.id(), this.index);
        return true;
    }

    @Override
    public boolean onMouseDrag(MouseButtonEvent click, double deltaX, double deltaY) {
        if (!this.reorderable || !this.drag.dragging(this.order.id())) return false;
        this.pointerMoved.accept(this.y + (int) click.y());
        return true;
    }

    @Override
    public boolean onMouseUp(MouseButtonEvent click) {
        if (!this.reorderable || !this.drag.dragging(this.order.id())) return false;
        var result = this.drag.finish();
        if (result == null) return false;
        this.actions.accept(new BazaarAction.ReorderTracked(result.id(), result.dropIndex()));
        return true;
    }

    @Override
    public boolean hasPersistentMouseCapture() {
        return this.reorderable && this.drag.dragging(this.order.id());
    }

    @Override
    public boolean shouldDrawTooltip(double mouseX, double mouseY) {
        return this.interactive
            && this.hover.isHovered(this.order.id())
            && super.shouldDrawTooltip(mouseX, mouseY);
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        super.draw(graphics, mouseX, mouseY, partialTicks, delta);
        boolean hovered = this.interactive && this.hover.isHovered(this.order.id());
        if (hovered) {
            graphics.fill(this.x, this.y, this.x + this.width, this.y + this.height, BazaarStyles.ROW_HOVER);
        }
        if (this.drag.dragging(this.order.id())) {
            graphics.fill(this.x, this.y, this.x + this.width, this.y + this.height, BazaarStyles.ROW_DRAG);
        } else if (this.order.status() == BazaarWidgetViewData.OrderStatus.Undercut) {
            graphics.fill(this.x, this.y, this.x + this.width, this.y + this.height, BazaarStyles.UNDERCUT_ROW);
        }

        if (this.item != null) this.drawChildren(graphics, mouseX, mouseY, partialTicks, delta, this.children);
        if (this.options.layout() == BazaarWidgetOptions.TrackedLayout.Compact) {
            this.drawCompact(graphics);
        } else {
            this.drawStandard(graphics);
        }
        this.drawProgress(graphics);
    }

    private void drawStandard(OwoUIGraphics graphics) {
        var font = Minecraft.getInstance().font;
        int x = this.x + WidgetLayoutTokens.ROW_HORIZONTAL_PADDING;
        if (this.item != null) x += STANDARD_ICON_SIZE + TEXT_GAP;
        int right = this.x + this.width - WidgetLayoutTokens.ROW_HORIZONTAL_PADDING;
        var side = Component.literal(this.order.side().label()).withStyle(ChatFormatting.BOLD);
        int sideX = right - font.width(side);
        graphics.text(font, ellipsize(this.productName, Math.max(0, sideX - TEXT_GAP - x)),
            x, this.y + 1, BazaarStyles.PRIMARY_TEXT, false);
        graphics.text(font, side, sideX, this.y + 1, this.order.side().accentColor(), false);

        var status = Component.literal(this.order.status().label()).withStyle(ChatFormatting.BOLD);
        int secondY = this.y + 1 + font.lineHeight + WidgetLayoutTokens.LINE_GAP;
        graphics.text(font, status, x, secondY, this.order.status().color(), false);
        int detailX = x + font.width(status);
        String details = BazaarOrderText.joined(this.optionalDetails(false));
        var prefix = details.isBlank() ? Component.empty() : Component.literal(" · ");
        int leftDetailWidth = font.width(prefix) + font.width(details);
        String marketText = this.firstFittingMarketText(
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

    private void drawCompact(OwoUIGraphics graphics) {
        var font = Minecraft.getInstance().font;
        int textY = this.y + Math.max(0, (this.height - COMPACT_PROGRESS_HEIGHT - font.lineHeight) / 2);
        int x = this.x + WidgetLayoutTokens.ROW_HORIZONTAL_PADDING;
        var status = Component.literal(this.order.status().label()).withStyle(ChatFormatting.BOLD);
        graphics.text(font, status, x, textY, this.order.status().color(), false);
        x += font.width(status) + TEXT_GAP;

        var side = Component.literal(this.order.side().label()).withStyle(ChatFormatting.BOLD);
        graphics.text(font, side, x, textY, this.order.side().accentColor(), false);
        x += font.width(side) + TEXT_GAP;
        if (this.item != null) x += COMPACT_ICON_SIZE + TEXT_GAP;

        int right = this.x + this.width - WidgetLayoutTokens.ROW_HORIZONTAL_PADDING;
        var details = new ArrayList<>(this.optionalDetails(false));
        int marketSeparatorWidth = details.isEmpty() ? 0 : font.width(" · ");
        String marketText = this.firstFittingMarketText(
            Math.max(0, right - x - MINIMUM_PRODUCT_WIDTH - TEXT_GAP
                - font.width(BazaarOrderText.joined(details)) - marketSeparatorWidth)
        );
        if (!marketText.isBlank()) details.add(marketText);
        String detailText = BazaarOrderText.joined(details);
        int detailWidth = font.width(detailText);
        while (!details.isEmpty() && detailWidth > Math.max(0, right - x - MINIMUM_PRODUCT_WIDTH - TEXT_GAP)) {
            details.removeLast();
            detailText = BazaarOrderText.joined(details);
            detailWidth = font.width(detailText);
        }
        int detailX = detailText.isBlank() ? right : right - detailWidth;
        int nameRight = detailText.isBlank() ? right : detailX - TEXT_GAP;
        graphics.text(font, ellipsize(this.productName, Math.max(0, nameRight - x)),
            x, textY, BazaarStyles.PRIMARY_TEXT, false);
        if (!detailText.isBlank()) {
            graphics.text(font, detailText, detailX, textY, BazaarStyles.SECONDARY_TEXT, false);
        }
    }

    private List<String> optionalDetails(boolean includeMarketInfo) {
        return BazaarOrderText.optionalDetails(
            this.order,
            this.options.showVolume(),
            this.options.priceDisplay(),
            includeMarketInfo && this.options.showMarketInfo()
        );
    }

    private String firstFittingMarketText(int availableWidth) {
        if (!this.options.showMarketInfo()) return "";
        var font = Minecraft.getInstance().font;
        for (var candidate : BazaarOrderText.hudMarketCandidates(
            this.order,
            BazaarWidgetOptions.QueueDisplay.Items,
            BazaarWidgetOptions.UndercutDetail.PriceGapAndQueue
        )) {
            if (font.width(candidate) <= availableWidth) return candidate;
        }
        return "";
    }

    private void drawProgress(OwoUIGraphics graphics) {
        if (!this.options.showProgress() || this.order.liveProgress().isEmpty()) return;
        var progress = this.order.liveProgress().orElseThrow();
        int progressHeight = this.options.layout() == BazaarWidgetOptions.TrackedLayout.Compact
            ? COMPACT_PROGRESS_HEIGHT : STANDARD_PROGRESS_HEIGHT;
        int left = this.x + WidgetLayoutTokens.ROW_HORIZONTAL_PADDING;
        int right = this.x + this.width - WidgetLayoutTokens.ROW_HORIZONTAL_PADDING;
        int top = this.y + this.height - progressHeight;
        graphics.fill(left, top, right, top + progressHeight, BazaarStyles.PROGRESS_TRACK);
        graphics.fill(left, top, left + progressFillWidth(right - left, progress.fraction()),
            top + progressHeight, BazaarStyles.PROGRESS_FILL);
    }

    static int progressHeight(BazaarWidgetOptions.TrackedLayout layout) {
        return layout == BazaarWidgetOptions.TrackedLayout.Compact
            ? COMPACT_PROGRESS_HEIGHT : STANDARD_PROGRESS_HEIGHT;
    }

    static int progressFillWidth(int availableWidth, double fraction) {
        return (int) Math.round(Math.max(0, availableWidth) * Math.max(0, Math.min(1, fraction)));
    }

    com.github.lutzluca.btrbz.data.OrderModels.TrackedOrderId orderId() { return this.order.id(); }
}
