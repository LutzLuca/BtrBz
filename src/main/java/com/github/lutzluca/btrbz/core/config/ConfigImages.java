package com.github.lutzluca.btrbz.core.config;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.widgets.WidgetId;
import dev.isxander.yacl3.api.OptionDescription;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/** Config-screen images with their native dimensions and widget associations. */
public enum ConfigImages {
    PRICE_ALERT("alert-registration-and-firing.png", 658, 202),
    PRICE_DIFF("price-diff.png", 693, 390),
    BOOKMARKS("bookmarks.png", 575, 323),
    FLIP_HELPER("flip-helper.png", 994, 654),
    ORDER_BOOK_SCREEN("order-book-screen.png", 1506, 847),
    ORDER_BOOK_SIGN("order-book-sign.png", 1027, 577),
    ORDER_LIMIT("order-limit.png", 597, 448),
    ORDER_NOTIFICATION("order-notifications.png", 661, 235),
    ORDER_PRESETS("order-presets.png", 661, 372),
    ORDER_PROTECTION("order-protection-blocking.png", 1092, 614),
    ORDER_STATUS("order-status-highlight.png", 423, 238),
    ORDER_TOOLTIP("order-tooltip.png", 542, 305),
    ORDER_VALUE_OVERVIEW("order-value-overview.png", 674, 379),
    PRODUCT_INFO_PAPER("product-info-paper.png", 622, 350),
    PRODUCT_INFO("product-info.png", 588, 218),
    REOPEN_LAST_ORDER("reopen-last-order.png", 578, 325),
    TRACKED_ORDER_TOOLTIPS("tracked-order-tooltips.png", 683, 384),
    TRACKED_ORDERS_BAZAAR("tracked-orders-bazaar.png", 628, 353),
    TRACKED_ORDERS_HUD("tracked-orders-hud.png", 553, 311),
    WIDGET_MANAGER_BUTTON("widget-manager-button.png", 277, 156);

    private final Identifier identifier;
    private final int width;
    private final int height;

    ConfigImages(String fileName, int width, int height) {
        this.identifier = Identifier.fromNamespaceAndPath(
            BtrBz.MOD_ID,
            "textures/gui/config/" + fileName
        );
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
            case "btrbz:bazaar_orders" -> TRACKED_ORDERS_HUD;
            case "btrbz:tracked_orders_list" -> TRACKED_ORDERS_BAZAAR;
            case "btrbz:order_value" -> ORDER_VALUE_OVERVIEW;
            case "btrbz:order_book" -> ORDER_BOOK_SCREEN;
            case "btrbz:order_book_price" -> ORDER_BOOK_SIGN;
            case "btrbz:bookmarks" -> BOOKMARKS;
            case "btrbz:order_presets" -> ORDER_PRESETS;
            case "btrbz:order_limit" -> ORDER_LIMIT;
            case "btrbz:price_diff" -> PRICE_DIFF;
            default -> null;
        };
    }
}
