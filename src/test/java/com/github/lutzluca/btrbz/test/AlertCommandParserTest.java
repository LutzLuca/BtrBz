package com.github.lutzluca.btrbz.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.lutzluca.btrbz.commands.alert.AlertCommandParser;
import com.github.lutzluca.btrbz.commands.alert.AlertCommandParser.AlertCommand;
import com.github.lutzluca.btrbz.commands.alert.AlertCommandParser.ParseException;
import com.github.lutzluca.btrbz.commands.alert.PriceExpression;
import com.github.lutzluca.btrbz.commands.alert.PriceExpression.AlertType;
import com.github.lutzluca.btrbz.commands.alert.PriceExpression.Binary;
import com.github.lutzluca.btrbz.commands.alert.PriceExpression.BinaryOperator;
import com.github.lutzluca.btrbz.commands.alert.PriceExpression.Identifier;
import com.github.lutzluca.btrbz.commands.alert.PriceExpression.Literal;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.google.common.collect.HashBiMap;
import io.vavr.control.Try;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AlertCommandParserTest {

    private final AlertCommandParser parser = new AlertCommandParser();

    @Nested
    @DisplayName("Valid Commands")
    class ValidCommands {

        @Test
        void simpleBuyOrder() throws ParseException {
            AlertCommand cmd = parser.parse("smOldeRinG 5 buy 12m");
            PriceExpression expected = new Literal(12_000_000.0);

            assertEquals("Smoldering V", cmd.productName());
            assertEquals(AlertType.BuyOrder, cmd.type());
            assertEquals(expected, cmd.priceExpression());
        }

        @Test
        void sellOrderWithExplicitIdentifier() throws ParseException {
            AlertCommand cmd = parser.parse("eye OF THE ender sell order + 2m - 10k");

            PriceExpression expected = new Binary(
                new Binary(
                    new Identifier("order"), BinaryOperator.Add,
                    new Literal(2_000_000.0)
                ),
                BinaryOperator.Subtract, new Literal(10_000.0)
            );

            assertEquals("Eye Of The Ender", cmd.productName());
            assertEquals(AlertType.SellOffer, cmd.type());
            assertEquals(expected, cmd.priceExpression());
        }

        @Test
        void instaBuyExplicitNegative() throws ParseException {
            AlertCommand cmd = parser.parse("dragon bone ib insta - 1.5m");

            PriceExpression expected = new Binary(
                new Identifier("insta"), BinaryOperator.Subtract,
                new Literal(1_500_000.0)
            );

            assertEquals("Dragon Bone", cmd.productName());
            assertEquals(AlertType.InstaBuy, cmd.type());
            assertEquals(expected, cmd.priceExpression());
        }

        @Test
        void orderTypeAliases() throws ParseException {
            assertEquals(AlertType.BuyOrder, parser.parse("item b 100k").type());
            assertEquals(AlertType.BuyOrder, parser.parse("item buyorder 100k").type());
            assertEquals(AlertType.SellOffer, parser.parse("item s 100k").type());
            assertEquals(AlertType.SellOffer, parser.parse("item selloffer 100k").type());
            assertEquals(AlertType.InstaBuy, parser.parse("item ibuy 100k").type());
            assertEquals(AlertType.InstaBuy, parser.parse("item instabuy 100k").type());
            assertEquals(AlertType.InstaSell, parser.parse("item is 100k").type());
            assertEquals(AlertType.InstaSell, parser.parse("item instasell 100k").type());
        }

        @Test
        void caseInsensitivity() throws ParseException {
            AlertCommand cmd = parser.parse("ITEM BUY 100K");
            assertEquals("Item", cmd.productName());
            assertEquals(AlertType.BuyOrder, cmd.type());
        }

        @Test
        void complexExpression() throws ParseException {
            AlertCommand cmd = parser.parse("item buy 120_000_000 / 2");

            PriceExpression expected =
                new Binary(new Literal(120_000_000.0), BinaryOperator.Divide, new Literal(2.0));

            assertEquals(expected, cmd.priceExpression());
        }

        @Test
        void expressionWithParentheses() throws ParseException {
            AlertCommand cmd = parser.parse("item buy (2m + 10k) * 2");

            PriceExpression expected = new Binary(
                new Binary(new Literal(2_000_000.0), BinaryOperator.Add, new Literal(10_000.0)),
                BinaryOperator.Multiply, new Literal(2.0)
            );

            assertEquals(expected, cmd.priceExpression());
        }

        @Test
        void numberFormattingVariations() throws ParseException {
            assertEquals(
                new Literal(120_123_123.3),
                parser.parse("item buy 120,123,123.3").priceExpression()
            );
            assertEquals(
                new Literal(120_123_123.3),
                parser.parse("item buy 120_123_123.3").priceExpression()
            );
            assertEquals(
                new Literal(15_300_000_000.0),
                parser.parse("item buy 15.3b").priceExpression()
            );
            assertEquals(new Literal(12_000_000.0), parser.parse("item buy 12m").priceExpression());
            assertEquals(new Literal(10_000.0), parser.parse("item buy 10k").priceExpression());
        }

        @Test
        void numberRounding() throws ParseException {
            assertEquals(
                new Literal(120_123_123.4),
                parser.parse("item buy 120_123_123.3791").priceExpression()
            );
            assertEquals(new Literal(100.5), parser.parse("item buy 100.45").priceExpression());
        }

        @Test
        void romanNumeralConversion() throws ParseException {
            assertEquals("Item I", parser.parse("item 1 buy 100k").productName());
            assertEquals("Item V", parser.parse("item 5 buy 100k").productName());
            assertEquals("Item X", parser.parse("item 10 buy 100k").productName());
            assertEquals("Item L", parser.parse("item 50 buy 100k").productName());
            assertEquals("Item XC", parser.parse("item 90 buy 100k").productName());
            assertEquals("Item C", parser.parse("item 100 buy 100k").productName());
        }

        @Test
        void explicitIdentifierOperators() throws ParseException {
            AlertCommand cmd = parser.parse("item buy order + 2m - 10k");

            PriceExpression expected = new Binary(
                new Binary(
                    new Identifier("order"), BinaryOperator.Add,
                    new Literal(2_000_000.0)
                ),
                BinaryOperator.Subtract, new Literal(10_000.0)
            );

            assertEquals(expected, cmd.priceExpression());
        }

        @Test
        void instaIdentifier() throws ParseException {
            AlertCommand cmd = parser.parse("item buy insta / 2");

            PriceExpression expected =
                new Binary(new Identifier("insta"), BinaryOperator.Divide, new Literal(2.0));

            assertEquals(expected, cmd.priceExpression());
        }

        @Test
        void expressionStartingWithIdentifier() throws ParseException {
            AlertCommand cmd = parser.parse("item buy order");

            PriceExpression expected = new Identifier("order");
            assertEquals(expected, cmd.priceExpression());
        }

        @Test
        void decimalWithoutInteger() throws ParseException {
            AlertCommand cmd = parser.parse("item buy .5m");

            PriceExpression expected = new Literal(500_000.0);
            assertEquals(expected, cmd.priceExpression());
        }

        @Test
        void multiWordProductNameWithMixedCase() throws ParseException {
            AlertCommand cmd = parser.parse("THE eYe OF THE eNdEr buy 100k");
            assertEquals("The Eye Of The Ender", cmd.productName());
        }

        @Test
        void productNameWithNumberInMiddle() throws ParseException {
            AlertCommand cmd = parser.parse("item 50 sword buy 100k");
            assertEquals("Item 50 Sword", cmd.productName());
        }

        @Test
        void singleLetterProductName() throws ParseException {
            AlertCommand cmd = parser.parse("a buy 100k");
            assertEquals("A", cmd.productName());
        }

        @Test
        void multipleSpacesBetweenTokens() throws ParseException {
            AlertCommand cmd = parser.parse("   item    buy    100k");
            assertEquals("Item", cmd.productName());
            assertEquals(AlertType.BuyOrder, cmd.type());
        }
    }

    @Nested
    @DisplayName("Expression Resolution")
    class ExpressionResolution {

        private final BazaarData mockData = new BazaarData(HashBiMap.create()) { };

        @Test
        void literalResolves() {
            Literal literal = new Literal(12_000_000.0);
            Try<Double> result = literal.resolve("", AlertType.BuyOrder, mockData);
            assertTrue(result.isSuccess());
            assertEquals(12_000_000.0, result.get());
        }

        @Test
        void binaryOpResolves() {
            Binary expr = new Binary(new Literal(100.0), BinaryOperator.Add, new Literal(50.0));
            Try<Double> result = expr.resolve("", AlertType.BuyOrder, mockData);
            assertTrue(result.isSuccess());
            assertEquals(150.0, result.get());
        }

        @Test
        void complexResolution() {
            Binary expr = new Binary(
                new Literal(100.0), BinaryOperator.Add,
                new Binary(new Literal(50.0), BinaryOperator.Multiply, new Literal(2.0))
            );
            Try<Double> result = expr.resolve("", AlertType.BuyOrder, mockData);
            assertTrue(result.isSuccess());
            assertEquals(200.0, result.get());
        }

        @Test
        void divisionResolution() {
            Binary expr = new Binary(new Literal(100.0), BinaryOperator.Divide, new Literal(4.0));
            Try<Double> result = expr.resolve("", AlertType.BuyOrder, mockData);
            assertTrue(result.isSuccess());
            assertEquals(25.0, result.get());
        }
    }
}
