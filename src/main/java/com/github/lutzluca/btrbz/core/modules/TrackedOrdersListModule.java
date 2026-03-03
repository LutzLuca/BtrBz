package com.github.lutzluca.btrbz.core.modules;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.ModuleManager;
import com.github.lutzluca.btrbz.core.OrderHighlightManager;
import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.core.config.ConfigScreen.OptionGrouping;
import com.github.lutzluca.btrbz.core.modules.TrackedOrdersListModule.OrderListConfig;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrder;
import com.github.lutzluca.btrbz.utils.Position;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import com.github.lutzluca.btrbz.utils.Utils;
import com.github.lutzluca.btrbz.widgets.widgets.ListWidget;
import com.github.lutzluca.btrbz.widgets.base.DraggableWidget;
import com.github.lutzluca.btrbz.widgets.base.Renderable;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;


@Slf4j
public class TrackedOrdersListModule extends Module<OrderListConfig> {

    private ListWidget list;
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
        this.list.addItem(entry);
    }

    private void onOrderRemoved(TrackedOrder order) {
        if (this.list == null) {
            return;
        }

        var children = this.list.getItems();
        for (int i = 0; i < children.size(); i++) {
            var widget = (OrderEntryRenderable) children.get(i);
            if (widget.getOrder() == order) {
                this.list.removeItem(i);
                break;
            }
        }
    }

    private void onWidgetHoverEnter(int slotIdx) {
        if (slotIdx == -1) {
            return;
        }

        this.currentHoverSlot = slotIdx;
        BtrBz.highlightManager().setHighlightOverride(slotIdx, 0xCC00FFFF /* 0xAAFFFFFF */);
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

        this.list.clear();

        var orderManager = BtrBz.orderManager();
        for (var order : orderManager.getTrackedOrders()) {
            var entry = this.createEntryWidget(order);
            this.list.addItem(entry);
        }
    }

    public void clearList() {
        if (this.list == null) {
            return;
        }

        this.list.clear();
    }

    public void updateChildrenCount() {
        if (this.list == null) {
            return;
        }

        this.list.setMaxVisibleItems(this.configState.maxVisibleChildren);
    }

    private OrderEntryRenderable createEntryWidget(TrackedOrder order) {
        return new OrderEntryRenderable(order);
    }

    @Override
    public boolean shouldDisplay(ScreenInfo info) {
        return this.configState.enabled && (info.inMenu(BazaarMenuType.Orders) || this.configState.showInBazaar && info.inBazaar());
    }

    @Override
    public List<DraggableWidget> createWidgets(ScreenInfo info) {
        if (this.list != null) {
            return List.of(this.list);
        }

        var position = this.getWidgetPosition(info);
        if (position.isEmpty()) {
            return List.of();
        }

        this.list = new ListWidget(
            position.get().x(),
            position.get().y(),
            175,
            250,
            "Tracked Orders"
        );
        this.list.setItemHeight(14)
            .setItemSpacing(1)
            .setRemovable(false)
            .setReorderable(false)
            .setMaxVisibleItems(this.configState.maxVisibleChildren)
            .onDragEnd((self, pos) -> this.savePosition(pos));

        this.list.onHoverChange((oldIdx, newIdx) -> {
            var items = this.list.getItems();
            var oldOrder = oldIdx >= 0 && oldIdx < items.size() ? ((OrderEntryRenderable) items.get(oldIdx)).getOrder() : null;
            var newOrder = newIdx >= 0 && newIdx < items.size() ? ((OrderEntryRenderable) items.get(newIdx)).getOrder() : null;
            log.debug("Hover change: {} -> {} | Displayed Products: {}, {}", oldIdx, newIdx, oldOrder, newOrder);

            if (oldIdx >= 0 && oldIdx < items.size() && items.get(oldIdx) instanceof OrderEntryRenderable orderEntry) {
                this.onWidgetHoverExit(orderEntry.getSlotIdx());
            }
            if (newIdx >= 0 && newIdx < items.size() && items.get(newIdx) instanceof OrderEntryRenderable orderEntry) {
                this.onWidgetHoverEnter(orderEntry.getSlotIdx());
            }
        });

        this.initializeList();

        return List.of(this.list);
    }

    private void savePosition(Position pos) {
        log.debug("Saving new position for TrackedOrdersListModule: {}", pos);
        this.updateConfig(cfg -> {
            cfg.x = pos.x();
            cfg.y = pos.y();
        });
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
        return Utils.zipNullables(this.configState.x, this.configState.y).map(Position::from);
    }

    public static class OrderListConfig {

        public Integer x, y;

        public boolean enabled = true;
        public boolean showInBazaar = true;
        public int maxVisibleChildren = 6;

        public Option.Builder<Boolean> createInBazaarOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("In Bazaar"))
                .description(OptionDescription.of(Component.literal(
                    "Whether to display the tracked orders list in the Bazaar and not only in the orders screen")))
                .binding(true, () -> this.showInBazaar, enabled -> this.showInBazaar = enabled)
                .controller(ConfigScreen::createBooleanController);
        }

        public Option.Builder<Integer> createMaxVisibleOption() {
            return Option
                .<Integer>createBuilder()
                .name(Component.literal("Max Visible Items"))
                .description(OptionDescription.of(Component.literal(
                    "Maximum number of orders visible at once before scrolling")))
                .binding(
                    6, () -> this.maxVisibleChildren, val -> {
                        this.maxVisibleChildren = val;
                        ModuleManager
                            .getInstance()
                            .getModule(TrackedOrdersListModule.class)
                            .updateChildrenCount();
                    }
                )
                .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(5, 10).step(1));
        }

        public Option.Builder<Boolean> createEnabledOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Order List Module"))
                .description(OptionDescription.of(Component.literal(
                    "Compact list of tracked orders. Hover an entry to highlight its slot.")))
                .binding(true, () -> this.enabled, enabled -> this.enabled = enabled)
                .controller(ConfigScreen::createBooleanController);
        }

        public OptionGroup getGroup() {
            var rootGroup = new OptionGrouping(this.createEnabledOption()).addOptions(
                this.createInBazaarOption(),
                this.createMaxVisibleOption()
            );

            return OptionGroup
                .createBuilder()
                .name(Component.literal("Order List"))
                .description(OptionDescription.of(Component.literal(
                    "Shows your tracked bazaar orders in a compact, hover-highlightable list.")))
                .options(rootGroup.build())
                .collapsed(false)
                .build();
        }
    }

    @Slf4j
    private static class OrderEntryRenderable implements Renderable {

        @Getter
        private final TrackedOrder order;

        public OrderEntryRenderable(TrackedOrder order) {
            this.order = order;
        }

        @Override
        public List<Component> getTooltip() {
            var cfg = ConfigManager.get().orderListTooltip;
            if (!cfg.enabled) {
                return null;
            }

            return BtrBz.tooltipProvider().getCachedTooltip(this.order, cfg);
        }

        @Override
        public void render(GuiGraphics context, int x, int y, int w, int h, int mouseX, int mouseY, float delta, boolean hovered) {
            var textRenderer = Minecraft.getInstance().font;

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

            context.drawString(textRenderer, typeText, textX, textY, typeColor, false);
            textX += textRenderer.width(typeText) + 2;

            context.drawString(textRenderer, "-", textX, textY, separatorColor, false);
            textX += textRenderer.width("-") + 2;

            context.drawString(textRenderer, volumeText, textX, textY, volumeColor, false);
            textX += textRenderer.width(volumeText) + 2;

            int remainingWidth = (x + w - 4) - textX;
            String displayName = nameText;

            if (textRenderer.width(nameText) > remainingWidth) {
                while (textRenderer.width(displayName + "...") > remainingWidth && !displayName.isEmpty()) {
                    displayName = displayName.substring(0, displayName.length() - 1);
                }
                displayName = displayName + "...";
            }

            context.drawString(textRenderer, displayName, textX, textY, nameColor, false);
        }

        public int getSlotIdx() {
            return this.order.slot;
        }
    }
}
