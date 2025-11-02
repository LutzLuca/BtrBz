package com.github.lutzluca.btrbz.core.modules;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.OrderHighlightManager;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.core.modules.TrackedOrdersListModule.OrderListConfig;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrder;
import com.github.lutzluca.btrbz.utils.Position;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import com.github.lutzluca.btrbz.utils.Util;
import com.github.lutzluca.btrbz.widgets.DraggableWidget;
import com.github.lutzluca.btrbz.widgets.ScrollableListWidget;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionEventListener.Event;
import dev.isxander.yacl3.api.OptionGroup;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.Getter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

public class TrackedOrdersListModule extends Module<OrderListConfig> {

    private ScrollableListWidget<OrderEntryWidget> list;
    private Integer currentHoverSlot = null;

    @Override
    public void onLoad() {
        var orderManager = BtrBz.orderManager();

        orderManager.addOnOrderAddedListener(this::onOrderAdded);
        orderManager.addOnOrderRemovedListener(this::onOrderRemoved);
    }

    private void onOrderAdded(TrackedOrder order) {
        if (this.list == null) {
            return;
        }

        var entry = this.createEntryWidget(order);
        this.list.addChild(entry);
    }

    private void onOrderRemoved(TrackedOrder order) {
        if (this.list == null) {
            return;
        }

        var children = this.list.getChildren();
        for (int i = 0; i < children.size(); i++) {
            var widget = children.get(i);
            if (widget.getOrder() == order) {
                this.list.removeChild(i);
                break;
            }
        }
    }

    private void onWidgetHoverEnter(int slotIdx) {
        if (slotIdx == -1) {
            return;
        }

        this.currentHoverSlot = slotIdx;
        BtrBz.highlightManager().setHighlightOverride(slotIdx, 0xAAFFFFFF);
    }

    private void onWidgetHoverExit(int slotIdx) {
        if (this.currentHoverSlot != null && this.currentHoverSlot == slotIdx) {
            this.currentHoverSlot = null;
            BtrBz.highlightManager().clearHighlightOverride();
        }
    }

    public void initializeList() {
        if (this.list == null) {
            return;
        }

        this.list.clearChildren();

        var orderManager = BtrBz.orderManager();
        for (var order : orderManager.getTrackedOrders()) {
            var entry = this.createEntryWidget(order);
            this.list.addChild(entry);
        }
    }

    public void clearList() {
        if (this.list == null) {
            return;
        }

        this.list.clearChildren();
    }

    private OrderEntryWidget createEntryWidget(TrackedOrder order) {
        return new OrderEntryWidget(
            0,
            0,
            200,
            14,
            order,
            this.list.getParentScreen(),
            widget -> this.onWidgetHoverEnter(widget.getSlotIdx()),
            widget -> this.onWidgetHoverExit(widget.getSlotIdx())
        );
    }

    @Override
    public boolean shouldDisplay(ScreenInfo info) {
        return this.configState.enabled && (info.inMenu(BazaarMenuType.Orders) || this.configState.showInBazaar && info.inBazaar());
    }

    @Override
    public List<ClickableWidget> createWidgets(ScreenInfo info) {
        if (this.list != null) {
            return List.of(this.list);
        }

        var position = this.getWidgetPosition(info);
        if (position.isEmpty()) {
            return List.of();
        }

        this.list = new ScrollableListWidget<OrderEntryWidget>(
            position.get().x(),
            position.get().y(),
            200,
            250,
            Text.literal("Tracked Orders"),
            info.getScreen()
        )
            .setMaxVisibleChildren(8)
            .setChildHeight(14)
            .setChildSpacing(1)
            .setTitleBarHeight(18)
            .setTopMargin(2)
            .setBottomPadding(2);

        this.initializeList();

        return List.of(this.list);
    }

    private Optional<Position> getWidgetPosition(ScreenInfo info) {
        return this.getConfigPosition().or(() -> info.getHandledScreenBounds().map(bounds -> {
            var x = bounds.x() + bounds.width();
            var y = bounds.y();
            var padding = 20;

            return new Position(x + padding, y);
        }));
    }

    private Optional<Position> getConfigPosition() {
        return Util.zipNullables(this.configState.x, this.configState.y).map(Position::from);
    }

    public static class OrderListConfig {

        public Integer x, y;

        public boolean enabled = true;
        public boolean showInBazaar = false;

        public Option.Builder<Boolean> createInBazaarOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Text.literal("In Bazaar"))
                .description(OptionDescription.of(Text.literal(
                    "Whether to display the tracked orders list in the Bazaar and not only in the orders screen")))
                .binding(false, () -> this.showInBazaar, enabled -> this.showInBazaar = enabled)
                .controller(ConfigScreen::createBooleanController);
        }

        public Option.Builder<Boolean> createEnabledOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Text.literal("Order List Module"))
                .description(OptionDescription.of(Text.literal(
                    "Compact list of tracked orders. Hover an entry to highlight its slot.")))
                .binding(true, () -> this.enabled, enabled -> this.enabled = enabled)
                .controller(ConfigScreen::createBooleanController);
        }

        public OptionGroup getGroup() {
            var inBazaarOption = this.createInBazaarOption().build();
            var enabled = this.createEnabledOption();

            enabled.addListener((option, event) -> {
                if (event == Event.STATE_CHANGE) {
                    inBazaarOption.setAvailable(option.pendingValue());
                }
            });

            return OptionGroup
                .createBuilder()
                .name(Text.literal("Order List"))
                .description(OptionDescription.of(Text.literal(
                    "Shows your tracked bazaar orders in a compact, hover-highlightable list.")))
                .option(enabled.build())
                .option(inBazaarOption)
                .collapsed(false)
                .build();
        }
    }

    private static class OrderEntryWidget extends DraggableWidget {

        @Getter
        private final TrackedOrder order;
        private final Consumer<OrderEntryWidget> onHoverEnter;
        private final Consumer<OrderEntryWidget> onHoverExit;
        private boolean wasHovered;

        public OrderEntryWidget(
            int x,
            int y,
            int width,
            int height,
            TrackedOrder order,
            Screen parentScreen,
            Consumer<OrderEntryWidget> onHoverEnter,
            Consumer<OrderEntryWidget> onHoverExit
        ) {
            super(x, y, width, height, Text.literal(order.productName), parentScreen);
            this.order = order;
            this.onHoverEnter = onHoverEnter;
            this.onHoverExit = onHoverExit;
            this.setRenderBorder(false);
            this.setRenderBackground(false);
        }

        @Override
        protected void renderContent(DrawContext context, int mouseX, int mouseY, float delta) {
            boolean hovered = this.isHovered();
            if (hovered && !this.wasHovered) {
                this.onHoverEnter.accept(this);
            } else if (!hovered && this.wasHovered) {
                this.onHoverExit.accept(this);
            }
            this.wasHovered = hovered;

            var textRenderer = MinecraftClient.getInstance().textRenderer;

            int x = this.getX();
            int y = this.getY();
            int w = this.getWidth();
            int h = this.getHeight();

            String typeText = order.type == OrderType.Sell ? "Sell" : "Buy";

            String volumeText = order.volume + "x";
            String nameText = order.productName;
            OrderType type = order.type;
            OrderStatus status = order.status;

            if (hovered) {
                context.fill(x, y, x + w, y + h, 0x30FFFFFF);
            }

            int statusColor = OrderHighlightManager.colorForStatus(status);
            int dotSize = 4;
            int dotX = x + 3;
            int dotY = y + (h - dotSize) / 2;
            context.fill(dotX, dotY, dotX + dotSize, dotY + dotSize, statusColor);

            int typeColor = type == OrderType.Sell ? 0xFF5DADE2 : 0xFFFF8C69;
            int volumeColor = 0xFFFFA500;
            int nameColor = 0xFFE0E0E0;
            int separatorColor = 0xFF808080;

            int textX = dotX + dotSize + 5;
            int textY = y + (h - 8) / 2;

            context.drawText(textRenderer, typeText, textX, textY, typeColor, false);
            textX += textRenderer.getWidth(typeText) + 2;

            context.drawText(textRenderer, "-", textX, textY, separatorColor, false);
            textX += textRenderer.getWidth("-") + 2;

            context.drawText(textRenderer, volumeText, textX, textY, volumeColor, false);
            textX += textRenderer.getWidth(volumeText) + 2;

            int remainingWidth = (x + w - 4) - textX;
            String displayName = nameText;

            if (textRenderer.getWidth(nameText) > remainingWidth) {
                while (textRenderer.getWidth(displayName + "...") > remainingWidth && !displayName.isEmpty()) {
                    displayName = displayName.substring(0, displayName.length() - 1);
                }
                displayName = displayName + "...";
            }

            context.drawText(textRenderer, displayName, textX, textY, nameColor, false);
        }

        public int getSlotIdx() {
            return this.order.slot;
        }
    }

}
