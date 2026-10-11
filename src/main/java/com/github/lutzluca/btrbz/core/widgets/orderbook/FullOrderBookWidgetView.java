package com.github.lutzluca.btrbz.core.widgets.orderbook;

import com.github.lutzluca.btrbz.core.ui.UiButton;
import com.github.lutzluca.btrbz.core.ui.UiComponents;
import com.github.lutzluca.btrbz.core.ui.UiText;
import com.github.lutzluca.btrbz.core.ui.UiStyles;
import com.github.lutzluca.btrbz.core.widgets.WidgetView;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import com.github.lutzluca.btrbz.core.widgets.ui.RetainedFlowLayout;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetLayoutTokens;
import io.wispforest.owo.ui.component.ItemComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;
import java.util.function.Consumer;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import io.wispforest.owo.ui.core.Size;
import org.jetbrains.annotations.Nullable;

final class FullOrderBookWidgetView
    implements WidgetView<OrderBookWidgetData.Snapshot, OrderBookWidgetConfig, OrderBookAction> {
    private static final int SCREEN_MARGIN = 12;
    private static final int HEADER_BOTTOM_PADDING = 6;
    private static final int FOOTER_TOP_PADDING = 6;
    private static final int BACK_BUTTON_WIDTH = 60;
    private static final int BACK_BUTTON_HEIGHT = 20;
    private final RetainedFlowLayout root = RetainedFlowLayout.vertical(Sizing.fixed(1), Sizing.content());
    private final RetainedFlowLayout header = RetainedFlowLayout.horizontal(Sizing.fill(100), Sizing.content());
    private @Nullable ItemComponent item;
    private final LabelComponent itemName = UiComponents.label("", UiStyles.palette().primary());
    private final LabelComponent bookTitle = UiComponents.label("Order book", UiStyles.palette().muted());
    private final RetainedFlowLayout spreadLine = RetainedFlowLayout.horizontal(Sizing.fill(100), Sizing.content());
    private final LabelComponent spread = UiComponents.label("", UiStyles.palette().label());
    private final OrderBookDepthTable table = new OrderBookDepthTable();
    private final RetainedFlowLayout footer = RetainedFlowLayout.horizontal(Sizing.fill(100), Sizing.content());
    private final LabelComponent instruction = UiComponents.label(
        "Click a price to copy it and return", UiStyles.palette().muted());
    private final UiButton goBack = UiButton.text(
        Component.literal("Go back"), _ -> this.actions.accept(new OrderBookAction.GoBack()));
    private Consumer<OrderBookAction> actions = _ -> {};
    private OrderBookDepth depth;
    private OrderBookWidgetConfig config;
    private boolean hasItem;

    FullOrderBookWidgetView() {
        this.root.allowOverflow(true);
        this.root.gap(0);
        this.root.padding(Insets.vertical(2));
        this.header.allowOverflow(true);
        this.header.verticalAlignment(VerticalAlignment.CENTER);
        this.header.gap(WidgetLayoutTokens.HEADER_GAP);
        this.header.padding(Insets.of(0, HEADER_BOTTOM_PADDING, OrderBookDepthTable.INSET, OrderBookDepthTable.INSET));
        this.header.surface((graphics, component) -> graphics.fill(
            component.x() + OrderBookDepthTable.INSET, component.y() + component.height() - 1,
            component.x() + component.width() - OrderBookDepthTable.INSET,
            component.y() + component.height(), OrderBookDepthTable.dividerColor()));
        this.itemName.horizontalSizing(Sizing.expand(100));

        this.spreadLine.horizontalAlignment(HorizontalAlignment.CENTER);
        this.spreadLine.padding(Insets.vertical(4));
        this.spreadLine.child(this.spread);

        this.footer.allowOverflow(true);
        this.footer.padding(Insets.of(FOOTER_TOP_PADDING, 0, OrderBookDepthTable.INSET, OrderBookDepthTable.INSET));
        this.footer.surface((graphics, component) -> graphics.fill(
            component.x() + OrderBookDepthTable.INSET, component.y(),
            component.x() + component.width() - OrderBookDepthTable.INSET,
            component.y() + 1, OrderBookDepthTable.dividerColor()));
        this.footer.gap(WidgetLayoutTokens.HEADER_GAP);
        this.footer.verticalAlignment(VerticalAlignment.CENTER);
        this.instruction.horizontalSizing(Sizing.expand(100));
        this.goBack.sizing(Sizing.fixed(BACK_BUTTON_WIDTH), Sizing.fixed(BACK_BUTTON_HEIGHT));
        this.footer.child(this.instruction);
        this.footer.child(this.goBack);
        this.root.child(this.header);
        this.root.child(this.spreadLine);
        this.root.child(this.table);
        this.root.child(this.footer);
    }

    @Override
    public UIComponent root() {
        return this.root;
    }

    @Override
    public void update(
        OrderBookWidgetData.Snapshot data,
        OrderBookWidgetConfig config,
        WidgetSession session,
        Consumer<OrderBookAction> actions
    ) {
        this.actions = actions;
        this.depth = OrderBookDepth.from(data, config.depthMode);
        this.config = config;
        this.spread.text(Component.literal("Spread: ").withStyle(UiStyles.label()).append(
            Component.literal(this.depth.spreadText()).withStyle(
                this.depth.spread().isPresent() ? UiStyles.money() : UiStyles.muted())));
        this.itemName.text(data.formattedItemName());
        this.header.clearChildren();
        var itemStack = data.itemStack();
        this.hasItem = itemStack.isPresent();
        if (itemStack.isPresent()) {
            var stack = itemStack.orElseThrow();
            if (this.item == null) {
                this.item = UiComponents.icon(stack);
            } else {
                this.item.stack(stack);
            }
            this.header.child(this.item);
        }
        this.header.child(this.itemName);
        this.header.child(this.bookTitle);
    }

    @Override
    public void updateLayout(Size availableSpace) {
        var layout = this.table.measure(this.depth, this.config,
            Math.max(1, availableSpace.width() - SCREEN_MARGIN * 2));
        int width = layout.contentWidth();
        this.root.horizontalSizing(Sizing.fixed(width));
        var font = Minecraft.getInstance().font;
        int nameWidth = Math.max(1, width - OrderBookDepthTable.INSET * 2
            - font.width(this.bookTitle.text()) - WidgetLayoutTokens.HEADER_GAP
            - (this.hasItem ? 16 + WidgetLayoutTokens.HEADER_GAP : 0));
        this.itemName.maxWidth(nameWidth);
        int nameLines = font.split(this.itemName.text(), nameWidth).size();
        int nameHeight = nameLines * (this.itemName.lineHeight() + this.itemName.lineSpacing())
            - this.itemName.lineSpacing();
        int headerHeight = Math.max(this.hasItem ? 16 : 0, nameHeight) + HEADER_BOTTOM_PADDING;
        int availableHeight = availableSpace.height() - SCREEN_MARGIN * 2
            - headerHeight - (font.lineHeight + this.spreadLine.padding().get().vertical())
            - BACK_BUTTON_HEIGHT - FOOTER_TOP_PADDING - this.root.padding().get().vertical();
        this.table.update(this.depth, this.config, this.actions, layout, availableHeight);
        this.instruction.text(UiText.firstFittingText(List.of(
            Component.literal("Click a price to copy it and return"),
            Component.literal("Click a price to copy")),
            width - BACK_BUTTON_WIDTH - WidgetLayoutTokens.HEADER_GAP - OrderBookDepthTable.INSET * 2));
    }
}
