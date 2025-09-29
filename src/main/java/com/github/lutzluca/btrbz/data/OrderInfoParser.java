package com.github.lutzluca.btrbz.data;

import com.github.lutzluca.btrbz.data.OrderModels.ChatFilledOrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.ChatOrderConfirmationInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.OrderModels.OutstandingOrderInfo;
import com.github.lutzluca.btrbz.utils.Util;
import io.vavr.control.Try;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

public final class OrderInfoParser {

    private OrderInfoParser() { }

    public static Try<OrderInfo> parseOrderInfo(ItemStack item, int slotIdx) {
        // name: {orderType} {productName}
        // lore lines:
        // Worth {roundedFormattedTotal} coins
        // Blank
        // Order amount / Offer amount: {volume}x
        // Optional: Filled: {filledAmount}/{volume} {filledPercentage}%!
        // Blank
        // Price per unit: {pricePerUnit} coins
        // ...
        return Try.of(() -> {
            var orderInfo = item.getName().getString().split(" ", 2);
            if (orderInfo.length != 2) {
                throw new IllegalArgumentException(
                    "Title line of item does not follow the pattern '<orderType> <productName>'");
            }

            var orderTypeResult = OrderType.tryFrom(orderInfo[0]);
            if (orderTypeResult.isFailure()) {
                throw new IllegalArgumentException(
                    "Failed to parse Order type: " + orderTypeResult.getCause().getMessage());
            }

            var productName = orderInfo[1];
            var lore = getLore(item);
            var additionalInfo = getAdditionalOrderInfo(lore);
            if (additionalInfo.isFailure()) {
                throw new IllegalArgumentException(
                    "Failed to parse out the additional Order info from the lore of the item",
                    additionalInfo.getCause()
                );
            }

            var details = additionalInfo.get();
            return new OrderInfo(
                productName.trim(), orderTypeResult.get(), details.volume(),
                details.pricePerUnit(), details.filled(), slotIdx
            );
        });
    }

    private static Try<OrderDetails> getAdditionalOrderInfo(List<String> lore) {
        return Try.of(() -> {
            Double pricePerUnit = null;
            Integer volume = null;
            Boolean filled = null;

            for (String rawLine : lore) {
                String line = rawLine.trim();
                if (line.isEmpty()) {
                    continue;
                }

                if (pricePerUnit == null && line.startsWith("Price per unit:")) {
                    var parsed = Util.parseUsFormattedNumber(
                        line.replace("Price per unit:", "").replace("coins", "").trim());
                    pricePerUnit = parsed
                        .getOrElseThrow(
                            () -> new IllegalArgumentException("Failed to parse pricePerUnit"))
                        .doubleValue();
                } else if (volume == null && (line.startsWith("Order amount:") || line.startsWith(
                    "Offer amount:"))) {
                    var parsed = Util.parseUsFormattedNumber(line
                        .replace("Order amount:", "")
                        .replace("Offer amount:", "")
                        .replaceAll("x.*", "")
                        .trim());
                    volume = parsed
                        .getOrElseThrow(
                            () -> new IllegalArgumentException("Failed to parse volume"))
                        .intValue();
                } else if (filled == null && line.startsWith("Filled") && line.contains("%")) {
                    filled = line.contains("100%");
                }

                if (pricePerUnit != null && volume != null && filled != null) {
                    break;
                }
            }

            if (pricePerUnit == null || volume == null) {
                throw new IllegalArgumentException(
                    "Missing required fields (pricePerUnit or volume) in lore");
            }

            return new OrderDetails(pricePerUnit, volume, filled != null && filled);
        });
    }

    public static Try<OutstandingOrderInfo> parseSetOrderItem(ItemStack item) {
        // name: Buy Order / Sell Offer
        // lore lines:
        // Bazaar
        // Blank
        // Price per unit: {pricePerUnit} coins
        // Blank
        // Order / Selling: {volume}x {productName}
        // Total price / You earn: {total} coins.
        // ...
        return Try.of(() -> {
            if (item == null || item.isEmpty()) {
                throw new IllegalArgumentException("Empty item");
            }

            String title = item.getName().getString();
            var type = switch (title) {
                case "Sell Offer" -> OrderType.Sell;
                case "Buy Order" -> OrderType.Buy;
                default -> throw new IllegalArgumentException("Unknown confirm title: " + title);
            };

            var lore = getLore(item);
            Double pricePerUnit = null;
            Integer volume = null;
            String productName = null;
            Double total = null;

            for (String rawLine : lore) {
                String line = rawLine.trim();
                if (line.isEmpty()) {
                    continue;
                }

                if (pricePerUnit == null && line.startsWith("Price per unit:")) {
                    var parsed = Util.parseUsFormattedNumber(
                        line.replace("Price per unit:", "").replace("coins", "").trim());
                    if (parsed.isFailure()) {
                        throw new IllegalArgumentException(
                            "Failed to parse pricePerUnit: " + parsed.getCause().getMessage());
                    }
                    pricePerUnit = parsed.get().doubleValue();
                } else if (volume == null && (line.startsWith("Order:") || line.startsWith(
                    "Selling:"))) {
                    var part = line.substring(line.indexOf(":") + 1).trim();
                    int xIdx = part.indexOf('x');
                    if (xIdx <= 0) {
                        throw new IllegalArgumentException("Invalid volume line: " + line);
                    }
                    var volStr = part.substring(0, xIdx).trim();
                    var parsed = Util.parseUsFormattedNumber(volStr);
                    if (parsed.isFailure()) {
                        throw new IllegalArgumentException(
                            "Failed to parse volume: " + parsed.getCause().getMessage());
                    }
                    volume = parsed.get().intValue();
                    productName = part.substring(xIdx + 1).trim();
                } else if (total == null && (line.startsWith("Total price:") || line.startsWith(
                    "You earn:"))) {
                    var parsed = Util.parseUsFormattedNumber(line
                        .replace("Total price:", "")
                        .replace("You earn:", "")
                        .replace("coins", "")
                        .trim());
                    if (parsed.isFailure()) {
                        throw new IllegalArgumentException(
                            "Failed to parse total: " + parsed.getCause().getMessage());
                    }
                    total = parsed.get().doubleValue();
                }
            }

            if (pricePerUnit == null || volume == null || productName == null || total == null) {
                throw new IllegalArgumentException(
                    "Could not extract all required fields from confirm item");
            }

            return new OutstandingOrderInfo(productName, type, volume, pricePerUnit, total);
        });
    }

    public static Try<ChatFilledOrderInfo> parseFilledOrderInfo(String bazaarMsg) {
        // [Bazaar] Your Buy Order / Your Sell Offer for {volume}x {productName} was filled!
        return Try.of(() -> {
            var filledMsg = bazaarMsg.replace("[Bazaar]", "").trim();

            var parts = filledMsg.replace("Your", "").trim().split(" for ");
            if (parts.length != 2) {
                throw new IllegalArgumentException(
                    "Bazaar chat message does not follow the pattern: 'Your {order type} for <info>', msg" + bazaarMsg);

            }

            var type = switch (parts[0].trim()) {
                case "Buy Order" -> OrderType.Buy;
                case "Sell Offer" -> OrderType.Sell;
                default -> throw new IllegalArgumentException(
                    "Unexpected order type: '" + parts[0] + "', expected 'Buy Order Setup' or 'Sell Offer Setup'");
            };

            var xIdx = parts[1].indexOf("x");
            if (xIdx < 0) {
                throw new IllegalArgumentException(
                    "Failed to find an 'x' to denote the volume of the order");
            }

            var volume = Util
                .parseUsFormattedNumber(parts[1].substring(0, xIdx).trim())
                .get()
                .intValue();
            var productName = parts[1].substring(xIdx + 1).replace("was filled!", "").trim();

            return new ChatFilledOrderInfo(productName, type, volume);
        });
    }

    public static Try<ChatOrderConfirmationInfo> parseSetupChat(String bazaarChatMsg) {
        return Try.of(() -> {
            var confirmationMsg = bazaarChatMsg.replace("[Bazaar]", "").trim();
            var parts = confirmationMsg.split("!", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException(
                    "Bazaar chat message does not follow the pattern '<order type setup>! <...>'");
            }

            var typeStr = parts[0].trim();
            var type = switch (typeStr) {
                case "Buy Order Setup" -> OrderType.Buy;
                case "Sell Offer Setup" -> OrderType.Sell;
                default -> throw new IllegalArgumentException(
                    "Unexpected order type: '" + typeStr + "', expected 'Buy Order Setup' or 'Sell Offer Setup'");
            };

            var body = parts[1].trim();
            var xIdx = body.indexOf('x');
            if (xIdx < 0) {
                throw new IllegalArgumentException("Missing 'x' in volume/product part: " + body);
            }

            var volumeStr = body.substring(0, xIdx).trim();
            var nameTotal = body.substring(xIdx + 1).trim().split(" for ", 2);
            if (nameTotal.length != 2) {
                throw new IllegalArgumentException(
                    "Expected '<productName> for <total>' pattern, got: " + body);
            }

            var volume = Util
                .parseUsFormattedNumber(volumeStr)
                .map(Number::intValue)
                .getOrElseThrow(() -> new IllegalArgumentException("Invalid volume: " + volumeStr));

            var productName = nameTotal[0].trim();

            var totalStr = nameTotal[1].replace("coins.", "").trim();
            var total = Util
                .parseUsFormattedNumber(totalStr)
                .map(Number::doubleValue)
                .getOrElseThrow(() -> new IllegalArgumentException("Invalid total: " + totalStr));

            return new ChatOrderConfirmationInfo(productName, type, volume, total);
        });
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
