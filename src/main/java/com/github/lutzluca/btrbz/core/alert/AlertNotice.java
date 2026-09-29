package com.github.lutzluca.btrbz.core.alert;

import com.github.lutzluca.btrbz.core.alert.AlertCondition.LiquiditySide;
import com.github.lutzluca.btrbz.core.alert.AlertType.PriceSource;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarStyles;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import com.github.lutzluca.btrbz.utils.Utils;
import java.text.NumberFormat;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Presentation of the saved condition and the observation that triggered it. */
public record AlertNotice(String formattedProductName, String title, Component condition, Component observation) {
    public static AlertNotice from(ReachedAlert reached, IndexedProduct product) {
        var condition = reached.alert().condition;
        var observation = switch (reached.observation()) {
            case AlertCondition.Observation.Price price -> Component.empty()
                .append(muted("Reached at "))
                .append(coins(price.value()));
            case AlertCondition.Observation.Liquidity liquidity -> muted("Observed: "
                + items(liquidity.quantity()) + " qualifying items");
        };
        return new AlertNotice(product.formattedName(), reached.alert().kind().name() + " target reached",
            conditionText(condition), observation);
    }

    public static MutableComponent conditionText(AlertCondition condition) {
        return switch (condition) {
            case AlertCondition.Price price -> Component.empty()
                .append(Component.literal(price.type().source().label())
                    .withColor(price.type().source() == PriceSource.Buy
                        ? BazaarStyles.BUY_ACCENT : BazaarStyles.SELL_ACCENT))
                .append(muted(" " + price.type().direction().symbol() + " "))
                .append(coins(price.price()));
            case AlertCondition.Liquidity liquidity -> Component.empty()
                .append(muted("Instantly "))
                .append(Component.literal(liquidity.side() == LiquiditySide.BuyOrders ? "sell " : "buy ")
                    .withColor(liquidityAccent(liquidity.side())))
                .append(muted(items(liquidity.quantity()) + " items at "
                    + (liquidity.side() == LiquiditySide.BuyOrders ? "≥ " : "≤ ")))
                .append(coins(liquidity.priceBound()))
                .append(muted(" each"));
        };
    }

    public static MutableComponent coins(double value) {
        return Component.literal(Utils.formatDecimal(value, 1, true) + " coins").withStyle(ChatFormatting.GOLD);
    }

    private static MutableComponent muted(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GRAY);
    }

    private static int liquidityAccent(LiquiditySide side) {
        return side == LiquiditySide.BuyOrders ? BazaarStyles.BUY_ACCENT : BazaarStyles.SELL_ACCENT;
    }

    private static String items(long quantity) {
        return NumberFormat.getIntegerInstance(Locale.US).format(quantity);
    }
}
