package com.github.lutzluca.btrbz.core.productinfo;

import com.github.lutzluca.btrbz.core.ui.UiStyles;

import com.github.lutzluca.btrbz.utils.Utils;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

final class ProductInfoTooltip {

    private ProductInfoTooltip() {}

    static void append(List<Component> lines, Prices prices, ProductInfoQuantity quantity, boolean shiftHeld) {
        lines.add(Component.empty());
        var hint = quantityHint(quantity, shiftHeld);
        if (hint != null) {
            lines.add(hint);
        }
        var count = quantity.count().orElse(1);
        var showTotal = shiftHeld && count > 1;
        lines.add(priceText("Buy Price: ", prices.buy(), count, showTotal));
        lines.add(priceText("Sell Price: ", prices.sell(), count, showTotal));
    }

    private static @Nullable Component quantityHint(ProductInfoQuantity quantity, boolean shiftHeld) {
        var source = quantity.source();
        if (quantity.count().isEmpty()) {
            return Component.literal(source.unavailableLabel()).withStyle(UiStyles.muted());
        }
        var count = quantity.count().getAsInt();
        if (count <= 1) {
            return null;
        }
        var formattedCount = Utils.formatDecimal(count, 0, true);
        if (shiftHeld) {
            var detail = source == ProductInfoQuantity.Source.ORDER ? ", full order" : "";
            return Component.literal(source.totalLabel() + " (").withStyle(UiStyles.label())
                .append(Component.literal(formattedCount).withStyle(UiStyles.quantity()))
                .append(Component.literal(" " + source.unit() + detail + ")").withStyle(UiStyles.label()));
        }
        return Component.literal("Hold ").withStyle(UiStyles.muted())
            .append(Component.literal("SHIFT").withStyle(UiStyles.key()))
            .append(Component.literal(" for " + source.totalLabel().toLowerCase(Locale.ROOT) + " (")
                .withStyle(UiStyles.muted()))
            .append(Component.literal(formattedCount).withStyle(UiStyles.quantity()))
            .append(Component.literal(" " + source.unit() + ")").withStyle(UiStyles.muted()));
    }

    private static Component priceText(String label, @Nullable Double price, int count, boolean showTotal) {
        var text = Component.literal(label).withStyle(UiStyles.label());
        if (price == null) {
            return text.append(Component.literal("Not Available").withStyle(UiStyles.label()));
        }
        var displayPrice = showTotal ? price * count : price;
        text.append(UiStyles.coins(displayPrice));
        if (showTotal) {
            text.append(Component.literal(" (").withStyle(UiStyles.muted()))
                .append(Component.literal(Utils.formatDecimal(count, 0, true)).withStyle(UiStyles.quantity()))
                .append(Component.literal("x)").withStyle(UiStyles.muted()));
        }
        return text;
    }

    record Prices(@Nullable Double buy, @Nullable Double sell) {}
}
