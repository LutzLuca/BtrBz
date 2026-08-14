package com.github.lutzluca.btrbz.core.config;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.widgets.WidgetId;
import dev.isxander.yacl3.api.OptionDescription;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/** Config-screen images with their native dimensions and widget associations. */
public enum ConfigImages {
    PriceAlert("alert-registration-and-firing.png", 658, 202),
    PriceDiff("price-diff.png", 693, 390),
    Bookmarks("bookmarks.png", 575, 323),
    FlipHelper("flip-helper.png", 994, 654),
    OrderBookScreen("order-book-screen.png", 1506, 847),
    OrderBookSign("order-book-sign.png", 1027, 577),
    OrderLimit("order-limit.png", 597, 448),
    OrderNotification("order-notifications.png", 661, 235),
    OrderPresets("order-presets.png", 661, 372),
    OrderProtection("order-protection-blocking.png", 1092, 614),
    OrderStatus("order-status-highlight.png", 423, 238),
    OrderTooltip("order-tooltip.png", 542, 305),
    OrderValueOverView("order-value-overview.png", 674, 379),
    ProductInfoPaper("product-info-paper.png", 622, 350),
    ProductInfo("product-info.png", 588, 218),
    ReopenLastOrder("reopen-last-order.png", 578, 325),
    TrackedOrderTooltips("tracked-order-tooltips.png", 683, 384),
    TrackedOrdersBazaar("tracked-orders-bazaar.png", 628, 353),
    TrackedOrdersHud("tracked-orders-hud.png", 553, 311),
    WidgetManagerButton("widget-manager-button.png", 277, 156);

    private final Identifier identifier;
    private final int width;
    private final int height;

    ConfigImages(String fileName, int width, int height) {
        this.identifier = Identifier.fromNamespaceAndPath(
            BtrBz.MOD_ID,
            "textures/gui/config/" + fileName);
        this.width = width;
        this.height = height;
    }

    public OptionDescription description(Component text) {
        return OptionDescription.createBuilder()
            .text(text)
            .image(this.identifier, this.width, this.height)
            .build();
    }

    public static @Nullable ConfigImages forWidget(WidgetId id) {
        return switch (id.toString()) {
            case "btrbz:bazaar_orders" -> ConfigImages.TrackedOrdersHud;
            case "btrbz:tracked_orders_list" -> ConfigImages.TrackedOrdersBazaar;
            case "btrbz:order_value" -> ConfigImages.OrderValueOverView;
            case "btrbz:order_book" -> ConfigImages.OrderBookScreen;
            case "btrbz:order_book_price" -> ConfigImages.OrderBookSign;
            case "btrbz:bookmarks" -> ConfigImages.Bookmarks;
            case "btrbz:order_presets" -> ConfigImages.OrderPresets;
            case "btrbz:order_limit" -> ConfigImages.OrderLimit;
            case "btrbz:price_diff" -> ConfigImages.PriceDiff;
            default -> null;
        };
    }
}
