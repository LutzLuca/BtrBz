package com.github.lutzluca.btrbz.core.order_book;

import com.github.lutzluca.btrbz.data.BazaarData.OrderLists;
import com.github.lutzluca.btrbz.utils.Utils;
import java.util.ArrayList;
import java.util.List;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.widgets.core.WidgetManager;
import com.github.lutzluca.btrbz.widgets.ListWidget;
import com.github.lutzluca.btrbz.widgets.Renderable;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply.Product.Summary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class OrderBookScreen extends Screen {

    private final Screen parent;
    private final OrderLists orders;

    private WidgetManager widgetManager;


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

        ListWidget buyOrderList = new ListWidget(
            buyX,
            listY,
            listWidth,
            listHeight,
            "Buy Orders"
        );
        buyOrderList.setReorderable(false)
            .setRemovable(false)
            .setItemHeight(14)
            .setDraggable(false);

        buyOrderList.onItemClick((self, item, idx) -> {
            copyPriceToClipboard(((OrderBookRenderable) item).getPricePerUnit());
            this.onClose();
        });

        int sellX = buyX + listWidth + 10;

        ListWidget sellOfferList = new ListWidget(
            sellX,
            listY,
            listWidth,
            listHeight,
            "Sell Offers"
        );
        sellOfferList.setReorderable(false)
            .setRemovable(false)
            .setItemHeight(14)
            .setDraggable(false);

        sellOfferList.onItemClick((self, item, idx) -> {
            copyPriceToClipboard(((OrderBookRenderable) item).getPricePerUnit());
            this.onClose();
        });

        List<Renderable> buyWidgets = new ArrayList<>();
        for (var summary : this.orders.buyOrders()) {
            buyWidgets.add(new OrderBookRenderable(summary, OrderType.Buy));
        }

        List<Renderable> sellWidgets = new ArrayList<>();
        for (var summary : this.orders.sellOffers()) {
            sellWidgets.add(new OrderBookRenderable(summary, OrderType.Sell));
        }

        buyOrderList.setItems(buyWidgets);
        sellOfferList.setItems(sellWidgets);

        this.widgetManager = new WidgetManager(List.of(buyOrderList, sellOfferList));
        this.widgetManager.init();
    }

    private void copyPriceToClipboard(double price) {
        Minecraft.getInstance().keyboardHandler.setClipboard(Utils.formatDecimal(price, 1, false));
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0x80000000);

        int listY = (this.height - (int) (this.height * 0.8)) / 2 + 40;

        context.drawString(
            this.font,
            this.title,
            (this.width - this.font.width(this.title)) / 2,
            listY - 30,
            0xFFFFFF
        );

        this.widgetManager.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.widgetManager.mouseClicked(event, doubleClick)) return true;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return this.widgetManager.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
            || super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.widgetManager.mouseReleased(event);
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.widgetManager.mouseDragged(event, dragX, dragY)) return true;
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public static class OrderBookRenderable implements Renderable {
        private final Summary summary;
        private final OrderType type;
        private final Component priceText;
        private final Component amountText;
        private final Component ordersText;

        public OrderBookRenderable(Summary summary, OrderType type) {
            this.summary = summary;
            this.type = type;
            this.priceText = Component.literal(Utils.formatDecimal(summary.getPricePerUnit(), 1, true));
            this.amountText = Component.literal(String.valueOf(summary.getAmount()));
            this.ordersText = Component.literal("(" + summary.getOrders() + " orders)");
        }

        @Override
        public void render(
            GuiGraphics ctx,
            int x, int y, int width, int height,
            int mouseX, int mouseY, float delta,
            boolean hovered
        ) {
            var font = Minecraft.getInstance().font;

            if (hovered) {
                int color = type == OrderType.Buy ? 0x6022AA22 : 0x60AA2222;
                ctx.fill(x, y, x + width, y + height, color);
            }

            int textColor = type == OrderType.Buy ? 0xFF55FF55 : 0xFFFF5555;
            int yPos = y + (height - font.lineHeight) / 2;

            ctx.drawString(font, this.priceText, x + 5, yPos, textColor);

            int ordersWidth = font.width(this.ordersText);
            ctx.drawString(font, this.ordersText, x + width - ordersWidth - 5, yPos, 0xFF888888);

            int amountWidth = font.width(this.amountText);
            ctx.drawString(font, this.amountText, x + width - ordersWidth - amountWidth - 12, yPos, 0xFFCCCCCC);
        }

        public double getPricePerUnit() {
            return this.summary.getPricePerUnit();
        }
    }
}
