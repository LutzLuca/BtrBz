package com.github.lutzluca.btrbz;

import com.github.lutzluca.btrbz.TrackedOrder.OrderType;
import io.vavr.control.Try;

public record SetOrderInfo(String productName, TrackedOrder.OrderType type, int volume,
        double pricePerUnit, double total, long timestampMillis) {

    public static SetOrderInfo of(String productName, TrackedOrder.OrderType type, int volume,
            double pricePerUnit, double total) {
        return new SetOrderInfo(productName, type, volume, pricePerUnit, total,
                System.currentTimeMillis());
    }

    public static Try<ChatOrder> parseSetupChat(String bazaarChatMsg) {
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
                default -> throw new IllegalArgumentException("Unexpected order type: '" + typeStr
                        + "', expected 'Buy Order Setup' or 'Sell Offer Setup'");
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

            var volume = OrderParser.parseNumber(volumeStr).map(Number::intValue).getOrElseThrow(
                    () -> new IllegalArgumentException("Invalid volume: " + volumeStr));

            var productName = nameTotal[0].trim();

            var totalStr = nameTotal[1].replace("coins.", "").trim();
            var total = OrderParser.parseNumber(totalStr).map(Number::doubleValue).getOrElseThrow(
                    () -> new IllegalArgumentException("Invalid total: " + totalStr));

            return new ChatOrder(productName, type, volume, total);
        });
    }

    public record ChatOrder(String productName, TrackedOrder.OrderType type, int volume,
            double total) {
    }
}
