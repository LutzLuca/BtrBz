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
        return new AlertNotice(product.formattedName(), reached.alert().kind().name() + " target reached",
            conditionText(reached.alert().condition), triggeredText(reached.observation()));
    }

    public static MutableComponent triggeredText(AlertCondition.Observation observation) {
        return switch (observation) {
            case AlertCondition.Observation.Price price -> Component.empty()
                .append(label("Triggered at "))
                .append(coins(price.value()));
            case AlertCondition.Observation.Liquidity liquidity -> label("Triggered with ")
                .append(Component.literal(items(liquidity.quantity())).withStyle(UiStyles.quantity()))
                .append(label(" items available"));
        };
    }

    public static MutableComponent conditionText(AlertCondition condition) {
        return switch (condition) {
            case AlertCondition.Price price -> Component.empty()
                .append(label(price.type().source() == AlertType.PriceSource.Buy ? "Buy price" : "Sell price"))
                .append(label(" " + price.type().direction().symbol() + " "))
                .append(coins(price.price()));
            case AlertCondition.Liquidity liquidity -> Component.empty()
                .append(
                    Component.literal(liquidity.side() == LiquiditySide.BuyOrders ? "Instant sell " : "Instant buy ")
                        .withStyle(UiStyles.color(liquidity.side() == LiquiditySide.BuyOrders
                            ? UiColor.Sell : UiColor.Buy)))
                .append(Component.literal(items(liquidity.quantity())).withStyle(UiStyles.quantity()))
                .append(label(" items at "
                    + (liquidity.side() == LiquiditySide.BuyOrders ? "≥ " : "≤ ")))
                .append(coins(liquidity.priceBound()));
        };
    }

    public static MutableComponent currentText(AlertCondition condition, AlertCondition.Observation observation) {
        return label(condition.isReached(observation) ? "Still met: " : "No longer met: ")
            .append(valueText(observation));
    }

    public static MutableComponent valueText(AlertCondition.Observation observation) {
        return switch (observation) {
            case AlertCondition.Observation.Price price -> coins(price.value());
            case AlertCondition.Observation.Liquidity liquidity ->
                label(items(liquidity.quantity()) + " items available");
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

    static String items(long quantity) {
        return NumberFormat.getIntegerInstance(Locale.US).format(quantity);
    }
}
