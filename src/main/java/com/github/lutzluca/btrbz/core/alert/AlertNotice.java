package com.github.lutzluca.btrbz.core.alert;

import com.github.lutzluca.btrbz.core.ui.UiStyles;

import com.github.lutzluca.btrbz.core.alert.AlertCondition.LiquiditySide;
import com.github.lutzluca.btrbz.data.IndexedProduct;
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
                .append(label("Reached at "))
                .append(UiStyles.coins(price.value()));
            case AlertCondition.Observation.Liquidity liquidity -> label("Observed: ")
                .append(Component.literal(items(liquidity.quantity())).withStyle(UiStyles.quantity()))
                .append(label(" qualifying items"));
        };
        return new AlertNotice(product.formattedName(), reached.alert().kind().name() + " target reached",
            conditionText(condition), observation);
    }

    public static MutableComponent conditionText(AlertCondition condition) {
        return switch (condition) {
            case AlertCondition.Price price -> Component.empty()
                .append(Component.literal(price.type().source().label()).withStyle(UiStyles.label()))
                .append(label(" " + price.type().direction().symbol() + " "))
                .append(UiStyles.coins(price.price()));
            case AlertCondition.Liquidity liquidity -> Component.empty()
                .append(label("Instantly "))
                .append(Component.literal(liquidity.side() == LiquiditySide.BuyOrders ? "sell " : "buy ")
                    .withStyle(UiStyles.color(liquidity.side() == LiquiditySide.BuyOrders
                        ? UiStyles.palette().buy() : UiStyles.palette().sell())))
                .append(Component.literal(items(liquidity.quantity())).withStyle(UiStyles.quantity()))
                .append(label(" items at " + (liquidity.side() == LiquiditySide.BuyOrders ? "≥ " : "≤ ")))
                .append(UiStyles.coins(liquidity.priceBound()))
                .append(label(" each"));
        };
    }

    private static MutableComponent label(String text) {
        return Component.literal(text).withStyle(UiStyles.label());
    }

    private static String items(long quantity) {
        return NumberFormat.getIntegerInstance(Locale.US).format(quantity);
    }
}
