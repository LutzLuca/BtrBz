package com.github.lutzluca.btrbz.core.alert;

import com.github.lutzluca.btrbz.core.ui.UiStyles;
import com.github.lutzluca.btrbz.core.appearance.UiColor;

import com.github.lutzluca.btrbz.core.alert.AlertCondition.LiquiditySide;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import com.github.lutzluca.btrbz.utils.Utils;
import java.text.NumberFormat;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Presentation of the saved condition and the observation that triggered it. */
public record AlertNotice(String formattedProductName, String title, Component condition, Component observation) {
    public static AlertNotice from(ReachedAlert reached, IndexedProduct product) {
        var condition = reached.alert().condition;
        var observation = switch (reached.observation()) {
            case AlertCondition.Observation.Price price -> Component.empty()
                .append(label("Triggered at "))
                .append(coins(price.value()));
            case AlertCondition.Observation.Liquidity liquidity -> label("Triggered with ")
                .append(Component.literal(items(liquidity.quantity())).withStyle(UiStyles.quantity()))
                .append(label(" items available"));
        };
        return new AlertNotice(product.formattedName(), reached.alert().kind().name() + " target reached",
            conditionText(condition), observation);
    }

    public static MutableComponent conditionText(AlertCondition condition) {
        return switch (condition) {
            case AlertCondition.Price price -> Component.empty()
                .append(label(price.type().source() == AlertType.PriceSource.Buy ? "Buy price" : "Sell price"))
                .append(label(" " + price.type().direction().symbol() + " "))
                .append(coins(price.price()));
            case AlertCondition.Liquidity liquidity -> Component.empty()
                .append(Component.literal(liquidity.side() == LiquiditySide.BuyOrders ? "Sell " : "Buy ")
                    .withStyle(UiStyles.color(liquidity.side() == LiquiditySide.BuyOrders
                        ? UiColor.Sell : UiColor.Buy)))
                .append(Component.literal(items(liquidity.quantity())).withStyle(UiStyles.quantity()))
                .append(label(" items instantly for "
                    + (liquidity.side() == LiquiditySide.BuyOrders ? "at least " : "up to ")))
                .append(coins(liquidity.priceBound()))
                .append(label(" each"));
        };
    }

    private static MutableComponent label(String text) {
        return Component.literal(text).withStyle(UiStyles.primary());
    }

    static MutableComponent coins(double value) {
        return Component.literal(formatPrice(value) + " coins")
            .withStyle(UiStyles.money());
    }

    static String formatPrice(double value) {
        String formatted = Utils.formatDecimal(value, 1, true);
        return formatted.endsWith(".0") ? formatted.substring(0, formatted.length() - 2) : formatted;
    }

    private static String items(long quantity) {
        return NumberFormat.getIntegerInstance(Locale.US).format(quantity);
    }
}
