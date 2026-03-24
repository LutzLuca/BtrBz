package com.github.lutzluca.btrbz.core.modules;

import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.core.config.ConfigScreen.OptionGrouping;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.Notifier;
import com.github.lutzluca.btrbz.utils.Position;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import com.github.lutzluca.btrbz.utils.Utils;
import com.github.lutzluca.btrbz.widgets.ListWidget;
import com.github.lutzluca.btrbz.widgets.Renderable;
import com.github.lutzluca.btrbz.widgets.base.DraggableWidget;
import com.github.lutzluca.btrbz.widgets.base.RenderContext;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply.Product.Summary;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
public class OrderBookPriceModule extends Module<OrderBookPriceModule.OrderBookPriceConfig> {

    private OrderBookPriceWidget widget;

    @Nullable private OrderType currentOrderType;

    @Override
    public void onLoad() {
        ScreenInfoHelper.registerOnSwitch(curr -> {
            if (!this.configState.enabled) {
                return;
            }

            var prev = ScreenInfoHelper.get().getPrevInfo();
            this.refreshCurrentOrderType(curr, prev);

            if (!this.isEnterPriceScreen(curr, prev)) {
                if (this.currentOrderType != null) {
                    log.debug(
                        "Clearing stale overlay state: prev={}, curr={}",
                        prev.getMenuType(),
                        curr.getMenuType()
                    );
                }

                this.currentOrderType = null;
            }
        });

        this.context().bazaarData().addListener(products -> {
            var productNameInfo = this.context().productInfoProvider().getOpenedProductNameInfo();
            if (this.isDisplayed() && productNameInfo != null) {
                this.rebuildList();
            }
        });
    }

    private boolean isEnterPriceScreen(ScreenInfo curr, ScreenInfo prev) {
        var productNameInfo = this.context().productInfoProvider().getOpenedProductNameInfo();
        if (!(curr.getScreen() instanceof SignEditScreen) || productNameInfo == null) {
            return false;
        }

        return prev.inMenu(BazaarMenuType.BuyOrderSetupPrice, BazaarMenuType.SellOfferSetup);
    }

    private Optional<OrderType> resolveCurrentOrderType(ScreenInfo curr, ScreenInfo prev) {
        boolean isSign = curr.getScreen() instanceof SignEditScreen;

        if (isSign && prev.inMenu(BazaarMenuType.BuyOrderSetupPrice)) {
            return Optional.of(OrderType.Buy);
        }

        if (isSign && prev.inMenu(BazaarMenuType.SellOfferSetup)) {
            return Optional.of(OrderType.Sell);
        }

        return Optional.empty();
    }

    private void refreshCurrentOrderType(ScreenInfo curr, ScreenInfo prev) {
        this.resolveCurrentOrderType(curr, prev).ifPresent(orderType -> this.currentOrderType = orderType);
    }

    @Override
    public boolean shouldDisplay(ScreenInfo info) {
        if (!this.configState.enabled) {
            return false;
        }

        var prev = ScreenInfoHelper.get().getPrevInfo();
        var productNameInfo = this.context().productInfoProvider().getOpenedProductNameInfo();
        return productNameInfo != null && this.isEnterPriceScreen(info, prev);
    }

    public void rebuildList() {
        if (this.widget == null) {
            return;
        }

        var productNameInfo = this.context().productInfoProvider().getOpenedProductNameInfo();
        if (productNameInfo == null) {
            return;
        }

        if (this.currentOrderType == null) {
            log.debug("Current order type is null, clearing list for product {}", productNameInfo.productName());
            this.widget.updateList(List.of());
            return;
        }

        var orders = this.context().bazaarData().getOrderLists(productNameInfo.productId());

        var summaries = switch (this.currentOrderType) {
            case Buy -> orders.buyOrders();
            case Sell -> orders.sellOffers();
        };

        List<Renderable> entries = new ArrayList<>();
        double accumulatedVolume = 0;
        for (var summary : summaries) {
            accumulatedVolume += summary.getAmount();
            entries.add(new OrderBookEntry(summary, this.currentOrderType, accumulatedVolume));
        }

        this.widget.updateList(entries);
    }

    @Override
    public List<DraggableWidget> createWidgets(ScreenInfo info) {
        var prev = ScreenInfoHelper.get().getPrevInfo();
        if (!this.isEnterPriceScreen(info, prev)) {
            return List.of();
        }
        this.resolveCurrentOrderType(info, prev).ifPresent(orderType -> this.currentOrderType = orderType);

        if (this.currentOrderType == null) {
            return List.of();
        }

        var position = this.getPosition();
        if (this.widget == null) {
            this.widget = new OrderBookPriceWidget(
                position.map(Position::x).orElse(20),
                position.map(Position::y).orElse(20),
                this::handlePriceClick
            );

            this.widget.onDragEnd((self, pos) -> this.savePosition(pos));
        }

        this.widget.setDraggable(true);

        this.rebuildList();
        return List.of(this.widget);
    }

    private Optional<Position> getPosition() {
        return Utils
            .zipNullables(this.configState.signX, this.configState.signY)
            .map(Position::from);
    }

    private void savePosition(Position pos) {
        this.updateConfig(cfg -> {
            cfg.signX = pos.x();
            cfg.signY = pos.y();
        });
    }

    private void handlePriceClick(double rawPrice, boolean copyOnly) {
        var productNameInfo = this.context().productInfoProvider().getOpenedProductNameInfo();
        if (productNameInfo == null) {
            return;
        }

        var currInfo = ScreenInfoHelper.get().getCurrInfo();
        var prevInfo = ScreenInfoHelper.get().getPrevInfo();
        var orderType = this.resolveCurrentOrderType(currInfo, prevInfo).orElse(this.currentOrderType);
        if (orderType == null) {
            return;
        }

        this.currentOrderType = orderType;

        if (copyOnly) {
            String formattedPrice = Utils.formatDecimal(rawPrice, 1, false);
            GameUtils.copyToClipboard(formattedPrice);
            Notifier.notifyPlayer(Notifier
                .prefix()
                .append(Component.literal("Copied price ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(formattedPrice).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                .append(Component.literal(" to clipboard").withStyle(ChatFormatting.GRAY)));
            return;
        }

        double priceToUse = this.applyUndercut(rawPrice, orderType);

        log.debug("Price click processed: rawPrice={}, finalPrice={}", rawPrice, priceToUse);

        if (currInfo.getScreen() instanceof SignEditScreen signEditScreen) {
            GameUtils.submitSignValue(signEditScreen, Utils.formatDecimal(priceToUse, 1, false));
        }
    }

    private double applyUndercut(double rawPrice, OrderType orderType) {
        return switch (orderType) {
            case Buy -> rawPrice + 0.1;
            case Sell -> Math.max(rawPrice - 0.1, 0.1);
        };
    }

    public static class OrderBookPriceConfig {
        public Integer signX;
        public Integer signY;
        public boolean enabled = true;

        public Option.Builder<Boolean> createEnableOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.nullToEmpty("Order Book Price Overlay: Master Switch"))
                .description(OptionDescription.of(Component.literal(
                    "Enable or disable the Order Book overlay on price sign screens.")))
                .binding(true, () -> this.enabled, val -> this.enabled = val)
                .controller(ConfigScreen::createBooleanController);
        }

        public OptionGroup createGroup() {
            var rootGroup = new OptionGrouping(this.createEnableOption());

            return OptionGroup
                .createBuilder()
                .name(Component.nullToEmpty("Order Book Price Overlay"))
                .description(OptionDescription.of(Component.literal(
                    "Displays order book data next to the price entry sign.")))
                .options(rootGroup.build())
                .collapsed(false)
                .build();
        }
    }

    @FunctionalInterface
    public interface PriceClickHandler {
        void onClick(double rawPrice, boolean copyOnly);
    }

    private static class OrderBookEntry implements Renderable {
        private final Summary summary;
        private final OrderType type;
        private final Component priceText;
        private final Component statsText;
        private final List<Component> tooltip;

        public OrderBookEntry(Summary summary, OrderType type, double accumulatedVolume) {
            this.summary = summary;
            this.type = type;
            this.priceText = Component.literal(Utils.formatDecimal(summary.getPricePerUnit(), 1, true));

            int orders = (int) summary.getOrders();
            String volumeStr = Utils.formatDecimal(summary.getAmount(), 0, true);
            this.statsText = Component.literal("Vol: " + volumeStr + "  Ord: " + orders);

            String cumulativeVolumeStr = Utils.formatDecimal(accumulatedVolume, 0, true);
            this.tooltip = List.of(
                Component.literal("Price: " + Utils.formatDecimal(summary.getPricePerUnit(), 1, true)).withStyle(ChatFormatting.GOLD),
                Component.literal("Level Volume: " + volumeStr).withStyle(ChatFormatting.GRAY),
                Component.literal("Orders: " + orders).withStyle(ChatFormatting.GRAY),
                Component.literal("Cumulative Volume: " + cumulativeVolumeStr).withStyle(ChatFormatting.AQUA)
            );
        }

        @Override
        public void render(
            GuiGraphics graphics,
            int x, int y,
            int width, int height,
            int mouseX, int mouseY, float delta,
            boolean hovered
        ) {
            var font = Minecraft.getInstance().font;

            if (hovered) {
                int color = this.type == OrderType.Buy ? 0x6022AA22 : 0x60AA2222;
                graphics.fill(x, y, x + width, y + height, color);
            }

            int priceColor = this.type == OrderType.Buy ? 0xFF55FF55 : 0xFFFF5555;
            int textY = y + (height - font.lineHeight) / 2;

            graphics.drawString(font, this.priceText, x + 5, textY, priceColor);

            int statsWidth = font.width(this.statsText);
            graphics.drawString(font, this.statsText, x + width - statsWidth - 5, textY, 0xFFCCCCCC);
        }

        public double getPricePerUnit() {
            return this.summary.getPricePerUnit();
        }

        @Override
        public List<Component> getTooltip() {
            return this.tooltip;
        }
    }

    private static class OrderBookPriceWidget extends DraggableWidget {
        private static final int HEADER_HEIGHT = 16;
        private static final int INSTRUCTION_HEIGHT = 14;
        private static final int LIST_Y_OFFSET = HEADER_HEIGHT + INSTRUCTION_HEIGHT;
        private static final int ITEM_HEIGHT = 16;

        private static final int DEFAULT_WIDTH = 230;
        private static final int DEFAULT_HEIGHT = 220;

        private static final int HEADER_BACKGROUND_COLOR = 0xC0000000;
        private static final int INSTRUCTION_BACKGROUND_COLOR = 0x80000000;
        private static final int TITLE_COLOR = 0xFFFFFFFF;
        private static final int INSTRUCTION_TEXT_COLOR = 0xFFDDDDDD;

        private static final Component INSTRUCTION_TEXT = Component
            .literal("Click -> Undercut | Ctrl-Click -> Copy price")
            .withStyle(ChatFormatting.GOLD);

        private final ListWidget list;

        public OrderBookPriceWidget(int defaultX, int defaultY, PriceClickHandler onClickHandler) {
            super(defaultX, defaultY, DEFAULT_WIDTH, DEFAULT_HEIGHT);

            this.list = new ListWidget(0, LIST_Y_OFFSET, DEFAULT_WIDTH, DEFAULT_HEIGHT - LIST_Y_OFFSET, "Order Book");
            this.list.setStatic().setDraggable(false);
            this.list.setItemHeight(ITEM_HEIGHT);
            this.list.onItemClick((self, renderable, index) -> {
                if (renderable instanceof OrderBookEntry entry) {
                    boolean copyOnly = Minecraft.getInstance().hasControlDown();
                    onClickHandler.onClick(entry.getPricePerUnit(), copyOnly);
                }
            });
        }

        public void updateList(List<Renderable> items) {
            this.list.setItems(items);
        }

        @Override
        protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float delta, RenderContext ctx) {
            this.renderHeader(graphics);
            this.renderInstruction(graphics);
            this.renderList(graphics, mouseX, mouseY, delta);
        }

        private void renderHeader(GuiGraphics graphics) {
            graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + HEADER_HEIGHT, HEADER_BACKGROUND_COLOR);
            graphics.drawCenteredString(Minecraft.getInstance().font, "Order Book", this.getX() + this.width / 2, this.getY() + 4, TITLE_COLOR);
        }

        private void renderInstruction(GuiGraphics graphics) {
            int x = this.getX();
            int y = this.getY() + HEADER_HEIGHT;
            int width = this.width;
            int height = LIST_Y_OFFSET - HEADER_HEIGHT;

            graphics.fill(
                x,
                y,
                x + width,
                y + height,
                INSTRUCTION_BACKGROUND_COLOR
            );
            graphics.drawCenteredString(
                Minecraft.getInstance().font,
                INSTRUCTION_TEXT,
                x + width / 2,
                y + 3,
                INSTRUCTION_TEXT_COLOR
            );
        }

        private void renderList(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            this.list.setX(this.getX());
            this.list.setY(this.getY() + LIST_Y_OFFSET);
            this.list.render(graphics, mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (this.list.isMouseOver(event.x(), event.y()) && this.list.mouseClicked(event, doubleClick)) {
                return true;
            }

            return super.mouseClicked(event, doubleClick);
        }

        @Override
        public boolean mouseReleased(MouseButtonEvent event) {
            if (this.isDragging()) {
                return super.mouseReleased(event);
            }
            if (this.list.mouseReleased(event)) {
                return true;
            }
            return super.mouseReleased(event);
        }

        @Override
        public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
            if (this.isDragging()) {
                return super.mouseDragged(event, deltaX, deltaY);
            }
            if (this.list.isMouseOver(event.x(), event.y()) && this.list.mouseDragged(event, deltaX, deltaY)) {
                return true;
            }
            return super.mouseDragged(event, deltaX, deltaY);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            if (this.list.isMouseOver(mouseX, mouseY) && this.list.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
    }
}
