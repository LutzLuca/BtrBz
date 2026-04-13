package com.github.lutzluca.btrbz.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import com.github.lutzluca.btrbz.data.BazaarMessageDispatcher.BazaarMessage;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OrderInfoParserTest {

    @Nested
    @DisplayName("parseBazaarMessage")
    class ParseBazaarMessage {

        @Nested
        @DisplayName("OrderSetup")
        class OrderSetupMessages {

            @Test
            void parsesBuyOrderSetup() {
                var result = OrderInfoParser.parseBazaarMessage(
                    "[Bazaar] Buy Order Setup! 12x Enchanted Diamond for 431,123 coins."
                );

                assertTrue(result.isSuccess());
                assertEquals(
                    new BazaarMessage.OrderSetup(OrderType.Buy, 12, "Enchanted Diamond", 431123.0),
                    result.get()
                );
            }

            @Test
            void parsesSellOfferSetup() {
                var result = OrderInfoParser.parseBazaarMessage(
                    "[Bazaar] Sell Offer Setup! 8x Heat Core for 10,400,000 coins."
                );

                assertTrue(result.isSuccess());
                assertEquals(
                    new BazaarMessage.OrderSetup(OrderType.Sell, 8, "Heat Core", 10400000.0),
                    result.get()
                );
            }

            @Test
            void rejectsMalformedSetupWithoutExpectedHeader() {
                assertTrue(OrderInfoParser.parseBazaarMessage(
                    "[Bazaar] Buy Setup! 12x Enchanted Diamond for 431,123 coins."
                ).isFailure());
            }

            @Test
            void rejectsSetupMissingBangSeparator() {
                assertTrue(OrderInfoParser.parseBazaarMessage(
                    "[Bazaar] Buy Order Setup 12x Enchanted Diamond for 431,123 coins."
                ).isFailure());
            }
        }

        @Nested
        @DisplayName("OrderFilled")
        class OrderFilledMessages {

            @Test
            void parsesBuyOrderFilled() {
                var result = OrderInfoParser.parseBazaarMessage(
                    "[Bazaar] Your Buy Order for 12x Enchanted Diamond was filled!"
                );

                assertTrue(result.isSuccess());
                assertEquals(
                    new BazaarMessage.OrderFilled(OrderType.Buy, 12, "Enchanted Diamond"),
                    result.get()
                );
            }

            @Test
            void parsesSellOfferFilled() {
                var result = OrderInfoParser.parseBazaarMessage(
                    "[Bazaar] Your Sell Offer for 5x Summoning Eye was filled!"
                );

                assertTrue(result.isSuccess());
                assertEquals(
                    new BazaarMessage.OrderFilled(OrderType.Sell, 5, "Summoning Eye"),
                    result.get()
                );
            }

            @Test
            void parsesGoToOrdersSuffixVariant() {
                var result = OrderInfoParser.parseBazaarMessage(
                    "[Bazaar] Your Buy Order for 2,304x Mithril was filled! [Go To Orders]"
                );

                assertTrue(result.isSuccess());
                assertEquals(
                    new BazaarMessage.OrderFilled(OrderType.Buy, 2304, "Mithril"),
                    result.get()
                );
            }

            @Test
            void rejectsMalformedFilledMessage() {
                assertTrue(OrderInfoParser.parseBazaarMessage(
                    "[Bazaar] Your Buy Order 12x Enchanted Diamond was filled!"
                ).isFailure());
            }
        }

        @Nested
        @DisplayName("Insta Orders")
        class InstaOrderMessages {

            @Test
            void parsesInstaBuy() {
                var result = OrderInfoParser.parseBazaarMessage(
                    "[Bazaar] Bought 12x Enchanted Diamond for 123,521 coins!"
                );

                assertTrue(result.isSuccess());
                assertEquals(
                    new BazaarMessage.InstaBuy(12, "Enchanted Diamond", 123521.0),
                    result.get()
                );
            }

            @Test
            void parsesInstaSellWithCommaFormattedValues() {
                var result = OrderInfoParser.parseBazaarMessage(
                    "[Bazaar] Sold 1,024x Mithril for 2,560,000 coins!"
                );

                assertTrue(result.isSuccess());
                assertEquals(
                    new BazaarMessage.InstaSell(1024, "Mithril", 2560000.0),
                    result.get()
                );
            }

            @Test
            void rejectsMalformedInstaOrder() {
                assertTrue(OrderInfoParser.parseBazaarMessage(
                    "[Bazaar] Bought Enchanted Diamond for 123,521 coins!"
                ).isFailure());
            }
        }

        @Nested
        @DisplayName("OrderFlipped")
        class OrderFlippedMessages {

            @Test
            void parsesFlippedOrder() {
                var result = OrderInfoParser.parseBazaarMessage(
                    "[Bazaar] Order Flipped! 3x Enchanted Sugar for 123,521 coins of total expected profit."
                );

                assertTrue(result.isSuccess());
                assertEquals(
                    new BazaarMessage.OrderFlipped(3, "Enchanted Sugar", 123521.0),
                    result.get()
                );
            }

            @Test
            void rejectsMalformedFlippedOrder() {
                assertTrue(OrderInfoParser.parseBazaarMessage(
                    "[Bazaar] Order Flipped! Enchanted Sugar for 123,521 coins of total expected profit."
                ).isFailure());
            }
        }

        @Test
        void rejectsNonBazaarMessagesCleanly() {
            assertTrue(OrderInfoParser.parseBazaarMessage("Hello there").isFailure());
        }
    }

    @Nested
    @DisplayName("parseOrderInfo lore seam")
    class ParseOrderInfoLore {

        @Test
        void parsesUnfilledBuyOrder() {
            var result = OrderInfoParser.parseOrderInfo("BUY Enchanted Diamond", orderLore(
                "Worth 431,123 coins",
                "",
                "Order amount: 12x",
                "",
                "Price per unit: 35,926.9 coins"
            ), 4);

            assertTrue(result.isSuccess());
            var info = assertInstanceOf(OrderInfo.UnfilledOrderInfo.class, result.get());
            assertEquals("Enchanted Diamond", info.productName());
            assertEquals(OrderType.Buy, info.type());
            assertEquals(12, info.volume());
            assertEquals(35_926.9, info.pricePerUnit());
            assertEquals(0, info.filledAmountSnapshot());
            assertEquals(0, info.unclaimed());
            assertEquals(4, info.slotIdx());
        }

        @Test
        void parsesUnfilledSellOffer() {
            var result = OrderInfoParser.parseOrderInfo("SELL Summoning Eye", orderLore(
                "Worth 7,500,000 coins",
                "Offer amount: 5x",
                "Price per unit: 1,500,000 coins"
            ), 9);

            assertTrue(result.isSuccess());
            var info = assertInstanceOf(OrderInfo.UnfilledOrderInfo.class, result.get());
            assertEquals("Summoning Eye", info.productName());
            assertEquals(OrderType.Sell, info.type());
            assertEquals(5, info.volume());
            assertEquals(1_500_000.0, info.pricePerUnit());
            assertEquals(9, info.slotIdx());
        }

        @Test
        void parsesPartiallyFilledOrder() {
            var result = OrderInfoParser.parseOrderInfo("BUY Enchanted Iron", orderLore(
                "Worth 120,000 coins",
                "Order amount: 64x",
                "Filled: 16/64 25%!",
                "Price per unit: 1,875 coins"
            ), 1);

            assertTrue(result.isSuccess());
            var info = assertInstanceOf(OrderInfo.UnfilledOrderInfo.class, result.get());
            assertEquals(16, info.filledAmountSnapshot());
        }

        @Test
        void parsesHundredPercentFilledOrderAsFilledInfo() {
            var result = OrderInfoParser.parseOrderInfo("SELL Mithril", orderLore(
                "Worth 2,560,000 coins",
                "Offer amount: 1,024x",
                "Filled: 1,024/1,024 100%!",
                "Price per unit: 2,500 coins"
            ), 6);

            assertTrue(result.isSuccess());
            var info = assertInstanceOf(OrderInfo.FilledOrderInfo.class, result.get());
            assertEquals(1024, info.filledAmountSnapshot());
        }

        @Test
        void parsesUnclaimedItemsAndIgnoresExtraLines() {
            var result = OrderInfoParser.parseOrderInfo("BUY Heat Core", orderLore(
                "Worth 10,400,000 coins",
                "Order amount: 8x",
                "Some unrelated line",
                "Price per unit: 1,300,000 coins",
                "Created: just now",
                "You have 2 of this order to claim"
            ), 12);

            assertTrue(result.isSuccess());
            var info = assertInstanceOf(OrderInfo.UnfilledOrderInfo.class, result.get());
            assertEquals(2, info.unclaimed());
        }

        @Test
        void failsWhenRequiredFieldsAreMissing() {
            assertTrue(OrderInfoParser.parseOrderInfo("BUY Heat Core", orderLore(
                "Worth 10,400,000 coins",
                "Created: just now"
            ), 12).isFailure());
        }
    }

    @Nested
    @DisplayName("parseSetOrderItem lore seam")
    class ParseSetOrderItemLore {

        @Test
        void parsesBuyOrderConfirmItem() {
            var result = OrderInfoParser.parseSetOrderItem("Buy Order", confirmLore(
                "Bazaar",
                "Price per unit: 35,926.9 coins",
                "Order: 12x Enchanted Diamond",
                "Total price: 431,123 coins"
            ));

            assertTrue(result.isSuccess());
            assertEquals("Enchanted Diamond", result.get().productName());
            assertEquals(OrderType.Buy, result.get().type());
            assertEquals(12, result.get().volume());
            assertEquals(35_926.9, result.get().pricePerUnit());
            assertEquals(431_123.0, result.get().total());
        }

        @Test
        void parsesSellOfferConfirmItem() {
            var result = OrderInfoParser.parseSetOrderItem("Sell Offer", confirmLore(
                "Bazaar",
                "Price per unit: 1,500,000 coins",
                "Selling: 5x Summoning Eye",
                "You earn: 7,500,000 coins"
            ));

            assertTrue(result.isSuccess());
            assertEquals("Summoning Eye", result.get().productName());
            assertEquals(OrderType.Sell, result.get().type());
        }

        @Test
        void failsWhenRequiredFieldsAreMissing() {
            assertTrue(OrderInfoParser.parseSetOrderItem("Buy Order", confirmLore(
                "Bazaar",
                "Price per unit: 35,926.9 coins"
            )).isFailure());
        }
    }

    private static List<String> orderLore(String... lines) {
        return List.of(lines);
    }

    private static List<String> confirmLore(String... lines) {
        return List.of(lines);
    }
}