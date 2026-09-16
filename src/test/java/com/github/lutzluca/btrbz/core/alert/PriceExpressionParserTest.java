package com.github.lutzluca.btrbz.core.alert;

import com.github.lutzluca.btrbz.core.alert.AlertType.Direction;
import com.github.lutzluca.btrbz.core.alert.AlertType.PriceSource;
import com.github.lutzluca.btrbz.data.BazaarData.MarketPrices;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PriceExpressionParserTest {

    private static final IndexedProduct PRODUCT = new IndexedProduct("ENCHANTED_DIAMOND", "Enchanted Diamond");
    private static final AlertType BUY_BELOW = new AlertType(PriceSource.Buy, Direction.Below);
    private static final AlertType SELL_ABOVE = new AlertType(PriceSource.Sell, Direction.Above);

    @ParameterizedTest
    @CsvSource(value = {
        "1234|1234", "14.4|14.4", ".5|0.5", "1.|1", "2.5m|2500000", ".5b|500000000",
        "1,234|1234", "12,345,678.9|12345678.9", "1_234_567.8|1234567.8",
        "1,234.5k|1234500", "1_000m|1000000000", "1,000.|1000"
    }, delimiter = '|')
    void acceptsNumbersWithConsistentThousandsGrouping(String input, double expected) {
        Assertions.assertEquals(expected, PriceExpressionParser.parse(input).get().resolve(prices(1.0, 2.0)).get());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "1,2m", "1,,000", "1__2", "1,000_000", "1_000,000", ",100", "_100", "100,", "100_",
        "1234,567", "1234_567", "1_00", "1,000,", "1_000_", "1.2,345", "1.2_345", "1,k", "1_m"
    })
    void rejectsMalformedSeparatorsBeforeRemovingThem(String input) {
        Assertions.assertTrue(PriceExpressionParser.parse(input).isFailure());
    }

    @Test
    void expressionsUsePrecedenceParenthesesAndShortReferences() {
        var expression = PriceExpressionParser.parse("(buy + 2.5k) * 2 - sell").get();
        var result = expression.resolve(prices(10_000.25, 11_000.5));

        Assertions.assertEquals(17_000.75, result.get());
    }

    @Test
    void draftResolutionTracksNewMarketPricesWithoutChangingTheDraft() {
        var draft = new AlertDraft(PRODUCT, BUY_BELOW, "buy * 1.01");

        var first = draft.resolve(prices(90.0, 100.0), 1_000L).get();
        var second = draft.resolve(prices(100.0, 120.0), 2_000L).get();

        Assertions.assertEquals(101.0, first.price());
        Assertions.assertEquals(121.2, second.price());
        Assertions.assertEquals(1_000L, first.timestamp());
        Assertions.assertEquals(2_000L, second.timestamp());
    }

    @Test
    void singleInputAcceptsLiteralsAndExpressions() {
        var draft = new AlertDraft(PRODUCT, SELL_ABOVE, "1.234567m");

        Assertions.assertEquals(1_234_567.0, draft.resolve(prices(1.0, 2.0), 3_000L).get().price());
        Assertions.assertEquals(0.5, new AlertDraft(PRODUCT, SELL_ABOVE, ".5")
            .resolve(prices(1.0, 2.0), 3_000L)
            .get()
            .price());
        Assertions.assertEquals(1.0, new AlertDraft(PRODUCT, SELL_ABOVE, "1.")
            .resolve(prices(1.0, 2.0), 3_000L)
            .get()
            .price());
        Assertions.assertEquals(2.2, new AlertDraft(PRODUCT, SELL_ABOVE, "buy * 1.1")
            .resolve(prices(1.0, 2.0), 3_000L)
            .get()
            .price());
    }

    @Test
    void rejectsMissingReferencesAndInvalidFinalValues() {
        Assertions.assertEquals(5.0, PriceExpressionParser.parse(".5 * buy")
            .flatMap(expression -> expression.resolve(prices(1.0, 10.0)))
            .get());
        Assertions.assertTrue(PriceExpressionParser.parse("buy + 1")
            .flatMap(expression -> expression.resolve(prices(10.0, null)))
            .isFailure());
        Assertions.assertTrue(PriceExpressionParser.parse("1 / 0")
            .flatMap(expression -> expression.resolve(prices(10.0, 20.0)))
            .isFailure());
        Assertions.assertTrue(PriceExpressionParser.parse("1 - 2")
            .flatMap(expression -> expression.resolve(prices(10.0, 20.0)))
            .isFailure());
    }

    @Test
    void roundsOnlyTheFinalThresholdToOneDecimal() {
        var draft = new AlertDraft(PRODUCT, BUY_BELOW, "buy * 1.1");
        var resolved = draft.resolve(prices(10.0, 13.05), 1_000L).get();
        Assertions.assertEquals(14.4, resolved.price());
        Assertions.assertEquals(0.5, new AlertDraft(PRODUCT, BUY_BELOW, "0.04 * 12")
            .resolve(prices(1.0, 2.0), 1_000L).get().price());
        Assertions.assertTrue(new AlertDraft(PRODUCT, BUY_BELOW, "0.04")
            .resolve(prices(1.0, 2.0), 1_000L).isFailure());
    }

    @Test
    void rejectsRemovedLongPriceReferences() {
        Assertions.assertTrue(PriceExpressionParser.parse("buy_order * 1.1").isFailure());
        Assertions.assertTrue(PriceExpressionParser.parse("sell_offer * 0.9").isFailure());
    }

    @Test
    void guardsUnboundedInputAndNesting() {
        Assertions.assertTrue(PriceExpressionParser.parse("1".repeat(257)).isFailure());
        Assertions.assertTrue(PriceExpressionParser.parse("(".repeat(34) + "1" + ")".repeat(34)).isFailure());
    }

    private static MarketPrices prices(Double buyOrder, Double sellOffer) {
        return new MarketPrices(Optional.ofNullable(buyOrder), Optional.ofNullable(sellOffer));
    }
}
