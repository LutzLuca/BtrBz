package com.github.lutzluca.btrbz.core.modules;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.ProductInfoProvider;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.core.config.ConfigScreen.OptionGrouping;
import com.github.lutzluca.btrbz.core.order_book.OrderBookScreen.OrderBookRenderable;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.utils.GameUtils;
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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

@Slf4j
public class OrderBookPriceModule extends Module<OrderBookPriceModule.OrderBookPriceConfig> {

    private static final int PRICE_SETUP_SIGN_TRIGGER_SLOT = 16;

    private OrderBookPriceWidget widget;

    // Transaction state
    private double pendingPrice = -1;
    private boolean pendingSubmit = false;
    @Nullable private OrderType currentOrderType;

    @Override
    public void onLoad() {
        ScreenInfoHelper.registerOnSwitch(curr -> {
            if (!this.configState.enabled || this.configState.displayMode == DisplayMode.Off) {
                return;
            }

            var prev = ScreenInfoHelper.get().getPrevInfo();
            this.refreshCurrentOrderType(curr, prev);

            if (this.isContainerPriceScreen(curr)) {
                this.rebuildLists();
            }

            if (!this.isPriceScreen(curr)) {
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

            if (this.getCurrentProductId() == null) {
                return;
            }

            if (!(info.getScreen() instanceof SignEditScreen signEditScreen)) {
                return;
            }

            if (!this.pendingSubmit || this.pendingPrice < 0) {
                return;
            }

            // Submit price directly to the sign
            String priceStr = Utils.formatDecimal(this.pendingPrice, 1, false);
            GameUtils.submitSignValue(signEditScreen, priceStr);

            this.pendingPrice = -1;
            this.pendingSubmit = false;
        });
        
        // Also listen for data updates so order lists refresh
        BtrBz.bazaarData().addListener(products -> {
            if (this.isDisplayed() && this.getCurrentProductId() != null) {
                this.rebuildLists();
            }
        });
    }

    private @Nullable String getCurrentProductId() {
        var info = ProductInfoProvider.get().getOpenedProductNameInfo();
        return info != null ? info.productId() : null;
    }

    private boolean isContainerPriceScreen(ScreenInfo info) {
        return info.inMenu(BazaarMenuType.BuyOrderSetupPrice, BazaarMenuType.SellOfferSetup);
    }

    private boolean isSignPriceScreen(ScreenInfo info) {
        return info.getScreen() instanceof SignEditScreen && this.getCurrentProductId() != null;
    }

    private boolean isPriceScreen(ScreenInfo info) {
        return this.isContainerPriceScreen(info) || this.isSignPriceScreen(info);
    }

    private Optional<OrderType> resolveCurrentOrderType(ScreenInfo curr, ScreenInfo prev) {
        if (curr.inMenu(BazaarMenuType.BuyOrderSetupPrice) ||
            (curr.getScreen() instanceof SignEditScreen && prev.inMenu(BazaarMenuType.BuyOrderSetupPrice))) {
            return Optional.of(OrderType.Buy);
        }

        if (curr.inMenu(BazaarMenuType.SellOfferSetup) ||
            (curr.getScreen() instanceof SignEditScreen && prev.inMenu(BazaarMenuType.SellOfferSetup))) {
            return Optional.of(OrderType.Sell);
        }

        return Optional.empty();
    }

    private void refreshCurrentOrderType(ScreenInfo curr, ScreenInfo prev) {
        this.resolveCurrentOrderType(curr, prev).ifPresent(orderType -> this.currentOrderType = orderType);
    }

    public enum PriceScreen {
        Container,
        Sign
    }

    private Optional<PriceScreen> getPriceScreen(ScreenInfo info) {
        if (this.isContainerPriceScreen(info)) {
            return Optional.of(PriceScreen.Container);
        }

        if (this.isSignPriceScreen(info)) {
            return Optional.of(PriceScreen.Sign);
        }

        return Optional.empty();
    }

    @Override
    public boolean shouldDisplay(ScreenInfo info) {
        if (!this.configState.enabled || this.configState.displayMode == DisplayMode.Off) {
            return false;
        }

        return this.getCurrentProductId() != null && this.getPriceScreen(info).isPresent();
    }

    public void rebuildLists() {
        if (this.widget == null) {
            return;
        }

        var productId = this.getCurrentProductId();
        if (productId == null) {
            return;
        }

        if (this.configState.displayMode == DisplayMode.Relevant && this.currentOrderType == null) {
            return;
        }

        var orders = BtrBz.bazaarData().getOrderLists(productId);

        List<Renderable> buyWidgets = new ArrayList<>();
        for (var summary : orders.buyOrders()) {
            buyWidgets.add(new OrderBookRenderable(summary, OrderType.Buy));
        }

        List<Renderable> sellWidgets = new ArrayList<>();
        for (var summary : orders.sellOffers()) {
            sellWidgets.add(new OrderBookRenderable(summary, OrderType.Sell));
        }

        boolean showBuy = switch (this.configState.displayMode) {
            case Relevant -> this.currentOrderType == OrderType.Buy;
            case Both -> true;
            case Off -> false;
        };

        boolean showSell = switch (this.configState.displayMode) {
            case Relevant -> this.currentOrderType == OrderType.Sell;
            case Both -> true;
            case Off -> false;
        };

        this.widget.updateLists(showBuy, buyWidgets, showSell, sellWidgets, this.configState.clickMode);
    }

    @Override
    public List<DraggableWidget> createWidgets(ScreenInfo info) {
        var screen = this.getPriceScreen(info);
        if (screen.isEmpty()) {
            return List.of();
        }

        var bounds = info
            .getHandledScreenBounds()
            .or(() -> ScreenInfoHelper.get().getPrevInfo().getHandledScreenBounds());

        if (bounds.isEmpty()) {
            return List.of();
        }

        int x = bounds.get().x();
        int y = 0;
        int width = bounds.get().width();
        int height = Math.max(bounds.get().y(), 0);

        if (this.widget == null) {
            this.widget = new OrderBookPriceWidget(
                x,
                y,
                width,
                height,
                this.configState.clickMode,
                mode -> this.updateConfig(cfg -> cfg.clickMode = mode),
                this::handlePriceClick
            );
            this.widget.setDraggable(false);
        } else {
            this.widget.setX(x);
            this.widget.setY(y);
            this.widget.setOverlayBounds(width, height);
            this.widget.setMode(this.configState.clickMode);
        }

        this.rebuildLists();
        return List.of(this.widget);
    }

    private void handlePriceClick(double rawPrice, boolean copyOnly) {
        if (this.getCurrentProductId() == null) {
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

    public enum DisplayMode {
        Relevant,
        Both,
        Off;

        public static EnumControllerBuilder<DisplayMode> controller(Option<DisplayMode> option) {
            return EnumControllerBuilder
                .create(option)
                .enumClass(DisplayMode.class)
                .formatValue(mode -> switch (mode) {
                    case Relevant -> Component.literal("Relevant Only");
                    case Both -> Component.literal("Both");
                    case Off -> Component.literal("Off");
                });
        }
    }

    public enum ClickMode {
        Copy,
        Undercut;

        public static EnumControllerBuilder<ClickMode> controller(Option<ClickMode> option) {
            return EnumControllerBuilder
                .create(option)
                .enumClass(ClickMode.class)
                .formatValue(mode -> switch (mode) {
                    case Copy -> Component.literal("Copy Price");
                    case Undercut -> Component.literal("Undercut (±0.1)");
                });
        }
    }

    public static class OrderBookPriceConfig {
        public boolean enabled = true;
        public DisplayMode displayMode = DisplayMode.Relevant;
        public ClickMode clickMode = ClickMode.Undercut;

        public Option.Builder<Boolean> createEnableOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.nullToEmpty("Order Book Price Overlay: Master Switch"))
                .description(OptionDescription.of(Component.literal(
                    "Master switch to enable or disable the Order Book overlay on price entry screens.")))
                .binding(true, () -> this.enabled, val -> this.enabled = val)
                .controller(ConfigScreen::createBooleanController);
        }

        public Option.Builder<DisplayMode> createDisplayModeOption() {
            return Option
                .<DisplayMode>createBuilder()
                .name(Component.nullToEmpty("Display Mode"))
                .description(OptionDescription.of(Component.literal(
                    "Which order lists to show on the overlay.")))
                .binding(
                    DisplayMode.Relevant,
                    () -> this.displayMode != null ? this.displayMode : DisplayMode.Relevant,
                    val -> this.displayMode = val
                )
                .controller(DisplayMode::controller);
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
                .addOptions(
                    this.createDisplayModeOption(),
                    this.createClickModeOption()
                );

            return OptionGroup
                .createBuilder()
                .name(Component.nullToEmpty("Order Book Price Overlay"))
                .description(OptionDescription.of(Component.literal(
                    "Displays order book data over the price entry sign/menu.")))
                .options(rootGroup.build())
                .collapsed(false)
                .build();
        }
    }

    @FunctionalInterface
    public interface PriceClickHandler {
        void onClick(double rawPrice, boolean copyOnly);
    }

    private static class OrderBookPriceWidget extends DraggableWidget {
        private static final int HEADER_HEIGHT = 16;
        private static final int LABEL_HEIGHT = 14;
        private static final int BUTTON_HEIGHT = 16;
        private static final int CONTROLS_HEIGHT = LABEL_HEIGHT + BUTTON_HEIGHT;
        private static final int LIST_Y_OFFSET = HEADER_HEIGHT + CONTROLS_HEIGHT;
        private static final int LIST_GAP = 5;
        private static final int MIN_LIST_WIDTH = 80;
        private static final int MIN_LIST_HEIGHT = 30;

        private final ListWidget buyList;
        private final ListWidget sellList;
        private final Button modeToggleButton;
        
        private ClickMode currentMode;
        private final Consumer<ClickMode> onModeChanged;
        
        private boolean showBuy;
        private boolean showSell;

        private int listWidth;
        private int listHeight;

        public OrderBookPriceWidget(
            int defaultX,
            int defaultY,
            int width,
            int height,
            ClickMode initialMode,
            Consumer<ClickMode> onModeChanged,
            PriceClickHandler onClickHandler
        ) {
            super(defaultX, defaultY, width, height);
            this.currentMode = initialMode;
            this.onModeChanged = onModeChanged;

            this.listWidth = Math.max((width - LIST_GAP) / 2, MIN_LIST_WIDTH);
            this.listHeight = Math.max(height - LIST_Y_OFFSET, MIN_LIST_HEIGHT);

            // Mode toggle button spans the width at the top (under drag handle area + label)
            this.modeToggleButton = Button.builder(
                this.getModeButtonText(initialMode),
                btn -> this.toggleMode()
            ).bounds(0, HEADER_HEIGHT + LABEL_HEIGHT, width, BUTTON_HEIGHT).build();

            this.buyList = new ListWidget(0, LIST_Y_OFFSET, this.listWidth, this.listHeight, "Buy Orders");
            this.buyList.setStatic().setDraggable(false);
            this.buyList.setItemHeight(14);
            this.buyList.onItemClick((self, renderable, index) -> {
                if (renderable instanceof OrderBookRenderable br) {
                    boolean copyOnly = Minecraft.getInstance().hasShiftDown() || Minecraft.getInstance().hasControlDown();
                    onClickHandler.onClick(br.getPricePerUnit(), copyOnly);
                }
            });

            this.sellList = new ListWidget(0, LIST_Y_OFFSET, this.listWidth, this.listHeight, "Sell Offers");
            this.sellList.setStatic().setDraggable(false);
            this.sellList.setItemHeight(14);
            this.sellList.onItemClick((self, renderable, index) -> {
                if (renderable instanceof OrderBookRenderable br) {
                    boolean copyOnly = Minecraft.getInstance().hasShiftDown() || Minecraft.getInstance().hasControlDown();
                    onClickHandler.onClick(br.getPricePerUnit(), copyOnly);
                }
            });

            this.setDraggable(false);
            this.setOverlayBounds(width, height);
        }

        public void setOverlayBounds(int width, int height) {
            this.width = Math.max(width, MIN_LIST_WIDTH);
            this.height = Math.max(height, LIST_Y_OFFSET + MIN_LIST_HEIGHT);

            this.modeToggleButton.setWidth(this.width);
            this.updateChildLayout();
            this.updateChildPositions();
        }
        
        public ClickMode getMode() {
            return this.currentMode;
        }

        public void setMode(ClickMode mode) {
            this.currentMode = mode;
            this.modeToggleButton.setMessage(this.getModeButtonText(mode));
        }

        private void toggleMode() {
            this.currentMode = this.currentMode == ClickMode.Copy ? ClickMode.Undercut : ClickMode.Copy;
            this.modeToggleButton.setMessage(this.getModeButtonText(this.currentMode));
            this.onModeChanged.accept(this.currentMode);
        }

        private Component getModeButtonText(ClickMode mode) {
            return Component.literal(mode == ClickMode.Copy ? "[Switch to Undercut]" : "[Switch to Copy]")
                .withStyle(mode == ClickMode.Copy ? ChatFormatting.GOLD : ChatFormatting.AQUA);
        }

        private Component getModeInstruction() {
            if (this.currentMode == ClickMode.Copy) {
                return Component.literal("L-Click: Copy Price").withStyle(ChatFormatting.AQUA);
            } else {
                return Component.literal("L-Click: Undercut Price").withStyle(ChatFormatting.GOLD);
            }
        }

        public void updateLists(boolean showBuy, List<Renderable> buyItems, boolean showSell, List<Renderable> sellItems, ClickMode mode) {
            this.showBuy = showBuy;
            this.showSell = showSell;
            this.buyList.setItems(buyItems);
            this.sellList.setItems(sellItems);
            this.setMode(mode);

            this.updateChildLayout();
            this.updateChildPositions();
        }

        @Override
        public void setX(int x) {
            super.setX(x);
            this.updateChildPositions();
        }

        @Override
        public void setY(int y) {
            super.setY(y);
            this.updateChildPositions();
        }

        private void updateChildLayout() {
            int visibleColumns = (this.showBuy ? 1 : 0) + (this.showSell ? 1 : 0);
            if (visibleColumns <= 0) {
                this.listWidth = Math.max(this.width, MIN_LIST_WIDTH);
            } else {
                int gap = visibleColumns > 1 ? LIST_GAP : 0;
                this.listWidth = Math.max((this.width - gap) / visibleColumns, MIN_LIST_WIDTH);
            }

            this.listHeight = Math.max(this.height - LIST_Y_OFFSET, MIN_LIST_HEIGHT);

            this.buyList.setWidth(this.listWidth);
            this.buyList.setHeight(this.listHeight);
            this.sellList.setWidth(this.listWidth);
            this.sellList.setHeight(this.listHeight);
        }

        private void updateChildPositions() {
            this.modeToggleButton.setX(this.getX());
            this.modeToggleButton.setY(this.getY() + HEADER_HEIGHT + LABEL_HEIGHT);

            int curX = this.getX();
            if (this.showBuy) {
                this.buyList.setX(curX);
                if (this.showSell) {
                    curX += this.listWidth + LIST_GAP;
                }
            }
            if (this.showSell) {
                this.sellList.setX(curX);
            }

            this.buyList.setY(this.getY() + LIST_Y_OFFSET);
            this.sellList.setY(this.getY() + LIST_Y_OFFSET);
        }

        @Override
        protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float delta, RenderContext ctx) {
            // Main Widget Header
            graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + HEADER_HEIGHT, 0xC0000000);
            graphics.drawCenteredString(Minecraft.getInstance().font, "Order Book", this.getX() + this.width / 2, this.getY() + 4, 0xFFFFFFFF);

            // Controls area background
            graphics.fill(this.getX(), this.getY() + HEADER_HEIGHT, this.getX() + this.width, this.getY() + LIST_Y_OFFSET, 0x80000000);
            
            // Instructions Label
            graphics.drawCenteredString(Minecraft.getInstance().font, this.getModeInstruction(), this.getX() + this.width / 2, this.getY() + HEADER_HEIGHT + 3, 0xFFDDDDDD);

            this.modeToggleButton.render(graphics, mouseX, mouseY, delta);

            if (this.showBuy) this.buyList.render(graphics, mouseX, mouseY, delta);
            if (this.showSell) this.sellList.render(graphics, mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (this.modeToggleButton.isMouseOver(event.x(), event.y()) && this.modeToggleButton.mouseClicked(event, doubleClick)) {
                return true;
            }
            if (this.showBuy && this.buyList.isMouseOver(event.x(), event.y()) && this.buyList.mouseClicked(event, doubleClick)) return true;
            if (this.showSell && this.sellList.isMouseOver(event.x(), event.y()) && this.sellList.mouseClicked(event, doubleClick)) return true;
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
            if (this.showBuy && this.buyList.mouseReleased(event)) return true;
            if (this.showSell && this.sellList.mouseReleased(event)) return true;
            return super.mouseReleased(event);
        }

        @Override
        public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
            if (this.isDragging()) {
                return super.mouseDragged(event, deltaX, deltaY);
            }
            if (this.showBuy && this.buyList.isMouseOver(event.x(), event.y()) && this.buyList.mouseDragged(event, deltaX, deltaY)) return true;
            if (this.showSell && this.sellList.isMouseOver(event.x(), event.y()) && this.sellList.mouseDragged(event, deltaX, deltaY)) return true;
            return super.mouseDragged(event, deltaX, deltaY);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            if (this.showBuy && this.buyList.isMouseOver(mouseX, mouseY) && this.buyList.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) return true;
            if (this.showSell && this.sellList.isMouseOver(mouseX, mouseY) && this.sellList.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) return true;
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
    }
}
