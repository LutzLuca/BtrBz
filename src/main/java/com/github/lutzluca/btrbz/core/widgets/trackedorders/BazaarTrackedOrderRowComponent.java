package com.github.lutzluca.btrbz.core.widgets.trackedorders;

import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarOrderText;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarStyles;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetLayoutTokens;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetTooltips;
import com.mojang.blaze3d.platform.InputConstants;
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
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Consumer;

import static com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi.ellipsize;

/** Full-name tracked-order row with an optional, layout-stable live fill bar. */
final class BazaarTrackedOrderRowComponent extends BaseParentUIComponent {
    static final int STANDARD_HEIGHT = 23;
    static final int COMPACT_HEIGHT = 16;
    private static final int STANDARD_ICON_SIZE = 16;
    private static final int COMPACT_ICON_SIZE = 12;
    private static final int TEXT_GAP = 3;
    private static final int MINIMUM_PRODUCT_WIDTH = 36;
    private static final int STANDARD_PROGRESS_HEIGHT = 2;
    private static final int COMPACT_PROGRESS_HEIGHT = 1;

    private final BazaarTrackedOrderListComponent list;
    private @Nullable ItemComponent item;
    private BazaarWidgetViewData.Order order;
    private TrackedOrdersWidgetConfig options;
    private Component productName;
    private int index;
    private boolean reorderable;
    private boolean interactive;
    private Consumer<TrackedOrdersAction> actions;

    BazaarTrackedOrderRowComponent(
        BazaarTrackedOrderListComponent list,
        BazaarWidgetViewData.Order order,
        TrackedOrdersWidgetConfig options,
        List<Component> tooltip,
        int index,
        boolean interactive,
        Consumer<TrackedOrdersAction> actions
    ) {
        super(Sizing.fill(100), Sizing.fixed(
            options.layout == TrackedOrdersWidgetConfig.TrackedLayout.Compact ? COMPACT_HEIGHT : STANDARD_HEIGHT
        ));
        this.list = list;
        this.allowOverflow(true);
        this.update(order, options, tooltip, index, interactive, actions);
    }

    void update(
        BazaarWidgetViewData.Order order,
        TrackedOrdersWidgetConfig options,
        List<Component> tooltip,
        int index,
        boolean interactive,
        Consumer<TrackedOrdersAction> actions
    ) {
        this.order = order;
        this.options = options;
        this.productName = order.formattedItemName(false);
        this.index = index;
        this.reorderable = interactive && options.sort == TrackedOrdersWidgetConfig.TrackedSort.Manual;
        this.interactive = interactive;
        var itemStack = order.itemStack();
        this.actions = actions;
        int iconSize = options.layout == TrackedOrdersWidgetConfig.TrackedLayout.Compact
            ? COMPACT_ICON_SIZE : STANDARD_ICON_SIZE;
        if (itemStack.isPresent()) {
            if (this.item == null) {
                this.item = BazaarUi.item(itemStack.orElseThrow(), iconSize);
            } else {
                this.item.stack(itemStack.orElseThrow());
                this.item.sizing(Sizing.fixed(iconSize), Sizing.fixed(iconSize));
            }
        }
        this.verticalSizing(Sizing.fixed(
            options.layout == TrackedOrdersWidgetConfig.TrackedLayout.Compact ? COMPACT_HEIGHT : STANDARD_HEIGHT
        ));
        this.tooltip(interactive ? WidgetTooltips.wrapped(this.tooltipLines(tooltip)) : List.of());
        this.updateLayout();
    }

    private List<Component> tooltipLines(List<Component> providerLines) {
        var lines = new ArrayList<Component>();
        lines.add(this.productName.copy());
        lines.add(Component.literal(this.order.status().label() + " · " + this.order.side().label()));
        lines.add(Component.literal(BazaarOrderText.orderIdentity(this.order)));
        var market = BazaarOrderText.hudMarketPositionCandidates(this.order, true, true);
        if (!market.isEmpty()) lines.add(Component.literal(market.getFirst()));
        this.order.liveProgress().ifPresent(progress -> lines.add(Component.literal(
            "Filled " + progress.filled() + "/" + progress.total()
        )));
        lines.addAll(providerLines);
        return List.copyOf(lines);
    }

    @Override
    public void layout(Size space) {
        if (this.item == null) return;
        boolean compact = this.options.layout == TrackedOrdersWidgetConfig.TrackedLayout.Compact;
        int iconSize = compact ? COMPACT_ICON_SIZE : STANDARD_ICON_SIZE;
        int progressHeight = compact ? COMPACT_PROGRESS_HEIGHT : STANDARD_PROGRESS_HEIGHT;
        int iconX = this.x + WidgetLayoutTokens.ROW_HORIZONTAL_PADDING;
        this.itemComponent().inflate(Size.of(iconSize, iconSize));
        this.itemComponent().mount(
            this,
            iconX,
            this.y + Math.max(0, (this.height - progressHeight - iconSize) / 2)
        );
    }

    @Override public List<UIComponent> children() {
        return this.item == null ? List.of() : List.of(this.itemComponent());
    }

    @Override
    public ParentUIComponent removeChild(UIComponent child) {
        throw new UnsupportedOperationException("Tracked row owns its item component");
    }

    @Override
    public boolean canFocus(FocusSource source) {
        return this.reorderable && source == FocusSource.MOUSE_CLICK;
    }

    @Override
    public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
        if (!this.reorderable || click.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.onMouseDown(click, doubled);
        }
        return this.list.beginDrag(this.order.id(), this.index);
    }

    @Override
    public boolean onMouseDrag(MouseButtonEvent click, double deltaX, double deltaY) {
        if (!this.reorderable || !this.list.trackingDrag(this.order.id())) return false;
        this.list.dragPointer(this.y + (int) click.y());
        return true;
    }

    @Override
    public boolean onMouseUp(MouseButtonEvent click) {
        if (!this.reorderable || !this.list.trackingDrag(this.order.id())) return false;
        var result = this.list.finishDrag().orElse(null);
        if (result == null) return false;
        if (result.moved()) {
            this.actions.accept(new TrackedOrdersAction.Reorder(result.key(), result.dropIndex()));
        }
        return true;
    }

    @Override
    public boolean shouldDrawTooltip(double mouseX, double mouseY) {
        return this.interactive
            && this.list.isHovered(this.order.id())
            && super.shouldDrawTooltip(mouseX, mouseY);
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        super.draw(graphics, mouseX, mouseY, partialTicks, delta);
        boolean hovered = this.interactive && this.list.isHovered(this.order.id());
        if (hovered) {
            graphics.fill(this.x, this.y, this.x + this.width, this.y + this.height, BazaarStyles.ROW_HOVER);
        }
        if (this.list.dragging(this.order.id())) {
            graphics.fill(this.x, this.y, this.x + this.width, this.y + this.height, BazaarStyles.ROW_DRAG);
        } else if (this.order.status() == BazaarWidgetViewData.OrderStatus.Undercut) {
            graphics.fill(this.x, this.y, this.x + this.width, this.y + this.height, BazaarStyles.UNDERCUT_ROW);
        }

        if (this.item != null) {
            this.drawChildren(graphics, mouseX, mouseY, partialTicks, delta, List.of(this.itemComponent()));
        }
        if (this.options.layout == TrackedOrdersWidgetConfig.TrackedLayout.Compact) {
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
        var status = Component.literal(this.order.status().label()).withStyle(ChatFormatting.BOLD);
        int statusX = sideX - TEXT_GAP - font.width(status);
        graphics.text(font, ellipsize(this.productName, Math.max(0, statusX - TEXT_GAP - x)),
            x, this.y + 1, BazaarStyles.PRIMARY_TEXT, false);
        graphics.text(font, status, statusX, this.y + 1, this.order.status().color(), false);
        graphics.text(font, side, sideX, this.y + 1, this.order.side().accentColor(), false);

        int secondY = this.y + 1 + font.lineHeight + WidgetLayoutTokens.LINE_GAP;
        String identity = BazaarOrderText.orderIdentity(this.order);
        String marketText = this.firstFittingMarketText(Math.max(0,
            right - x - font.width(identity) - TEXT_GAP));
        int marketX = marketText.isBlank() ? right : right - font.width(marketText);
        graphics.text(font, ellipsize(Component.literal(identity), Math.max(0, marketX - TEXT_GAP - x)),
            x, secondY, BazaarStyles.SECONDARY_TEXT, false);
        if (!marketText.isBlank()) {
            graphics.text(font, Component.literal(marketText), marketX, secondY, BazaarStyles.SECONDARY_TEXT, false);
        }
    }

    private void drawCompact(OwoUIGraphics graphics) {
        var font = Minecraft.getInstance().font;
        int textY = this.y + Math.max(0, (this.height - COMPACT_PROGRESS_HEIGHT - font.lineHeight) / 2);
        int x = this.x + WidgetLayoutTokens.ROW_HORIZONTAL_PADDING;
        if (this.item != null) x += COMPACT_ICON_SIZE + TEXT_GAP;
        int right = this.x + this.width - WidgetLayoutTokens.ROW_HORIZONTAL_PADDING;
        var side = Component.literal(this.order.side().label()).withStyle(ChatFormatting.BOLD);
        int sideX = right - font.width(side);
        var status = Component.literal(this.order.status().label()).withStyle(ChatFormatting.BOLD);
        int statusX = sideX - TEXT_GAP - font.width(status);
        graphics.text(font, ellipsize(this.productName, Math.max(0, statusX - TEXT_GAP - x)),
            x, textY, BazaarStyles.PRIMARY_TEXT, false);
        graphics.text(font, status, statusX, textY, this.order.status().color(), false);
        graphics.text(font, side, sideX, textY, this.order.side().accentColor(), false);
    }

    private String firstFittingMarketText(int availableWidth) {
        var font = Minecraft.getInstance().font;
        for (var candidate : BazaarOrderText.hudMarketPositionCandidates(this.order, true, true)) {
            if (font.width(candidate) <= availableWidth) return candidate;
        }
        return "";
    }

    private void drawProgress(OwoUIGraphics graphics) {
        if (this.order.liveProgress().isEmpty()) return;
        var progress = this.order.liveProgress().orElseThrow();
        int progressHeight = this.options.layout == TrackedOrdersWidgetConfig.TrackedLayout.Compact
            ? COMPACT_PROGRESS_HEIGHT : STANDARD_PROGRESS_HEIGHT;
        int left = this.x + WidgetLayoutTokens.ROW_HORIZONTAL_PADDING;
        int right = this.x + this.width - WidgetLayoutTokens.ROW_HORIZONTAL_PADDING;
        int top = this.y + this.height - progressHeight;
        graphics.fill(left, top, right, top + progressHeight, BazaarStyles.PROGRESS_TRACK);
        graphics.fill(left, top, left + progressFillWidth(right - left, progress.fraction()),
            top + progressHeight, BazaarStyles.PROGRESS_FILL);
    }

    static int progressHeight(TrackedOrdersWidgetConfig.TrackedLayout layout) {
        return layout == TrackedOrdersWidgetConfig.TrackedLayout.Compact
            ? COMPACT_PROGRESS_HEIGHT : STANDARD_PROGRESS_HEIGHT;
    }

    static int progressFillWidth(int availableWidth, double fraction) {
        return (int) Math.round(Math.max(0, availableWidth) * Math.max(0, Math.min(1, fraction)));
    }

    com.github.lutzluca.btrbz.data.OrderModels.TrackedOrderId orderId() { return this.order.id(); }

    private ItemComponent itemComponent() {
        return Objects.requireNonNull(this.item, "item");
    }
}
