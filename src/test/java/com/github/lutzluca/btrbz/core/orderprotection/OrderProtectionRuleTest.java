package com.github.lutzluca.btrbz.core.orderprotection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.lutzluca.btrbz.core.orderprotection.OrderProtectionRule.Settings;
import com.github.lutzluca.btrbz.core.orderprotection.OrderProtectionRule.Violation;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OrderProtectionRuleTest {

    private static final Settings DEFAULT_SETTINGS = new Settings(true, true, 15, 15);

    @Nested
    class OpposingOrderProtection {

        @Test
        void blocksBuyOrderAtBestSellOffer() {
            var result = evaluate(OrderType.Buy, 105, 100, 105, DEFAULT_SETTINGS);

            assertBlockedBy(result, Violation.OpposingPrice);
            assertEquals(105, result.referencePrice());
        }

        @Test
        void blocksBuyOrderAboveBestSellOffer() {
            assertBlockedBy(evaluate(OrderType.Buy, 110, 100, 105, DEFAULT_SETTINGS), Violation.OpposingPrice);
        }

        @Test
        void blocksSellOfferAtBestBuyOrder() {
            var result = evaluate(OrderType.Sell, 100, 100, 105, DEFAULT_SETTINGS);

            assertBlockedBy(result, Violation.OpposingPrice);
            assertEquals(100, result.referencePrice());
        }

        @Test
        void blocksSellOfferBelowBestBuyOrder() {
            assertBlockedBy(evaluate(OrderType.Sell, 95, 100, 105, DEFAULT_SETTINGS), Violation.OpposingPrice);
        }

        @Test
        void crossingProtectionIsIndependentOfPercentageExemption() {
            var onlyOpposing = new Settings(true, false, 0, 0);

            assertBlockedBy(evaluate(OrderType.Buy, 0.6, 0.5, 0.6, onlyOpposing), Violation.OpposingPrice);
            assertBlockedBy(evaluate(OrderType.Sell, 0.5, 0.5, 0.6, onlyOpposing), Violation.OpposingPrice);
        }

        @Test
        void crossingRuleHasPriorityOverOtherViolations() {
            assertBlockedBy(evaluate(OrderType.Buy, 120, 100, 110, DEFAULT_SETTINGS), Violation.OpposingPrice);
        }

        @Test
        void crossingProtectionCanBeDisabled() {
            var noOpposing = new Settings(false, true, 15, 15);

            assertAllowed(evaluate(OrderType.Buy, 105, 100, 105, noOpposing));
            assertAllowed(evaluate(OrderType.Sell, 100, 100, 105, noOpposing));
        }
    }

    @Nested
    class SameSidePercentageProtection {

        @Test
        void blocksBuyOrderAtPercentageBoundary() {
            var result = evaluate(OrderType.Buy, 115, 100, 200, DEFAULT_SETTINGS);

            assertBlockedBy(result, Violation.Percentage);
            assertEquals(15, result.delta());
            assertEquals(15, result.percentage());
        }

        @Test
        void blocksSellOfferAtPercentageBoundary() {
            var result = evaluate(OrderType.Sell, 85, 10, 100, DEFAULT_SETTINGS);

            assertBlockedBy(result, Violation.Percentage);
            assertEquals(15, result.delta());
            assertEquals(15, result.percentage());
        }

        @Test
        void blocksLargerChangesBeyondTheBoundary() {
            assertBlockedBy(evaluate(OrderType.Buy, 130, 100, 200, DEFAULT_SETTINGS), Violation.Percentage);
            assertBlockedBy(evaluate(OrderType.Sell, 70, 10, 100, DEFAULT_SETTINGS), Violation.Percentage);
        }

        @Test
        void allowsChangesBelowPercentageBoundary() {
            assertAllowed(evaluate(OrderType.Buy, 114.9, 100, 200, DEFAULT_SETTINGS));
            assertAllowed(evaluate(OrderType.Sell, 85.1, 10, 100, DEFAULT_SETTINGS));
        }

        @Test
        void allowsEqualAndLessCompetitivePrices() {
            assertAllowed(evaluate(OrderType.Buy, 100, 100, 200, DEFAULT_SETTINGS));
            assertAllowed(evaluate(OrderType.Buy, 99, 100, 200, DEFAULT_SETTINGS));
            assertAllowed(evaluate(OrderType.Sell, 100, 10, 100, DEFAULT_SETTINGS));
            assertAllowed(evaluate(OrderType.Sell, 101, 10, 100, DEFAULT_SETTINGS));
        }

        @Test
        void allowsOneTickImprovementOnCheapItems() {
            assertAllowed(evaluate(OrderType.Buy, 0.6, 0.5, 1, DEFAULT_SETTINGS));
            assertAllowed(evaluate(OrderType.Sell, 0.5, 0.1, 0.6, DEFAULT_SETTINGS));
        }

        @Test
        void allowsOneTickImprovementOnNormalPriceItems() {
            assertAllowed(evaluate(OrderType.Buy, 100.1, 100, 200, DEFAULT_SETTINGS));
            assertAllowed(evaluate(OrderType.Sell, 99.9, 10, 100, DEFAULT_SETTINGS));
        }

        @Test
        void determinesOneTickBoundaryFromCanonicalizedPrices() {
            assertTrue(0.8 - 0.7 > 0.1);

            assertAllowed(evaluate(OrderType.Buy, 0.8, 0.7, 1, DEFAULT_SETTINGS));
            assertAllowed(evaluate(OrderType.Sell, 0.7, 0.1, 0.8, DEFAULT_SETTINGS));
        }

        @Test
        void stillBlocksLargerCheapItemJump() {
            assertBlockedBy(evaluate(OrderType.Buy, 0.7, 0.5, 1, DEFAULT_SETTINGS), Violation.Percentage);
            assertBlockedBy(evaluate(OrderType.Sell, 0.4, 0.1, 0.6, DEFAULT_SETTINGS), Violation.Percentage);
        }

        @Test
        void allowsLargeCoinChangeBelowPercentageLimit() {
            assertAllowed(evaluate(OrderType.Buy, 210_000_000, 200_000_000, 300_000_000, DEFAULT_SETTINGS));
        }

        @Test
        void percentageProtectionCanBeDisabled() {
            var noPercentage = new Settings(true, false, 15, 15);

            assertAllowed(evaluate(OrderType.Buy, 130, 100, 200, noPercentage));
            assertAllowed(evaluate(OrderType.Sell, 70, 10, 100, noPercentage));
        }
    }

    @Nested
    class MissingAndInvalidPrices {

        @Test
        void allowsOrderWhenRelevantMarketSideIsMissing() {
            var result = OrderProtectionRule.evaluate(
                OrderType.Buy,
                100,
                Optional.empty(),
                Optional.of(200.0),
                DEFAULT_SETTINGS);

            assertAllowed(result);
        }

        @Test
        void allowsOrderWhenOpposingSideIsMissing() {
            var result = OrderProtectionRule.evaluate(
                OrderType.Buy,
                100,
                Optional.of(90.0),
                Optional.empty(),
                DEFAULT_SETTINGS);

            assertAllowed(result);
        }

        @Test
        void ignoresInvalidMarketPrice() {
            var result = OrderProtectionRule.evaluate(
                OrderType.Sell,
                100,
                Optional.of(Double.NaN),
                Optional.empty(),
                DEFAULT_SETTINGS);

            assertAllowed(result);
        }

        @Test
        void ignoresNonPositiveMarketPrice() {
            assertAllowed(OrderProtectionRule.evaluate(
                OrderType.Buy,
                100,
                Optional.of(0.0),
                Optional.of(200.0),
                DEFAULT_SETTINGS));
            assertAllowed(OrderProtectionRule.evaluate(
                OrderType.Sell,
                100,
                Optional.of(90.0),
                Optional.of(-5.0),
                DEFAULT_SETTINGS));
        }

        @Test
        void blocksInvalidProposedPrice() {
            assertBlockedBy(
                OrderProtectionRule.evaluate(
                    OrderType.Buy,
                    0,
                    Optional.empty(),
                    Optional.empty(),
                    DEFAULT_SETTINGS),
                Violation.InvalidPrice);
        }

        @Test
        void blocksNegativeAndNaNProposedPrice() {
            assertBlockedBy(
                OrderProtectionRule.evaluate(
                    OrderType.Sell,
                    -10,
                    Optional.of(90.0),
                    Optional.of(100.0),
                    DEFAULT_SETTINGS),
                Violation.InvalidPrice);
            assertBlockedBy(
                OrderProtectionRule.evaluate(
                    OrderType.Buy,
                    Double.NaN,
                    Optional.of(90.0),
                    Optional.of(100.0),
                    DEFAULT_SETTINGS),
                Violation.InvalidPrice);
        }
    }

    private static OrderProtectionRule.Result evaluate(
        OrderType type,
        double proposed,
        double bestBuy,
        double bestSell,
        Settings settings
    ) {
        return OrderProtectionRule.evaluate(type, proposed, Optional.of(bestBuy), Optional.of(bestSell), settings);
    }

    private static void assertAllowed(OrderProtectionRule.Result result) {
        assertFalse(result.blocked());
        assertTrue(result.violation().isEmpty());
    }

    private static void assertBlockedBy(OrderProtectionRule.Result result, Violation violation) {
        assertTrue(result.blocked());
        assertEquals(Optional.of(violation), result.violation());
    }
}
