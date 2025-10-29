package com.github.lutzluca.btrbz.data;

import com.github.lutzluca.btrbz.data.BazaarMessageDispatcher.BazaarMessage;
import com.github.lutzluca.btrbz.data.BazaarMessageDispatcher.BazaarMessage.InstaBuy;
import com.github.lutzluca.btrbz.data.BazaarMessageDispatcher.BazaarMessage.InstaSell;
import com.github.lutzluca.btrbz.data.BazaarMessageDispatcher.BazaarMessage.OrderFilled;
import com.github.lutzluca.btrbz.data.BazaarMessageDispatcher.BazaarMessage.OrderFlipped;
import com.github.lutzluca.btrbz.data.BazaarMessageDispatcher.BazaarMessage.OrderSetup;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.OrderModels.OutstandingOrderInfo;
import com.github.lutzluca.btrbz.utils.Util;
import io.vavr.control.Try;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

@Slf4j
public final class OrderInfoParser {

    private OrderInfoParser() { }

    public static Try<BazaarMessage> parseBazaarMessage(String bazaarMsg) {
        assert bazaarMsg.startsWith("[Bazaar]");
        var msg = bazaarMsg.replace("[Bazaar]", "").trim();

        if (msg.endsWith("was filled!")) {
            return parseFilledOrderMessage(msg).onFailure(err -> logParseError(
                "filled order",
                msg,
                err
            ));
        }

        if (msg.contains("Setup!")) {
            return parseSetupOrderMessage(msg).onFailure(err -> logParseError(
                "setup order",
                msg,
                err
            ));
        }

        if (msg.startsWith("Bought") || msg.startsWith("Sold")) {
            return parseInstaOrderMessage(msg).onFailure(err -> logParseError(
                "insta order",
                msg,
                err
            ));
        }

        if (msg.startsWith("Order Flipped!")) {
            return parseFlippedOrderMessage(msg).onFailure(err -> logParseError(
                "flipped order",
                msg,
                err
            ));
        }

        log.trace("Unhandled bazaar message format: '{}'", msg);
        return Try.failure(new IllegalArgumentException("Unhandled bazaar message format: " + msg));
    }

    private static void logParseError(String ctx, String msg, Throwable err) {
        log.warn("Failed to parse {}: '{}'", ctx, msg, err);
    }

    private static ParsedItem parseItemWithValue(String fragment, String ctx) {
        // e.g. "12x Enchanted Diamond for 230,123 coins."
        var parts = fragment.split(" for ", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException(ctx + ": missing 'for' in '" + fragment + "'");
        }

        var volumeAndName = parts[0].trim();
        var pricePart = parts[1]
            .trim()
            .replace("coins.", "")
            .replace("coins!", "")
            .replace("coins of total expected profit.", "")
            .trim();

        var parsedVolume = parseVolume(volumeAndName, ctx);
        var price = Util
            .parseUsFormattedNumber(pricePart)
            .getOrElseThrow(() -> new IllegalArgumentException(ctx + ": invalid price in '" + fragment + "'"));

        return new ParsedItem(parsedVolume.volume, parsedVolume.productName, price.doubleValue());
    }

    private static ParsedVolume parseVolume(String fragment, String ctx) {
        // "12x Enchanted Diamond"
        var xSplit = fragment.split("x", 2);
        if (xSplit.length != 2) {
            throw new IllegalArgumentException(ctx + ": missing 'x' in '" + fragment + "'");
        }

        var volume = Util
            .parseUsFormattedNumber(xSplit[0].trim())
            .getOrElseThrow(() -> new IllegalArgumentException(ctx + ": invalid volume in '" + fragment + "'"));

        var product = xSplit[1].trim();
        return new ParsedVolume(volume.intValue(), product);
    }

    private static Try<BazaarMessage> parseSetupOrderMessage(String msg) {
        // "Buy Order Setup! 12x Enchanted Diamond for 431,123 coins."
        return Try.of(() -> {
            var parts = msg.split("!", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Setup message: missing '!' in '" + msg + "'");
            }

            var header = parts[0].trim(); // "Buy Order Setup" or "Sell Offer Setup"
            var body = parts[1].trim();

            var type = switch (header) {
                case "Buy Order Setup" -> OrderType.Buy;
                case "Sell Offer Setup" -> OrderType.Sell;
                default ->
                    throw new IllegalArgumentException("Setup message: unknown type '" + header + "'");
            };

            var parsed = parseItemWithValue(body, "Setup message");
            return new OrderSetup(type, parsed.volume, parsed.productName, parsed.value);
        });
    }

    private static Try<BazaarMessage> parseFilledOrderMessage(String msg) {
        // "Your Buy Order for 12x Enchanted Diamond was filled!"
        return Try.of(() -> {
            var forSplit = msg.split(" for ", 2);
            if (forSplit.length != 2) {
                throw new IllegalArgumentException("Filled order: missing 'for' in '" + msg + "'");
            }

            var typePart = forSplit[0].trim();
            var orderType = switch (typePart) {
                case "Your Buy Order" -> OrderType.Buy;
                case "Your Sell Offer" -> OrderType.Sell;
                default ->
                    throw new IllegalArgumentException("Filled order: unknown type '" + typePart + "'");
            };

            var fragment = forSplit[1].replace("was filled!", "").trim();
            var parsed = parseVolume(fragment, "Filled order");

            return new OrderFilled(orderType, parsed.volume, parsed.productName);
        });
    }

    private static Try<BazaarMessage> parseInstaOrderMessage(String msg) {
        // "Bought 12x Enchanted Diamond for 123,521 coins!"
        return Try.of(() -> {
            var isBuy = msg.startsWith("Bought");
            var action = isBuy ? "Bought" : "Sold";
            var fragment = msg.substring(action.length()).trim();

            var parsed = parseItemWithValue(fragment, "Insta " + action);
            return isBuy ? new InstaBuy(parsed.volume, parsed.productName, parsed.value)
                : new InstaSell(parsed.volume, parsed.productName, parsed.value);
        });
    }

    private static Try<BazaarMessage> parseFlippedOrderMessage(String msg) {
        // "Order Flipped! 3x Enchanted Sugar for 123,521 coins of total expected profit."
        return Try.of(() -> {
            var parts = msg.split("!", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Flipped order: missing '!' in '" + msg + "'");
            }

            var body = parts[1].trim();
            var parsed = parseItemWithValue(body, "Flipped order");

            return new OrderFlipped(parsed.volume, parsed.productName, parsed.value);
        });
    }

    public static Try<OrderInfo> parseOrderInfo(ItemStack item, int slotIdx) {
        // name: {type} {productName}
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
                    "Title line of item does not follow the pattern '<type> <productName>'");
            }

            var orderTypeResult = OrderType.tryFrom(orderInfo[0]);
            if (orderTypeResult.isFailure()) {
                throw new IllegalArgumentException("Failed to parse Order type: " + orderTypeResult
                    .getCause()
                    .getMessage());
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
                productName.trim(),
                orderTypeResult.get(),
                details.volume(),
                details.pricePerUnit(),
                details.filled(),
                slotIdx
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
                    var parsed = Util.parseUsFormattedNumber(line
                        .replace("Price per unit:", "")
                        .replace("coins", "")
                        .trim());
                    pricePerUnit = parsed
                        .getOrElseThrow(() -> new IllegalArgumentException(
                            "Failed to parse pricePerUnit"))
                        .doubleValue();
                } else if (volume == null && (line.startsWith("Order amount:") || line.startsWith(
                    "Offer amount:"))) {
                    var parsed = Util.parseUsFormattedNumber(line
                        .replace("Order amount:", "")
                        .replace("Offer amount:", "")
                        .replaceAll("x.*", "")
                        .trim());
                    volume = parsed
                        .getOrElseThrow(() -> new IllegalArgumentException("Failed to parse volume"))
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
                    var parsed = Util.parseUsFormattedNumber(line
                        .replace("Price per unit:", "")
                        .replace("coins", "")
                        .trim());
                    if (parsed.isFailure()) {
                        throw new IllegalArgumentException("Failed to parse pricePerUnit: " + parsed
                            .getCause()
                            .getMessage());
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
                        throw new IllegalArgumentException("Failed to parse volume: " + parsed
                            .getCause()
                            .getMessage());
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
                        throw new IllegalArgumentException("Failed to parse total: " + parsed
                            .getCause()
                            .getMessage());
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

    public static List<String> getLore(ItemStack item) {
        return Optional
            .ofNullable(item.get(DataComponentTypes.LORE))
            .map(LoreComponent::lines)
            .orElseGet(ArrayList::new)
            .stream()
            .map(Text::getString)
            .toList();
    }

    private record ParsedItem(int volume, String productName, double value) { }

    private record ParsedVolume(int volume, String productName) { }

    private record OrderDetails(double pricePerUnit, int volume, boolean filled) { }
}
