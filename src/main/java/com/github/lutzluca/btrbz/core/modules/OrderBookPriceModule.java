package com.github.lutzluca.btrbz.core.modules;

import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.core.config.ConfigScreen.OptionGrouping;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.ProductIdentity;
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
import dev.isxander.yacl3.api.OptionGroup;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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

    private static final BazaarMenuType[] REGULAR_PRICE_SETUP_MENUS = {
        BazaarMenuType.BuyOrderSetupPrice,
        BazaarMenuType.SellOfferSetup
    };

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

        this.context().bazaarData().addListener(snapshot -> {
            var prev = ScreenInfoHelper.get().getPrevInfo();
            if (this.isDisplayed() && this.resolveProduct(prev).isPresent()) {
                this.rebuildList();
            }
        });
    }

    private boolean isEnterPriceScreen(ScreenInfo curr, ScreenInfo prev) {
        if (!(curr.getScreen() instanceof SignEditScreen) || this.resolveProduct(prev).isEmpty()) {
            return false;
        }

        return prev.inMenu(REGULAR_PRICE_SETUP_MENUS)
            || (this.configState.showOnFlipSign && prev.inMenu(BazaarMenuType.OrderOptions));
    }

    private Optional<IndexedProduct> resolveProduct(ScreenInfo prev) {
        if (prev.inMenu(BazaarMenuType.OrderOptions)) {
            // OrderOptions does not expose the product, so use the product captured from the clicked order.
            return this.context().flipProductContext().getSelectedProduct();
        }

        return Optional.ofNullable(this.context().productInfoProvider().getOpenedProduct());
    }

    private Optional<OrderType> resolveCurrentOrderType(ScreenInfo curr, ScreenInfo prev) {
        boolean isSign = curr.getScreen() instanceof SignEditScreen;

        if (isSign && prev.inMenu(BazaarMenuType.BuyOrderSetupPrice)) {
            return Optional.of(OrderType.Buy);
        }

        if (isSign && prev.inMenu(BazaarMenuType.SellOfferSetup)) {
            return Optional.of(OrderType.Sell);
        }

        if (isSign
            && this.configState.showOnFlipSign
            && prev.inMenu(BazaarMenuType.OrderOptions)) {
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
        return this.isEnterPriceScreen(info, prev);
    }

    public void rebuildList() {
        if (this.widget == null) {
            return;
        }

        var prev = ScreenInfoHelper.get().getPrevInfo();
        var product = this.resolveProduct(prev);
        if (product.isEmpty()) {
            return;
        }

        if (this.currentOrderType == null) {
            log.debug("Current order type is null, clearing list for product {}", product.get());
            this.widget.updateList(List.of());
            return;
        }

        var orders = this.context()
            .bazaarData()
            .getOrderLists(ProductIdentity.fromIndex(product.get()));

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
    public Optional<DraggableWidget> createWidget(ScreenInfo info) {
        var prev = ScreenInfoHelper.get().getPrevInfo();
        if (!this.isEnterPriceScreen(info, prev)) {
            return Optional.empty();
        }
        this.resolveCurrentOrderType(info, prev).ifPresent(orderType -> this.currentOrderType = orderType);

        if (this.currentOrderType == null) {
            return Optional.empty();
        }

        var position = this.getPosition();
        if (this.widget == null) {
            this.widget = new OrderBookPriceWidget(
                position.map(Position::x).orElse(20),
                position.map(Position::y).orElse(20),
                this::handlePriceClick
            );

            this.widget.onDragEnd((self, pos) -> this.updateConfig(cfg -> cfg.signPosition = pos));
        }

        this.widget.setDraggable(true);

        this.rebuildList();
        return Optional.of(this.widget);
    }

    private Optional<Position> getPosition() {
        return Optional.ofNullable(this.configState.signPosition);
    }

    private void handlePriceClick(double rawPrice, boolean copyOnly) {
        var currInfo = ScreenInfoHelper.get().getCurrInfo();
        var prevInfo = ScreenInfoHelper.get().getPrevInfo();
        var product = this.resolveProduct(prevInfo);
        if (product.isEmpty()) {
            return;
        }

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
            if (prevInfo.inMenu(BazaarMenuType.OrderOptions)) {
                this.context()
                    .flipSubmissionTracker()
                    .recordSubmittedFlip(ProductIdentity.fromIndex(product.get()), priceToUse);
            }
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
        public Position signPosition;
        public boolean enabled = true;
        public boolean showOnFlipSign = true;

        public Option.Builder<Boolean> createEnableOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.nullToEmpty("Enable Price Entry Order Book Overlay"))
                .description(ConfigScreen.createDescription(ConfigScreen.paragraphs(
                    ConfigScreen.text(
                        "Show current buy orders and sell offers beside the price-entry sign."),
                    ConfigScreen.note(
                        "Click a price to enter 0.1 coins ahead of it, or Ctrl-click to copy the displayed price unchanged.")
                )))
                .binding(true, () -> this.enabled, val -> this.enabled = val)
                .controller(ConfigScreen::createBooleanController);
        }

        public Option.Builder<Boolean> createShowOnFlipSignOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.nullToEmpty("Show on Flip Price Sign"))
                .description(ConfigScreen.createDescription(ConfigScreen.paragraphs(
                    ConfigScreen.text(
                        "Show the sell-offer order book when entering a custom price for a filled buy order."),
                    ConfigScreen.note(
                        "Disable this to keep the overlay on regular buy-order and sell-offer price signs only.")
                )))
                .binding(
                    true,
                    () -> this.showOnFlipSign,
                    val -> this.showOnFlipSign = val
                )
                .controller(ConfigScreen::createBooleanController);
        }

        public OptionGroup createGroup() {
            var rootGroup = new OptionGrouping(this.createEnableOption())
                .addOptions(this.createShowOnFlipSignOption());

            return OptionGroup
                .createBuilder()
                .name(Component.nullToEmpty("Price Entry Order Book Overlay"))
                .description(ConfigScreen.createDescription(ConfigScreen.paragraphs(
                    ConfigScreen.text("Compare prices without leaving the price-entry sign."),
                    ConfigScreen.example(
                        "Clicking 100 enters 100.1 for a buy order or 99.9 for a sell offer. Ctrl-click copies 100 instead.")
                ),
                    ConfigScreen.ConfigImage.PRICE_ENTRY_ORDER_BOOK
                ))
                .options(rootGroup.build())
                .collapsed(true)
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
            GuiGraphicsExtractor graphics,
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

            graphics.text(font, this.priceText, x + 5, textY, priceColor);

            int statsWidth = font.width(this.statsText);
            graphics.text(font, this.statsText, x + width - statsWidth - 5, textY, 0xFFCCCCCC);
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
        protected void renderContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, RenderContext ctx) {
            this.renderHeader(graphics);
            this.renderInstruction(graphics);
            this.renderList(graphics, mouseX, mouseY, delta);
        }

        private void renderHeader(GuiGraphicsExtractor graphics) {
            graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + HEADER_HEIGHT, HEADER_BACKGROUND_COLOR);
            graphics.centeredText(Minecraft.getInstance().font, "Order Book", this.getX() + this.width / 2, this.getY() + 4, TITLE_COLOR);
        }

        private void renderInstruction(GuiGraphicsExtractor graphics) {
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
            graphics.centeredText(
                Minecraft.getInstance().font,
                INSTRUCTION_TEXT,
                x + width / 2,
                y + 3,
                INSTRUCTION_TEXT_COLOR
            );
        }

        private void renderList(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            this.list.setX(this.getX());
            this.list.setY(this.getY() + LIST_Y_OFFSET);
            this.list.extractRenderState(graphics, mouseX, mouseY, delta);
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
