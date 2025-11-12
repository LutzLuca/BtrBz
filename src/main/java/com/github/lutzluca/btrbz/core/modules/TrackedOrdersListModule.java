package com.github.lutzluca.btrbz.core.modules;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.OrderHighlightManager;
import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.core.modules.TrackedOrdersListModule.OrderListConfig;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrder;
import com.github.lutzluca.btrbz.utils.Position;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import com.github.lutzluca.btrbz.utils.Utils;
import com.github.lutzluca.btrbz.widgets.DraggableWidget;
import com.github.lutzluca.btrbz.widgets.ScrollableListWidget;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionEventListener.Event;
import dev.isxander.yacl3.api.OptionGroup;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TrackedOrdersListModule extends Module<OrderListConfig> {

    private final TooltipCache tooltipCache = new TooltipCache();
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
            widget -> this.onWidgetHoverExit(widget.getSlotIdx()),
            this.tooltipCache
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
        return Utils.zipNullables(this.configState.x, this.configState.y).map(Position::from);
    }

    public static class OrderListConfig {

        public Integer x, y;

        public boolean enabled = true;
        public boolean showInBazaar = false;
        public boolean showTooltips = true;

        public Option.Builder<Boolean> createInBazaarOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Text.literal("In Bazaar"))
                .description(OptionDescription.of(Text.literal(
                    "Whether to display the tracked orders list in the Bazaar and not only in the orders screen")))
                .binding(false, () -> this.showInBazaar, enabled -> this.showInBazaar = enabled)
                .controller(ConfigScreen::createBooleanController);
        }

        public Option.Builder<Boolean> createTooltipsOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Text.literal("Show Tooltips"))
                .description(OptionDescription.of(Text.literal(
                    "Whether to show detailed tooltips when hovering over order entries")))
                .binding(true, () -> this.showTooltips, enabled -> this.showTooltips = enabled)
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
            var tooltipsOption = this.createTooltipsOption().build();
            var enabled = this.createEnabledOption();

            enabled.addListener((option, event) -> {
                if (event == Event.STATE_CHANGE) {
                    boolean enabledValue = option.pendingValue();
                    inBazaarOption.setAvailable(enabledValue);
                    tooltipsOption.setAvailable(enabledValue);
                }
            });

            return OptionGroup
                .createBuilder()
                .name(Text.literal("Order List"))
                .description(OptionDescription.of(Text.literal(
                    "Shows your tracked bazaar orders in a compact, hover-highlightable list.")))
                .option(enabled.build())
                .option(inBazaarOption)
                .option(tooltipsOption)
                .collapsed(false)
                .build();
        }
    }

    @Slf4j
    private static class TooltipCache {

        private final Map<@NotNull TrackedOrder, @Nullable List<Text>> cache = new HashMap<>();

        TooltipCache() {
            log.debug("Initializing TooltipCache");
            BtrBz.bazaarData().addListener(products -> {
                log.trace(
                    "Bazaar data updated, clearing tooltip cache with {} entries",
                    this.cache.size()
                );
                this.cache.clear();
            });
        }

        List<Text> getOrCompute(@NotNull TrackedOrder order, Supplier<List<Text>> supplier) {
            return this.cache.computeIfAbsent(
                order, key -> {
                    log.trace("Computing tooltip cache for {}", key);
                    return supplier.get();
                }
            );
        }
    }

    @Slf4j
    private static class OrderEntryWidget extends DraggableWidget {

        @Getter
        private final TrackedOrder order;
        private final Consumer<OrderEntryWidget> onHoverEnter;
        private final Consumer<OrderEntryWidget> onHoverExit;
        private final TooltipCache tooltipCache;
        private boolean wasHovered;

        public OrderEntryWidget(
            int x,
            int y,
            int width,
            int height,
            TrackedOrder order,
            Screen parentScreen,
            Consumer<OrderEntryWidget> onHoverEnter,
            Consumer<OrderEntryWidget> onHoverExit,
            TooltipCache tooltipCache
        ) {
            super(x, y, width, height, Text.literal(order.productName), parentScreen);
            this.tooltipCache = tooltipCache;
            this.order = order;
            this.onHoverEnter = onHoverEnter;
            this.onHoverExit = onHoverExit;
            this.setRenderBorder(false);
            this.setRenderBackground(false);

            this.setTooltipSupplier(this::getTooltipLines);
            this.setTooltipDelay(Duration.ofMillis(300));
        }

        private List<Text> priceLines(BazaarData data, String productId) {
            var priceInfo = data.getOrderPrices(productId);

            var header = Text.literal("Current Prices").formatted(Formatting.GOLD, Formatting.BOLD);

            var buyOrderLine = Text
                .literal("Buy Orders: ")
                .formatted(Formatting.YELLOW)
                .append(priceInfo
                    .buyOrderPrice()
                    .map(price -> Text
                        .literal(Utils.formatDecimal(price, 1, true))
                        .formatted(Formatting.WHITE))
                    .orElse(Text.literal("N/A").formatted(Formatting.DARK_GRAY)));

            var sellOfferLine = Text
                .literal("Sell Offers: ")
                .formatted(Formatting.YELLOW)
                .append(priceInfo
                    .sellOfferPrice()
                    .map(price -> Text
                        .literal(Utils.formatDecimal(price, 1, true))
                        .formatted(Formatting.WHITE))
                    .orElse(Text.literal("N/A").formatted(Formatting.DARK_GRAY)));

            return List.of(header, buyOrderLine, sellOfferLine);
        }

        private List<Text> currOrderLines(BazaarData data) {
            var header = Text.literal("Your Order").formatted(Formatting.GOLD, Formatting.BOLD);

            var priceLine = Text
                .literal("Price: ")
                .formatted(Formatting.GRAY)
                .append(Text
                    .literal(Utils.formatDecimal(this.order.pricePerUnit, 1, true))
                    .formatted(Formatting.WHITE));

            var volumeLine = Text
                .literal("Volume: ")
                .formatted(Formatting.GRAY)
                .append(Text.literal(String.valueOf(order.volume)).formatted(Formatting.WHITE));

            return List.of(header, priceLine, volumeLine);
        }

        private List<Text> statusLines(BazaarData data) {
            List<Text> lines = new ArrayList<>();
            switch (order.status) {
                case OrderStatus.Top ignored -> {
                    lines.add(Text
                        .literal("Best Price!")
                        .formatted(Formatting.GREEN, Formatting.BOLD));
                }
                case OrderStatus.Matched ignored -> {
                    lines.add(Text.literal("Matched!").formatted(Formatting.AQUA, Formatting.BOLD));
                }
                case OrderStatus.Undercut ignored -> {
                    lines.add(Text.literal("Undercut!").formatted(Formatting.RED, Formatting.BOLD));

                    var queueInfo = data.calculateQueuePosition(
                        order.productName,
                        order.type,
                        order.pricePerUnit
                    );

                    if (queueInfo.isPresent()) {
                        lines.add(Text
                            .literal("Orders ahead: ")
                            .formatted(Formatting.GRAY)
                            .append(Text
                                .literal(String.valueOf(queueInfo.get().ordersAhead))
                                .formatted(Formatting.RED)));

                        lines.add(Text
                            .literal("Items ahead: ")
                            .formatted(Formatting.GRAY)
                            .append(Text
                                .literal(Utils.formatDecimal(queueInfo.get().itemsAhead, 0, true))
                                .formatted(Formatting.RED)));
                    }
                }
                case OrderStatus.Unknown ignored -> {
                    lines.add(Text.literal("Status Unknown").formatted(Formatting.GRAY));
                }
            }

            return lines;
        }

        private List<Text> buildTooltipLines() {
            log.debug("Building TooltipLines");

            var data = BtrBz.bazaarData();
            var productId = data.nameToId(this.order.productName);

            if (productId.isEmpty()) {
                return List.of(Text.literal("Unknown Product").formatted(Formatting.RED));
            }

            var lines = new ArrayList<>(this.priceLines(data, productId.get()));
            lines.add(Text.empty());

            lines.addAll(this.currOrderLines(data));
            lines.add(Text.empty());

            lines.addAll(this.statusLines(data));

            return lines;
        }

        public List<Text> getTooltipLines() {
            if (!ConfigManager.get().orderList.showTooltips) {
                return List.of();
            }

            return this.tooltipCache.getOrCompute(this.order, this::buildTooltipLines);
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
