package com.github.lutzluca.btrbz.core.fliphelper;

import com.github.lutzluca.btrbz.data.ProductIdentity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class FlipSubmissionTrackerTest {
    @Test
    void submittedRuntimeIdAndPriceAreRetainedForNameOnlyChat() {
        var product = ProductIdentity.fromRuntime("UI Product", "TEST", null);
        try (var tracker = new FlipSubmissionTracker()) {
            tracker.recordSubmittedFlip(product, 109.9);
            var submitted = tracker.consume(ProductIdentity.fromName("UI Product")).orElseThrow();
            Assertions.assertEquals(product, submitted.product());
            Assertions.assertEquals(109.9, submitted.pricePerUnit());
            Assertions.assertTrue(tracker.consume(product).isEmpty());
        }
    }

    @Test
    void cancelledSubmissionsCannotSupplyAPriceToALaterFlip() {
        var product = ProductIdentity.fromName("Enchanted Carrot");
        try (var tracker = new FlipSubmissionTracker()) {
            tracker.recordSubmittedFlip(product, 100);
            tracker.clear();
            Assertions.assertTrue(tracker.consume(product).isEmpty());

            tracker.recordSubmittedFlip(product, 200);
            Assertions.assertEquals(200, tracker.consume(product).orElseThrow().pricePerUnit());
            Assertions.assertTrue(tracker.consume(product).isEmpty());
        }
    }
}
