package com.github.lutzluca.btrbz.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.lutzluca.btrbz.core.commands.alert.AlertCommandParser;
import com.github.lutzluca.btrbz.core.commands.alert.AlertCommandParser.AlertCommand;
import com.github.lutzluca.btrbz.core.commands.alert.AlertCommandParser.ParseException;
import com.github.lutzluca.btrbz.core.commands.alert.PriceExpression;
import com.github.lutzluca.btrbz.core.commands.alert.PriceExpression.AlertType;
import com.github.lutzluca.btrbz.core.commands.alert.PriceExpression.Binary;
import com.github.lutzluca.btrbz.core.commands.alert.PriceExpression.BinaryOperator;
import com.github.lutzluca.btrbz.core.commands.alert.PriceExpression.Literal;
import com.github.lutzluca.btrbz.core.commands.alert.PriceExpression.Reference;
import com.github.lutzluca.btrbz.core.commands.alert.PriceExpression.ReferenceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AlertCommandParserTest {

    private final AlertCommandParser parser = new AlertCommandParser();

    @Nested
    @DisplayName("valid commands")
    class ValidCommands {

        @Test
        void simpleBuyOrder() throws ParseException {
            AlertCommand cmd = AlertCommandParserTest.this.parser.parse("SMOLDERING_5 buy 12m");
            PriceExpression expected = new Literal(12_000_000.0);

            assertEquals("SMOLDERING_5", cmd.productId());
            assertEquals(AlertType.BuyOrder, cmd.type());
            assertEquals(expected, cmd.expr());
        }

        @Test
        void sellOrderWithExplicitReference() throws ParseException {
            AlertCommand cmd = AlertCommandParserTest.this.parser.parse(
                "EYE_OF_THE_ENDER sell order + 2m - 10k"
            );

            PriceExpression expected = new Binary(
                new Binary(
                    new Reference(ReferenceType.Order),
                    BinaryOperator.Add,
                    new Literal(2_000_000.0)
                ),
                BinaryOperator.Subtract,
                new Literal(10_000.0)
            );

            assertEquals("EYE_OF_THE_ENDER", cmd.productId());
            assertEquals(AlertType.SellOffer, cmd.type());
            assertEquals(expected, cmd.expr());
        }

        @Test
        void productIdIsNormalizedToUppercase() throws ParseException {
            AlertCommand cmd = AlertCommandParserTest.this.parser.parse("fine_topaz_gem buy 100k");

            assertEquals("FINE_TOPAZ_GEM", cmd.productId());
        }

        @Test
        void orderTypeAliases() throws ParseException {
            assertEquals(AlertType.BuyOrder, AlertCommandParserTest.this.parser.parse("ITEM b 100k").type());
            assertEquals(AlertType.BuyOrder, AlertCommandParserTest.this.parser.parse("ITEM buyorder 100k").type());
            assertEquals(AlertType.SellOffer, AlertCommandParserTest.this.parser.parse("ITEM s 100k").type());
            assertEquals(AlertType.SellOffer, AlertCommandParserTest.this.parser.parse("ITEM selloffer 100k").type());
            assertEquals(AlertType.InstaBuy, AlertCommandParserTest.this.parser.parse("ITEM ibuy 100k").type());
            assertEquals(AlertType.InstaBuy, AlertCommandParserTest.this.parser.parse("ITEM instabuy 100k").type());
            assertEquals(AlertType.InstaSell, AlertCommandParserTest.this.parser.parse("ITEM is 100k").type());
            assertEquals(AlertType.InstaSell, AlertCommandParserTest.this.parser.parse("ITEM instasell 100k").type());
        }

        @Test
        void complexExpression() throws ParseException {
            AlertCommand cmd = AlertCommandParserTest.this.parser.parse("ITEM buy 120_000_000 / 2");

            PriceExpression expected = new Binary(
                new Literal(120_000_000.0),
                BinaryOperator.Divide,
                new Literal(2.0)
            );

            assertEquals(expected, cmd.expr());
        }

        @Test
        void expressionWithParentheses() throws ParseException {
            AlertCommand cmd = AlertCommandParserTest.this.parser.parse("ITEM buy (2m + 10k) * 2");

            PriceExpression expected = new Binary(
                new Binary(new Literal(2_000_000.0), BinaryOperator.Add, new Literal(10_000.0)),
                BinaryOperator.Multiply,
                new Literal(2.0)
            );

            assertEquals(expected, cmd.expr());
        }

        @Test
        void numberFormattingVariations() throws ParseException {
            assertEquals(new Literal(120_123_123.3), AlertCommandParserTest.this.parser.parse("ITEM buy 120,123,123.3").expr());
            assertEquals(new Literal(120_123_123.3), AlertCommandParserTest.this.parser.parse("ITEM buy 120_123_123.3").expr());
            assertEquals(new Literal(15_300_000_000.0), AlertCommandParserTest.this.parser.parse("ITEM buy 15.3b").expr());
            assertEquals(new Literal(12_000_000.0), AlertCommandParserTest.this.parser.parse("ITEM buy 12m").expr());
            assertEquals(new Literal(10_000.0), AlertCommandParserTest.this.parser.parse("ITEM buy 10k").expr());
        }

        @Test
        void numberRounding() throws ParseException {
            assertEquals(
                new Literal(120_123_123.4),
                AlertCommandParserTest.this.parser.parse("ITEM buy 120_123_123.3791").expr()
            );
            assertEquals(new Literal(100.5), AlertCommandParserTest.this.parser.parse("ITEM buy 100.45").expr());
        }

        @Test
        void instaIdentifier() throws ParseException {
            AlertCommand cmd = AlertCommandParserTest.this.parser.parse("ITEM buy insta / 2");

            PriceExpression expected = new Binary(
                new Reference(ReferenceType.Insta),
                BinaryOperator.Divide,
                new Literal(2.0)
            );

            assertEquals(expected, cmd.expr());
        }

        @Test
        void expressionStartingWithIdentifier() throws ParseException {
            AlertCommand cmd = AlertCommandParserTest.this.parser.parse("ITEM buy order");

            PriceExpression expected = new Reference(ReferenceType.Order);
            assertEquals(expected, cmd.expr());
        }
    }
}
