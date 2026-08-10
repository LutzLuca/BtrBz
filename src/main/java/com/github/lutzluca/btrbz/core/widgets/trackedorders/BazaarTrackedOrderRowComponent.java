package com.github.lutzluca.btrbz.core.widgets.trackedorders;

import com.github.lutzluca.btrbz.core.widgets.WidgetMath;
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
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi.ellipsize;

/** Full-name tracked-order row with an optional, layout-stable live fill bar. */
final class BazaarTrackedOrderRowComponent extends BaseParentUIComponent {
    static final int STANDARD_HEIGHT = 23;
    static final int COMPACT_HEIGHT = 16;
    private static final int STANDARD_ICON_SIZE = 16;
    private static final int COMPACT_ICON_SIZE = 12;
    private static final int TEXT_GAP = WidgetLayoutTokens.ORDER_TEXT_GAP;
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

    private @Nullable BazaarWidgetViewData.Order lastOrder;
    private @Nullable TrackedOrdersWidgetConfig.Snapshot lastOptions;
    private int lastIndex = -1;
    private boolean lastInteractive;
    private boolean initialized;

    private @Nullable DrawLayout drawLayout;
    private int drawLayoutWidth = -1;

    private record DrawLayout(
        Component side, int sideX,
        Component status, int statusX,
        FormattedCharSequence productText,
        @Nullable FormattedCharSequence identityText,
        @Nullable Component marketText, int marketX
    ) {}

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
        this.actions = actions;
        this.order = order;
        this.options = options;

        var optionsSnapshot = TrackedOrdersWidgetConfig.Snapshot.of(options);

        if (this.initialized
            && order == this.lastOrder
            && optionsSnapshot.equals(this.lastOptions)
            && index == this.lastIndex
            && interactive == this.lastInteractive) {
            return;
        }

        this.drawLayout = null;

        boolean layoutChanged = this.lastOptions == null || this.lastOptions.layout() != optionsSnapshot.layout();

        this.productName = order.formattedItemName(false);
        this.index = index;
        this.reorderable = interactive && options.sort == TrackedOrdersWidgetConfig.TrackedSort.Manual;
        this.interactive = interactive;

        int iconSize = options.layout == TrackedOrdersWidgetConfig.TrackedLayout.Compact
            ? COMPACT_ICON_SIZE : STANDARD_ICON_SIZE;

        this.item = BazaarUi.reconcileItem(this.item, order.itemStack(), iconSize);

        if (layoutChanged && this.item != null) {
            this.item.sizing(Sizing.fixed(iconSize), Sizing.fixed(iconSize));
        }

        if (layoutChanged) {
            this.verticalSizing(Sizing.fixed(options.layout == TrackedOrdersWidgetConfig.TrackedLayout.Compact ? COMPACT_HEIGHT : STANDARD_HEIGHT));
        }

        this.tooltip(interactive ? WidgetTooltips.wrapped(tooltip) : List.of());
        this.updateLayout();

        this.initialized = true;
        this.lastOrder = order;
        this.lastOptions = optionsSnapshot;
        this.lastIndex = index;
        this.lastInteractive = interactive;
    }

    @Override
    public void layout(Size space) {
        if (this.item == null) {
            return;
        }

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

    @Override
    public List<UIComponent> children() {
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
        if (!this.reorderable || !this.list.trackingDrag(this.order.id())) {
            return false;
        }

        this.list.dragPointer(this.y + (int) click.y());

        return true;
    }

    @Override
    public boolean onMouseUp(MouseButtonEvent click) {
        if (!this.reorderable || !this.list.trackingDrag(this.order.id())) {
            return false;
        }

        var result = this.list.finishDrag().orElse(null);

        if (result == null) {
            return false;
        }

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
        if (this.drawLayout == null || this.drawLayoutWidth != this.width) {
            this.drawLayout = this.computeStandardLayout();
            this.drawLayoutWidth = this.width;
        }

        var layout = this.drawLayout;
        var font = Minecraft.getInstance().font;

        int x = this.x + WidgetLayoutTokens.ROW_HORIZONTAL_PADDING;

        if (this.item != null) {
            x += STANDARD_ICON_SIZE + TEXT_GAP;
        }

        graphics.text(font, layout.productText(), x, this.y + 1, BazaarStyles.PRIMARY_TEXT, false);
        graphics.text(font, layout.status(), this.x + layout.statusX(), this.y + 1,
            this.order.status().color(), false);
        graphics.text(font, layout.side(), this.x + layout.sideX(), this.y + 1,
            this.order.side().accentColor(), false);

        int secondY = this.y + 1 + font.lineHeight + WidgetLayoutTokens.LINE_GAP;

        if (layout.identityText() != null) {
            graphics.text(font, layout.identityText(), x, secondY, BazaarStyles.SECONDARY_TEXT, false);
        }

        if (layout.marketText() != null) {
            graphics.text(font, layout.marketText(), this.x + layout.marketX(), secondY,
                BazaarStyles.SECONDARY_TEXT, false);
        }
    }

    private DrawLayout computeStandardLayout() {
        var font = Minecraft.getInstance().font;
        int x = WidgetLayoutTokens.ROW_HORIZONTAL_PADDING;

        if (this.item != null) {
            x += STANDARD_ICON_SIZE + TEXT_GAP;
        }

        int right = this.width - WidgetLayoutTokens.ROW_HORIZONTAL_PADDING;

        var side = Component.literal(this.order.side().label()).withStyle(ChatFormatting.BOLD);
        int sideX = right - font.width(side);
        var status = Component.literal(this.order.status().label()).withStyle(ChatFormatting.BOLD);
        int statusX = sideX - TEXT_GAP - font.width(status);

        var productText = ellipsize(this.productName, Math.max(0, statusX - TEXT_GAP - x));

        String identity = BazaarOrderText.orderIdentity(this.order);
        String market = BazaarUi.firstFittingText(
            BazaarOrderText.marketPositionCandidates(this.order, true, true),
            Math.max(0, right - x - font.width(identity) - TEXT_GAP)
        );
        int marketX = market.isBlank() ? right : right - font.width(market);

        var identityText = ellipsize(Component.literal(identity), Math.max(0, marketX - TEXT_GAP - x));

        return new DrawLayout(
            side, sideX, status, statusX, productText,
            identityText,
            market.isBlank() ? null : Component.literal(market),
            marketX
        );
    }

    private void drawCompact(OwoUIGraphics graphics) {
        if (this.drawLayout == null || this.drawLayoutWidth != this.width) {
            this.drawLayout = this.computeCompactLayout();
            this.drawLayoutWidth = this.width;
        }

        var layout = this.drawLayout;
        var font = Minecraft.getInstance().font;

        int textY = this.y + Math.max(0, (this.height - COMPACT_PROGRESS_HEIGHT - font.lineHeight) / 2);
        int x = this.x + WidgetLayoutTokens.ROW_HORIZONTAL_PADDING;

        if (this.item != null) {
            x += COMPACT_ICON_SIZE + TEXT_GAP;
        }

        graphics.text(font, layout.productText(), x, textY, BazaarStyles.PRIMARY_TEXT, false);
        graphics.text(font, layout.status(), this.x + layout.statusX(), textY,
            this.order.status().color(), false);
        graphics.text(font, layout.side(), this.x + layout.sideX(), textY,
            this.order.side().accentColor(), false);
    }

    private DrawLayout computeCompactLayout() {
        var font = Minecraft.getInstance().font;
        int x = WidgetLayoutTokens.ROW_HORIZONTAL_PADDING;

        if (this.item != null) {
            x += COMPACT_ICON_SIZE + TEXT_GAP;
        }

        int right = this.width - WidgetLayoutTokens.ROW_HORIZONTAL_PADDING;

        var side = Component.literal(this.order.side().label()).withStyle(ChatFormatting.BOLD);
        int sideX = right - font.width(side);
        var status = Component.literal(this.order.status().label()).withStyle(ChatFormatting.BOLD);
        int statusX = sideX - TEXT_GAP - font.width(status);

        var productText = ellipsize(this.productName, Math.max(0, statusX - TEXT_GAP - x));

        return new DrawLayout(side, sideX, status, statusX, productText, null, null, 0);
    }

    private void drawProgress(OwoUIGraphics graphics) {
        if (this.order.liveProgress().isEmpty()) {
            return;
        }

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
        return WidgetMath.portion(availableWidth, fraction);
    }

    com.github.lutzluca.btrbz.data.OrderModels.TrackedOrderId orderId() {
        return this.order.id();
    }

    private ItemComponent itemComponent() {
        return Objects.requireNonNull(this.item, "item");
    }
}
