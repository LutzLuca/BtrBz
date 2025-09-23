package com.github.lutzluca.btrbz;

import com.github.lutzluca.btrbz.TrackedOrder.OrderInfo;
import com.github.lutzluca.btrbz.TrackedOrder.OrderType;
import io.vavr.control.Try;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

public final class OrderParser {

    private OrderParser() { }


    public static Optional<OrderInfo> parseOrder(ItemStack item) {
        var orderInfo = item.getName().getString().split(" ", 2);
        if (orderInfo.length != 2) {
            Notifier.logInfo(
                "Title line of item does not follow the pattern '<orderType> <productName>'");
            return Optional.empty();
        }

        var orderTypeResult = OrderType.tryFrom(orderInfo[0]);
        var productName = orderInfo[1];
        if (orderTypeResult.isFailure()) {
            Notifier.logInfo("Failed to parse Order type", orderTypeResult.getCause());
            return Optional.empty();
        }

        var lore = getLore(item);
        var additionalInfoOpt = getAdditionalOrderInfo(lore);
        if (additionalInfoOpt.isEmpty()) {
            Notifier.logInfo(
                "Failed to parse out the additional Order info from the lore of the item");
            return Optional.empty();
        }

        var info = additionalInfoOpt.get();
        return Optional.of(new OrderInfo(productName.trim(), orderTypeResult.get(), info.volume(),
            info.pricePerUnit(),
            info.filled()
        ));
    }

    private static Optional<OrderDetails> getAdditionalOrderInfo(List<String> lore) {
        Double pricePerUnit = null;
        Integer volume = null;
        Boolean filled = null;

        for (String rawLine : lore) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }

            if (pricePerUnit == null && line.startsWith("Price per unit:")) {
                var parsed = parseNumber(
                    line.replace("Price per unit:", "")
                        .replace("coins", "")
                        .trim()
                );

                if (parsed.isFailure()) {
                    Notifier.logInfo("Failed to parse pricePerUnit", parsed.getCause());
                    return Optional.empty();
                }
                pricePerUnit = parsed.get().doubleValue();
            } else if (volume == null && (line.startsWith("Order amount:") || line.startsWith(
                "Offer amount:"))) {
                var parsed = parseNumber(
                    line.replace("Order amount:", "")
                        .replace("Offer amount:", "")
                        .replaceAll("x.*", "")
                        .trim()
                );

                if (parsed.isFailure()) {
                    Notifier.logInfo("Failed to parse volume", parsed.getCause());
                    return Optional.empty();
                }
                volume = parsed.get().intValue();
            } else if (filled == null && line.startsWith("Filled") && line.contains("%")) {
                filled = line.contains("100%");
            }

            if (pricePerUnit != null && volume != null && filled != null) {
                break;
            }
        }

        if (pricePerUnit != null && volume != null) {
            return Optional.of(new OrderDetails(pricePerUnit, volume, filled != null && filled));
        }
        return Optional.empty();
    }

    private static Try<Number> parseNumber(String str) {
        var nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setParseIntegerOnly(false);
        return Try.of(() -> nf.parse(str.trim()));
    }

    private static List<String> getLore(ItemStack item) {
        return Optional
            .ofNullable(item.get(DataComponentTypes.LORE))
            .map(LoreComponent::lines)
            .orElseGet(ArrayList::new)
            .stream()
            .map(Text::getString)
            .toList();
    }

    private record OrderDetails(double pricePerUnit, int volume, boolean filled) { }
}
