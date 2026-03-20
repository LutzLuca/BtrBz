package com.github.lutzluca.btrbz.core.modules;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.ProductInfoProvider;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.core.config.ConfigScreen.OptionGrouping;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.utils.GameUtils;
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
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ClickType;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply.Product.Summary;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

@Slf4j
public class OrderBookPriceModule extends Module<OrderBookPriceModule.OrderBookPriceConfig> {

    private static final int PRICE_SETUP_SIGN_TRIGGER_SLOT = 16;

    private OrderBookPriceWidget widget;

    private double pendingPrice = -1;
    private boolean pendingSubmit = false;
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
                if (this.pendingSubmit || this.pendingPrice >= 0 || this.currentOrderType != null) {
                    log.debug(
                        "Clearing stale overlay state: prev={}, curr={}",
                        prev.getMenuType(),
                        curr.getMenuType()
                    );
                }

                this.pendingPrice = -1;
                this.pendingSubmit = false;
                this.currentOrderType = null;
            }
        });

        ScreenInfoHelper.registerOnSwitch(info -> {
            if (!this.configState.enabled) {
                return;
            }

            var productNameInfo = ProductInfoProvider.get().getOpenedProductNameInfo();
            if (productNameInfo == null || 
                !(info.getScreen() instanceof SignEditScreen signEditScreen)
            ) {
                return;
            }

            if (!this.pendingSubmit || this.pendingPrice < 0) {
                return;
            }

            String priceStr = Utils.formatDecimal(this.pendingPrice, 1, false);
            GameUtils.submitSignValue(signEditScreen, priceStr);

            this.pendingPrice = -1;
            this.pendingSubmit = false;
        });

        BtrBz.bazaarData().addListener(products -> {
            var productNameInfo = ProductInfoProvider.get().getOpenedProductNameInfo();
            if (this.isDisplayed() && productNameInfo != null) {
                this.rebuildList();
            }
        });
    }

    private boolean isEnterPriceScreen(ScreenInfo curr, ScreenInfo prev) {
        var productNameInfo = ProductInfoProvider.get().getOpenedProductNameInfo();
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
        var productNameInfo = ProductInfoProvider.get().getOpenedProductNameInfo();
        return productNameInfo != null && this.isEnterPriceScreen(info, prev);
    }

    public void rebuildList() {
        if (this.widget == null) {
            return;
        }

        var productNameInfo = ProductInfoProvider.get().getOpenedProductNameInfo();
        if (productNameInfo == null) {
            return;
        }

        if (this.currentOrderType == null) {
            log.debug("Current order type is null, clearing list for product {}", productNameInfo.productName());
            this.widget.updateList(List.of(), this.configState.clickMode);
            return;
        }

        var orders = BtrBz.bazaarData().getOrderLists(productNameInfo.productId());
        var productName = productNameInfo.productName();

        var summaries = switch (this.currentOrderType) {
            case Buy -> orders.buyOrders();
            case Sell -> orders.sellOffers();
        };

        List<Renderable> entries = new ArrayList<>();
        for (var summary : summaries) {
            entries.add(new OrderBookEntry(productName, summary, this.currentOrderType));
        }

        this.widget.updateList(entries, this.configState.clickMode);
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
                this.configState.clickMode,
                mode -> this.updateConfig(cfg -> cfg.clickMode = mode),
                this::handlePriceClick
            );

            this.widget.onDragEnd((self, pos) -> this.savePosition(pos));
        } else {
            this.widget.setMode(this.configState.clickMode);
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
        var productNameInfo = ProductInfoProvider.get().getOpenedProductNameInfo();
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
        ClickMode mode = this.widget.getMode();
        double priceToUse = rawPrice;

        if (mode == ClickMode.Undercut) {
            switch (orderType) {
                case Buy -> priceToUse = rawPrice + 0.1;
                case Sell -> priceToUse = Math.max(rawPrice - 0.1, 0.1);
            }
        }

        if (copyOnly) {
            GameUtils.copyToClipboard(Utils.formatDecimal(priceToUse, 1, false));
            return;
        }

        var client = Minecraft.getInstance();
        var player = client.player;
        var interactionManager = client.gameMode;
        if (player == null || interactionManager == null) {
            return;
        }

        this.pendingSubmit = true;
        this.pendingPrice = priceToUse;

        log.debug("Price click processed: rawPrice={}, finalPrice={}, mode={}", rawPrice, priceToUse, mode);

        if (currInfo.getScreen() instanceof SignEditScreen signEditScreen) {
            GameUtils.submitSignValue(signEditScreen, Utils.formatDecimal(priceToUse, 1, false));

            this.pendingPrice = -1;
            this.pendingSubmit = false;
            return;
        }

        interactionManager.handleInventoryMouseClick(
            currInfo.getGenericContainerScreen().get().getMenu().containerId, PRICE_SETUP_SIGN_TRIGGER_SLOT, 1, ClickType.PICKUP, player
        );
    }

    public enum ClickMode {
        Matched,
        Undercut;

        public static EnumControllerBuilder<ClickMode> controller(Option<ClickMode> option) {
            return EnumControllerBuilder
                .create(option)
                .enumClass(ClickMode.class)
                .formatValue(mode -> switch (mode) {
                    case Matched -> Component.literal("Copy Price");
                    case Undercut -> Component.literal("Undercut (±0.1)");
                });
        }
    }

    public static class OrderBookPriceConfig {
        public Integer signX;
        public Integer signY;
        public boolean enabled = true;
        public ClickMode clickMode = ClickMode.Undercut;

        public Option.Builder<Boolean> createEnableOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.nullToEmpty("Order Book Price Overlay: Master Switch"))
                .description(OptionDescription.of(Component.literal(
                    "Enable or disable the Order Book overlay on price sign screens.")))
                .binding(true, () -> this.enabled, val -> this.enabled = val)
                .controller(ConfigScreen::createBooleanController);
        }

        public Option.Builder<ClickMode> createClickModeOption() {
            return Option
                .<ClickMode>createBuilder()
                .name(Component.nullToEmpty("Default Click Mode"))
                .description(OptionDescription.of(Component.literal(
                    "Default behaviour when clicking an order entry. Can also be toggled in the widget.")))
                .binding(
                    ClickMode.Undercut,
                    () -> this.clickMode != null ? this.clickMode : ClickMode.Undercut,
                    val -> this.clickMode = val
                )
                .controller(ClickMode::controller);
        }

        public OptionGroup createGroup() {
            var rootGroup = new OptionGrouping(this.createEnableOption())
                .addOptions(this.createClickModeOption());

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

        public OrderBookEntry(String productName, Summary summary, OrderType type) {
            this.summary = summary;
            this.type = type;
            this.priceText = Component.literal(Utils.formatDecimal(summary.getPricePerUnit(), 1, true));

            int orders = (int) summary.getOrders();
            String volumeStr = Utils.formatDecimal(summary.getAmount(), 0, true);
            this.statsText = Component.literal(volumeStr + " | " + orders);
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
    }

    // it should be explained why we have this here with all the forwarding of the 
    // callbacks
    private static class OrderBookPriceWidget extends DraggableWidget {
        private static final int HEADER_HEIGHT = 16;
        private static final int LABEL_HEIGHT = 14;
        private static final int BUTTON_HEIGHT = 16;
        private static final int CONTROLS_HEIGHT = LABEL_HEIGHT + BUTTON_HEIGHT;
        private static final int LIST_Y_OFFSET = HEADER_HEIGHT + CONTROLS_HEIGHT;
        private static final int ITEM_HEIGHT = 16;

        private static final int DEFAULT_WIDTH = 280;
        private static final int DEFAULT_HEIGHT = 220;

        private final ListWidget list;
        private final Button modeToggleButton;

        private ClickMode currentMode;
        private final Consumer<ClickMode> onModeChanged;

        public OrderBookPriceWidget(
            int defaultX,
            int defaultY,
            ClickMode initialMode,
            Consumer<ClickMode> onModeChanged,
            PriceClickHandler onClickHandler
        ) {
            super(defaultX, defaultY, DEFAULT_WIDTH, DEFAULT_HEIGHT);
            this.currentMode = initialMode;
            this.onModeChanged = onModeChanged;

            this.modeToggleButton = Button.builder(
                this.getModeButtonText(initialMode),
                btn -> this.toggleMode()
            ).bounds(0, HEADER_HEIGHT + LABEL_HEIGHT, DEFAULT_WIDTH, BUTTON_HEIGHT).build();

            this.list = new ListWidget(0, LIST_Y_OFFSET, DEFAULT_WIDTH, DEFAULT_HEIGHT - LIST_Y_OFFSET, "Order Book");
            this.list.setStatic().setDraggable(false);
            this.list.setItemHeight(ITEM_HEIGHT);
            this.list.onItemClick((self, renderable, index) -> {
                if (renderable instanceof OrderBookEntry entry) {
                    boolean copyOnly = Minecraft.getInstance().hasShiftDown() || Minecraft.getInstance().hasControlDown();
                    onClickHandler.onClick(entry.getPricePerUnit(), copyOnly);
                }
            });
        }

        public void updateList(List<Renderable> items, ClickMode mode) {
            this.list.setItems(items);
            this.setMode(mode);
        }

        public ClickMode getMode() {
            return this.currentMode;
        }

        public void setMode(ClickMode mode) {
            this.currentMode = mode;
            this.modeToggleButton.setMessage(this.getModeButtonText(mode));
        }

        private void toggleMode() {
            this.currentMode = this.currentMode == ClickMode.Matched ? ClickMode.Undercut : ClickMode.Matched;
            this.modeToggleButton.setMessage(this.getModeButtonText(this.currentMode));
            this.onModeChanged.accept(this.currentMode);
        }

        private Component getModeButtonText(ClickMode mode) {
            return Component.literal(mode == ClickMode.Matched ? "[Switch to Undercut]" : "[Switch to Copy]")
                .withStyle(mode == ClickMode.Matched ? ChatFormatting.GOLD : ChatFormatting.AQUA);
        }

        private Component getModeInstruction() {
            return switch (this.currentMode) {
                case Matched -> Component.literal("L-Click: Copy Price").withStyle(ChatFormatting.AQUA);
                case Undercut -> Component.literal("L-Click: Undercut Price").withStyle(ChatFormatting.GOLD);
            };
        }

        @Override
        protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float delta, RenderContext ctx) {
            graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + HEADER_HEIGHT, 0xC0000000);
            graphics.drawCenteredString(Minecraft.getInstance().font, "Order Book", this.getX() + this.width / 2, this.getY() + 4, 0xFFFFFFFF);

            graphics.fill(this.getX(), this.getY() + HEADER_HEIGHT, this.getX() + this.width, this.getY() + LIST_Y_OFFSET, 0x80000000);
            graphics.drawCenteredString(Minecraft.getInstance().font, this.getModeInstruction(), this.getX() + this.width / 2, this.getY() + HEADER_HEIGHT + 3, 0xFFDDDDDD);

            this.modeToggleButton.setX(this.getX());
            this.modeToggleButton.setY(this.getY() + HEADER_HEIGHT + LABEL_HEIGHT);
            this.modeToggleButton.render(graphics, mouseX, mouseY, delta);

            this.list.setX(this.getX());
            this.list.setY(this.getY() + LIST_Y_OFFSET);
            this.list.render(graphics, mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (this.modeToggleButton.isMouseOver(event.x(), event.y()) && this.modeToggleButton.mouseClicked(event, doubleClick)) {
                return true;
            }
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
            if (this.modeToggleButton.isMouseOver(event.x(), event.y()) && this.modeToggleButton.mouseReleased(event)) {
                return true;
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
            if (this.list.isMouseOver(mouseX, mouseY) && this.list.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)){
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
    }
}
