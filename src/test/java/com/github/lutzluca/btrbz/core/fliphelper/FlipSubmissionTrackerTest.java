package com.github.lutzluca.btrbz.core.fliphelper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.lutzluca.btrbz.data.ProductIdentity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FlipSubmissionTrackerTest {

    private static final ProductIdentity ENCHANTED_SUGAR = ProductIdentity.fromRuntime(
        "Enchanted Sugar",
        "ENCHANTED_SUGAR",
        null
    );

    @Nested
    @DisplayName("Priced flips")
    class PricedFlips {

        @Test
        void retainsSubmittedPrice() {
            try (var tracker = new FlipSubmissionTracker()) {
                tracker.recordSubmittedFlip(ENCHANTED_SUGAR, 999.9);

                var match = tracker.consume(ProductIdentity.fromName("Enchanted Sugar"));

                assertTrue(match.isPresent());
                assertEquals(999.9, match.orElseThrow().pricePerUnit());
            }
        }

        @Test
        void matchesByDisplayNameWithoutResolvedProduct() {
            try (var tracker = new FlipSubmissionTracker()) {
                tracker.recordSubmittedFlip(ENCHANTED_SUGAR, 999.9);

                var match = tracker.consume(ProductIdentity.fromName("Enchanted Sugar"));

                assertTrue(match.isPresent());
            }
        }

        @Test
        void leavesDifferentProductPending() {
            var cocoaBeans = ProductIdentity.fromRuntime("Cocoa Beans", "INK_SACK:3", null);
            try (var tracker = new FlipSubmissionTracker()) {
                tracker.recordSubmittedFlip(ENCHANTED_SUGAR, 999.9);

                assertTrue(tracker.consume(cocoaBeans).isEmpty());
                assertTrue(tracker.consume(ProductIdentity.fromName("Enchanted Sugar")).isPresent());
            }
        }
    }
}
