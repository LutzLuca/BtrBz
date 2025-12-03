package com.github.lutzluca.btrbz.core.order_book;

import com.github.lutzluca.btrbz.data.BazaarData.OrderLists;
import com.github.lutzluca.btrbz.utils.Utils;
import java.util.ArrayList;
import java.util.List;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.widgets.StaticListWidget;
import java.util.Objects;
import lombok.Getter;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply.Product.Summary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class OrderBookScreen extends Screen {

    private final Screen parent;
    private final OrderLists orders;

    private StaticListWidget<OrderEntryWidget> buyOrderList;
    private StaticListWidget<OrderEntryWidget> sellOfferList;


    public OrderBookScreen(Screen parent, String productName, OrderLists orders) {
        super(Component.literal(productName + " Order Book"));
        this.parent = parent;
        this.orders = orders;
    }

    @Override
    protected void init() {
        int width = this.width;
        int height = this.height;

        int panelWidth = (int) (width * 0.7);
        int panelHeight = (int) (height * 0.8);
        int panelX = (width - panelWidth) / 2;
        int panelY = (height - panelHeight) / 2;

        int listWidth = (panelWidth - 30) / 2;
        int listHeight = panelHeight - 60;

        int buyX = panelX + 10;
        int listY = panelY + 40;

        this.buyOrderList = new StaticListWidget<OrderEntryWidget>(
            buyX,
            listY,
            listWidth,
            listHeight,
            Component.literal("Buy Orders"),
            this
        )
            .setMaxVisibleChildren(Math.min(15, this.orders.buyOrders().size()))
            .onChildClick(this::onBuyOrderClick);
        this.buyOrderList.setDragThreshold(Integer.MAX_VALUE);

        int sellX = buyX + listWidth + 10;

        this.sellOfferList = new StaticListWidget<OrderEntryWidget>(
            sellX,
            listY,
            listWidth,
            listHeight,
            Component.literal("Sell Offers"),
            this
        )
            .setMaxVisibleChildren(Math.min(15, this.orders.sellOffers().size()))
            .onChildClick(this::onSellOfferClick);
        this.sellOfferList.setDragThreshold(Integer.MAX_VALUE);

        this.rebuildLists();

        this.addRenderableWidget(buyOrderList);
        this.addRenderableWidget(sellOfferList);
    }

    private void rebuildLists() {
        List<OrderEntryWidget> buyWidgets = new ArrayList<>();
        for (var summary : this.orders.buyOrders()) {
            buyWidgets.add(new OrderEntryWidget(summary, OrderType.Buy, 0, 0, 50, 14));
        }

        List<OrderEntryWidget> sellWidgets = new ArrayList<>();
        for (var summary : this.orders.sellOffers()) {
            sellWidgets.add(new OrderEntryWidget(summary, OrderType.Sell, 0, 0, 50, 14));
        }

        this.buyOrderList.rebuildEntries(buyWidgets);
        this.sellOfferList.rebuildEntries(sellWidgets);
    }

    private void onBuyOrderClick(OrderEntryWidget widget, Integer index) {
        copyPriceToClipboard(widget.getSummary().getPricePerUnit());
        this.onClose();
    }

    private void onSellOfferClick(OrderEntryWidget widget, Integer index) {
        copyPriceToClipboard(widget.getSummary().getPricePerUnit());
        this.onClose();
    }

    private void copyPriceToClipboard(double price) {
        Minecraft.getInstance().keyboardHandler.setClipboard(String.format("%.1f", price));
    }

    @Override
    public void onClose() {
        Objects.requireNonNull(this.minecraft).setScreen(parent);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0x80000000);

        context.drawCenteredString(
            this.font,
            this.title,
            this.width / 2,
            (this.buyOrderList.getY() - 30),
            0xFFFFFF
        );

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public class OrderEntryWidget extends AbstractWidget {

        @Getter
        private final Summary summary;
        private final OrderType type;

        public OrderEntryWidget(
            Summary summary,
            OrderType type,
            int x,
            int y,
            int width,
            int height
        ) {
            super(x, y, width, height, Component.empty());
            this.summary = summary;
            this.type = type;
        }

        @Override
        protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
            var textRenderer = Minecraft.getInstance().font;

            if (this.isHovered()) {
                context.fill(
                    this.getX(),
                    this.getY(),
                    this.getX() + this.width,
                    this.getY() + this.height,
                    type == OrderType.Buy ? 0x6022AA22 : 0x60AA2222
                );
            }

            String price = Utils.formatDecimal(this.summary.getPricePerUnit(), 1, true);
            String amount = String.valueOf(this.summary.getAmount());
            String ordersInfo = "(" + this.summary.getOrders() + " orders)";

            int textColor = type == OrderType.Buy ? 0xFF55FF55 : 0xFFFF5555;
            int yPos = this.getY() + (this.height - textRenderer.lineHeight) / 2;

            context.drawString(textRenderer, price, this.getX() + 5, yPos, textColor);

            int ordersWidth = textRenderer.width(ordersInfo);
            context.drawString(
                textRenderer,
                ordersInfo,
                this.getX() + this.width - ordersWidth - 5,
                yPos,
                0xFF888888
            );

            int amountWidth = textRenderer.width(amount);
            context.drawString(
                textRenderer,
                amount,
                this.getX() + this.width - ordersWidth - amountWidth - 12,
                yPos,
                0xFFCCCCCC
            );
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput builder) {
            this.defaultButtonNarrationText(builder);
        }
    }
}