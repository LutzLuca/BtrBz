package com.github.lutzluca.btrbz.core.fliphelper;

import com.github.lutzluca.btrbz.core.trackedorders.TrackedOrderManager;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class FlipHelperTest {
    @Test
    void pendingCancellationRetainsSelectionButWorkflowResetClearsIt() {
        var data = new BazaarData();
        var context = new FlipProductContext();
        var selected = ProductIdentity.fromRuntime("Test Product", "TEST", null);
        try (var submissions = new FlipSubmissionTracker(); var orders = new TrackedOrderManager(data)) {
            var helper = new FlipHelper(data, context, submissions, orders);
            helper.onOrderClick(new OrderInfo.FilledOrderInfo(selected, "Test Product", OrderType.Buy,
                10, 100, 10, 10, 0));

            helper.cancelPendingFlip();

            Assertions.assertEquals(selected, context.getSelectedProduct().orElseThrow());

            helper.resetWorkflow();

            Assertions.assertTrue(context.getSelectedProduct().isEmpty());
        }
    }
}
