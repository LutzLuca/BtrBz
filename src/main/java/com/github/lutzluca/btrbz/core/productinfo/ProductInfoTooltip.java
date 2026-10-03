package com.github.lutzluca.btrbz.core.productinfo;

import com.github.lutzluca.btrbz.utils.Utils;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
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
            return Component.literal(source.unavailableLabel()).withStyle(ChatFormatting.DARK_GRAY);
        }
        var count = quantity.count().getAsInt();
        if (count <= 1) {
            return null;
        }
        var formattedCount = Utils.formatDecimal(count, 0, true);
        if (shiftHeld) {
            var detail = source == ProductInfoQuantity.Source.ORDER ? ", full order" : "";
            return Component.literal(source.totalLabel() + " (").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(formattedCount).withStyle(ChatFormatting.LIGHT_PURPLE))
                .append(Component.literal(" " + source.unit() + detail + ")").withStyle(ChatFormatting.GRAY));
        }
        return Component.literal("Hold ").withStyle(ChatFormatting.DARK_GRAY)
            .append(Component.literal("SHIFT").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
            .append(Component.literal(" for " + source.totalLabel().toLowerCase(Locale.ROOT) + " (")
                .withStyle(ChatFormatting.DARK_GRAY))
            .append(Component.literal(formattedCount).withStyle(ChatFormatting.LIGHT_PURPLE))
            .append(Component.literal(" " + source.unit() + ")").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static Component priceText(String label, @Nullable Double price, int count, boolean showTotal) {
        var text = Component.literal(label).withStyle(ChatFormatting.AQUA);
        if (price == null) {
            return text.append(Component.literal("Not Available").withStyle(ChatFormatting.GRAY));
        }
        var displayPrice = showTotal ? price * count : price;
        text.append(Component.literal(Utils.formatDecimal(displayPrice, 1, true) + " coins")
            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        if (showTotal) {
            text.append(Component.literal(" (" + Utils.formatDecimal(count, 0, true) + "x)")
                .withStyle(ChatFormatting.DARK_GRAY));
        }
        return text;
    }

    record Prices(@Nullable Double buy, @Nullable Double sell) {}
}
