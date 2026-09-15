package com.github.lutzluca.btrbz.core.alert;

import com.github.lutzluca.btrbz.data.BazaarData.MarketPrices;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PriceExpressionParserTest {

    private static final IndexedProduct PRODUCT = new IndexedProduct("ENCHANTED_DIAMOND", "Enchanted Diamond");

    @Test
    void advancedExpressionsUsePrecedenceParenthesesAndExplicitReferences() {
        var expression = PriceExpressionParser.parse("(buy_order + 2.5k) * 2 - sell_offer").get();
        var result = expression.resolve(prices(10_000.25, 11_000.5));

        Assertions.assertEquals(14_000.0, result.get());
    }

    @Test
    void draftResolutionTracksNewMarketPricesWithoutChangingTheDraft() {
        var draft = new AlertDraft(PRODUCT, AlertType.InstaBuy, "sell_offer * 1.01", true);

        var first = draft.resolve(prices(90.0, 100.0), 1_000L).get();
        var second = draft.resolve(prices(100.0, 120.0), 2_000L).get();

        Assertions.assertEquals(101.0, first.price());
        Assertions.assertEquals(121.2, second.price());
        Assertions.assertEquals(1_000L, first.timestamp());
        Assertions.assertEquals(2_000L, second.timestamp());
    }

    @Test
    void basicInputAcceptsFormattedNumbersWithoutLossyRounding() {
        var draft = new AlertDraft(PRODUCT, AlertType.InstaSell, "1.234567m", false);

        Assertions.assertEquals(1_234_567.0, draft.resolve(prices(1.0, 2.0), 3_000L).get().price());
        Assertions.assertEquals(0.5, new AlertDraft(PRODUCT, AlertType.InstaSell, ".5", false)
            .resolve(prices(1.0, 2.0), 3_000L)
            .get()
            .price());
        Assertions.assertEquals(1.0, new AlertDraft(PRODUCT, AlertType.InstaSell, "1.", false)
            .resolve(prices(1.0, 2.0), 3_000L)
            .get()
            .price());
        Assertions.assertTrue(new AlertDraft(PRODUCT, AlertType.InstaSell, "buy_order", false)
            .resolve(prices(1.0, 2.0), 3_000L)
            .isFailure());
    }

    @Test
    void rejectsMissingReferencesAndInvalidFinalValues() {
        Assertions.assertEquals(5.0, PriceExpressionParser.parse(".5 * sell_offer")
            .flatMap(expression -> expression.resolve(prices(1.0, 10.0)))
            .get());
        Assertions.assertTrue(PriceExpressionParser.parse("sell_offer + 1")
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
    void guardsUnboundedInputAndNesting() {
        Assertions.assertTrue(PriceExpressionParser.parse("1".repeat(257)).isFailure());
        Assertions.assertTrue(PriceExpressionParser.parse("(".repeat(34) + "1" + ")".repeat(34)).isFailure());
    }

    private static MarketPrices prices(Double buyOrder, Double sellOffer) {
        return new MarketPrices(Optional.ofNullable(buyOrder), Optional.ofNullable(sellOffer));
    }
}
