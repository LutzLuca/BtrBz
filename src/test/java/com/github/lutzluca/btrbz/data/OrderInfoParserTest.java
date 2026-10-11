package com.github.lutzluca.btrbz.data;

import com.github.lutzluca.btrbz.data.BazaarMessageDispatcher.BazaarMessage;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.OrderModels.OutstandingOrderInfo;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OrderInfoParserTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("acceptedMessages")
    void parsesBazaarMessages(String description, String message, BazaarMessage expected) {
        var result = OrderInfoParser.parseBazaarMessage(message);

        Assertions.assertTrue(result.isSuccess());
        Assertions.assertEquals(expected, result.get());
    }

    private static Stream<Arguments> acceptedMessages() {
        return Stream.of(
            Arguments.of("buy setup",
                "[Bazaar] Buy Order Setup! 12x Enchanted Diamond for 431,123 coins.",
                new BazaarMessage.OrderSetup(OrderType.Buy, 12, "Enchanted Diamond", 431123.0)),
            Arguments.of("sell setup",
                "[Bazaar] Sell Offer Setup! 8x Heat Core for 10,400,000 coins.",
                new BazaarMessage.OrderSetup(OrderType.Sell, 8, "Heat Core", 10400000.0)),
            Arguments.of("buy filled",
                "[Bazaar] Your Buy Order for 12x Enchanted Diamond was filled!",
                new BazaarMessage.OrderFilled(OrderType.Buy, 12, "Enchanted Diamond")),
            Arguments.of("sell filled",
                "[Bazaar] Your Sell Offer for 5x Summoning Eye was filled!",
                new BazaarMessage.OrderFilled(OrderType.Sell, 5, "Summoning Eye")),
            Arguments.of("filled with grouped volume and navigation suffix",
                "[Bazaar] Your Buy Order for 2,304x Mithril was filled! [Go To Orders]",
                new BazaarMessage.OrderFilled(OrderType.Buy, 2304, "Mithril")),
            Arguments.of("instant buy",
                "[Bazaar] Bought 12x Enchanted Diamond for 123,521 coins!",
                new BazaarMessage.InstaBuy(12, "Enchanted Diamond", 123521.0)),
            Arguments.of("instant sell with grouped values",
                "[Bazaar] Sold 1,024x Mithril for 2,560,000 coins!",
                new BazaarMessage.InstaSell(1024, "Mithril", 2560000.0)),
            Arguments.of("flipped order",
                "[Bazaar] Order Flipped! 3x Enchanted Sugar for 123,521 coins of total expected profit.",
                new BazaarMessage.OrderFlipped(3, "Enchanted Sugar", 123521.0)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rejectedMessages")
    void rejectsMalformedBazaarMessages(String description, String message) {
        Assertions.assertTrue(OrderInfoParser.parseBazaarMessage(message).isFailure());
    }

    private static Stream<Arguments> rejectedMessages() {
        return Stream.of(
            Arguments.of("wrong setup header",
                "[Bazaar] Buy Setup! 12x Enchanted Diamond for 431,123 coins."),
            Arguments.of("missing setup bang",
                "[Bazaar] Buy Order Setup 12x Enchanted Diamond for 431,123 coins."),
            Arguments.of("missing filled separator",
                "[Bazaar] Your Buy Order 12x Enchanted Diamond was filled!"),
            Arguments.of("missing instant volume",
                "[Bazaar] Bought Enchanted Diamond for 123,521 coins!"),
            Arguments.of("missing flipped volume",
                "[Bazaar] Order Flipped! Enchanted Sugar for 123,521 coins of total expected profit."),
            Arguments.of("non-Bazaar message", "Hello there"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("orderLore")
    void parsesOrderLore(
        String description,
        String title,
        List<String> lore,
        int slot,
        OrderInfo expected
    ) {
        var result = OrderInfoParser.parseOrderInfo(title, lore, slot);

        Assertions.assertTrue(result.isSuccess());
        Assertions.assertEquals(expected, result.get());
    }

    private static Stream<Arguments> orderLore() {
        return Stream.of(
            Arguments.of("unfilled buy",
                "BUY Enchanted Diamond",
                List.of("Worth 431,123 coins", "", "Order amount: 12x", "",
                    "Price per unit: 35,926.9 coins"),
                4,
                new OrderInfo.UnfilledOrderInfo(
                    "Enchanted Diamond", OrderType.Buy, 12, 35_926.9, 0, 0, 4)),
            Arguments.of("unfilled sell",
                "SELL Summoning Eye",
                List.of("Worth 7,500,000 coins", "Offer amount: 5x",
                    "Price per unit: 1,500,000 coins"),
                9,
                new OrderInfo.UnfilledOrderInfo(
                    "Summoning Eye", OrderType.Sell, 5, 1_500_000.0, 0, 0, 9)),
            Arguments.of("formatted book-title lore retains volume and claims",
                "\u00a7aBUY \u00a79Bane of Arthropods VI",
                List.of("Worth 9 coins", "\u00a77Order amount: \u00a7a32x", "Filled: 4/32 (12.5%)",
                    "Price per unit: 0.3 coins", "You have 4 items to claim!"),
                10,
                new OrderInfo.UnfilledOrderInfo(
                    "Bane of Arthropods VI", OrderType.Buy, 32, 0.3, 4, 4, 10)),
            Arguments.of("one-item filled sell",
                "SELL Bane of Arthropods VI",
                List.of("Offer amount: 1x", "Filled: 1/1 100%!", "Price per unit: 0.3 coins"), 12,
                new OrderInfo.FilledOrderInfo(
                    "Bane of Arthropods VI", OrderType.Sell, 1, 0.3, 1, 0, 12)),
            Arguments.of("complete compact fill keeps exact order volume",
                "BUY Flawed Topaz Gemstone",
                List.of("Worth 21.3M coins", "Order amount: 51,200x",
                    "Filled: 51.2k/51.2k 100%!", "Price per unit: 415.9 coins"),
                6,
                new OrderInfo.FilledOrderInfo(
                    "Flawed Topaz Gemstone", OrderType.Buy, 51_200, 415.9, 51_200, 0, 6)),
            Arguments.of("unclaimed items with distracting lore",
                "BUY Heat Core",
                List.of("Worth 10,400,000 coins", "Order amount: 8x", "Some unrelated line",
                    "Price per unit: 1,300,000 coins", "Created: just now",
                    "You have 2 of this order to claim"),
                12,
                new OrderInfo.UnfilledOrderInfo(
                    "Heat Core", OrderType.Buy, 8, 1_300_000.0, 0, 2, 12)),
            Arguments.of("formatted order title and lore",
                "\u00a7aBUY \u00a7d\u00a7lBank III",
                List.of("\u00a77Worth \u00a76343.6 coins", "", "\u00a77Order amount: \u00a7a4x", "",
                    "\u00a77Price per unit: \u00a7685.9 coins"),
                17,
                new OrderInfo.UnfilledOrderInfo(
                    "Bank III", OrderType.Buy, 4, 85.9, 0, 0, 17)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("exactFillLore")
    void exactFilledNumeratorWinsOverRoundedPercentage(
        String description,
        String title,
        List<String> lore,
        int slot,
        OrderInfo expected
    ) {
        var result = OrderInfoParser.parseOrderInfo(title, lore, slot);

        Assertions.assertTrue(result.isSuccess());
        Assertions.assertEquals(expected, result.get());
    }

    private static Stream<Arguments> exactFillLore() {
        return Stream.of(
            Arguments.of("17 of 64 despite rounded 25 percent",
                "BUY Enchanted Iron",
                List.of("Worth 120,000 coins", "Order amount: 64x", "Filled: 17/64 (25%)",
                    "Price per unit: 1,875 coins"),
                1,
                new OrderInfo.UnfilledOrderInfo(
                    "Enchanted Iron", OrderType.Buy, 64, 1_875.0, 17, 0, 1)),
            Arguments.of("exact numerator with compact denominator",
                "BUY Flawed Topaz Gemstone",
                List.of("Worth 16.1M coins", "Order amount: 51,200x", "Filled: 652/51.2k (1.3%)",
                    "Price per unit: 314.0 coins", "You have 652 items to claim!"),
                11,
                new OrderInfo.UnfilledOrderInfo(
                    "Flawed Topaz Gemstone", OrderType.Buy, 51_200, 314.0, 652, 652, 11)));
    }

    @Test
    void estimatesFilledAmountFromPercentageWhenTheNumeratorIsCompact() {
        var result = OrderInfoParser.parseOrderInfo("BUY Enchanted Iron",
            List.of("Worth 96,000,000 coins", "Order amount: 51,200x", "Filled: 3.1k/51.2k (6.1%)",
                "Price per unit: 1,875 coins"),
            1);

        Assertions.assertTrue(result.isSuccess());
        Assertions.assertEquals(new OrderInfo.UnfilledOrderInfo(
            "Enchanted Iron", OrderType.Buy, 51_200, 1_875.0, 3_123, 0, 1), result.get());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("expiryLore")
    void filledStateTakesPrecedenceOverExpiryAndPreservesClaims(
        String description,
        String title,
        List<String> lore,
        int slot,
        OrderInfo expected
    ) {
        var result = OrderInfoParser.parseOrderInfo(title, lore, slot);

        Assertions.assertTrue(result.isSuccess());
        Assertions.assertEquals(expected, result.get());
    }

    private static Stream<Arguments> expiryLore() {
        return Stream.of(
            Arguments.of("similar text is not expiry",
                "BUY Heat Core",
                List.of("Order amount: 8x", "Price per unit: 1,300,000 coins",
                    "Expires in 1m", "Not Expired!"),
                12,
                new OrderInfo.UnfilledOrderInfo(
                    "Heat Core", OrderType.Buy, 8, 1_300_000.0, 0, 0, 12)),
            Arguments.of("exact stripped marker without fills",
                "BUY Heat Core",
                List.of("Order amount: 8x", "Price per unit: 1,300,000 coins", " \u00a7cExpired! "), 12,
                new OrderInfo.ExpiredOrderInfo(
                    "Heat Core", OrderType.Buy, 8, 1_300_000.0, 0, 0, 12)),
            Arguments.of("expired partial sell preserves coin claim",
                "SELL Heat Core",
                List.of("Offer amount: 8x", "Filled: 3/8 (37.5%)", "Price per unit: 7 coins",
                    "You have 11 coins to claim!", "Expired!"),
                12,
                new OrderInfo.ExpiredOrderInfo("Heat Core", OrderType.Sell, 8, 7.0, 3, 11, 12)),
            Arguments.of("expired complete buy preserves item claim and is treated as filled",
                "BUY Heat Core",
                List.of("Expired!", "Order amount: 8x", "Filled: 8/8 100%!",
                    "Price per unit: 5 coins", "You have 2 items to claim!"),
                13,
                new OrderInfo.FilledOrderInfo("Heat Core", OrderType.Buy, 8, 5.0, 8, 2, 13)));
    }

    @Test
    void rejectsOrderLoreMissingRequiredFields() {
        Assertions.assertTrue(OrderInfoParser.parseOrderInfo("BUY Heat Core",
            List.of("Worth 10,400,000 coins", "Created: just now"), 12).isFailure());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("confirmationLore")
    void parsesOrderConfirmation(
        String description,
        String title,
        List<String> lore,
        OutstandingOrderInfo expected
    ) {
        var result = OrderInfoParser.parseSetOrderItem(title, lore);

        Assertions.assertTrue(result.isSuccess());
        Assertions.assertEquals(expected, result.get());
    }

    private static Stream<Arguments> confirmationLore() {
        return Stream.of(
            Arguments.of("buy confirmation", "Buy Order",
                List.of("Bazaar", "Price per unit: 35,926.9 coins",
                    "Order: 12x Enchanted Diamond", "Total price: 431,123 coins"),
                new OutstandingOrderInfo("Enchanted Diamond", OrderType.Buy, 12, 35_926.9, 431_123.0)),
            Arguments.of("formatted buy confirmation", "\u00a7aBuy Order",
                List.of("\u00a78Bazaar", "\u00a77Price per unit: \u00a7685.9 coins",
                    "\u00a77Order: \u00a7a4\u00a77x \u00a7d\u00a7lBank III", "\u00a77Total price: \u00a76343.6 coins"),
                new OutstandingOrderInfo("Bank III", OrderType.Buy, 4, 85.9, 343.6)),
            Arguments.of("sell confirmation", "Sell Offer",
                List.of("Bazaar", "Price per unit: 1,500,000 coins",
                    "Selling: 5x Summoning Eye", "You earn: 7,500,000 coins"),
                new OutstandingOrderInfo("Summoning Eye", OrderType.Sell, 5, 1_500_000.0, 7_500_000.0)));
    }

    @Test
    void rejectsConfirmationMissingRequiredFields() {
        Assertions.assertTrue(OrderInfoParser.parseSetOrderItem("Buy Order",
            List.of("Bazaar", "Price per unit: 35,926.9 coins")).isFailure());
    }

    @Test
    void extractsFormattedProductNameFromConfirmationLoreWithoutCount() {
        var formattedName = OrderInfoParser.formattedProductNameFromConfirmationLore(
            List.of(Component.empty()
                .append(Component.literal("Order: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("160").withStyle(ChatFormatting.GREEN))
                .append(Component.literal("x ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("Enchanted Gold Ingot").withStyle(ChatFormatting.GREEN))),
            "Enchanted Gold Ingot");

        Assertions.assertEquals(ChatFormatting.GREEN + "Enchanted Gold Ingot", formattedName.orElseThrow());
    }
}
