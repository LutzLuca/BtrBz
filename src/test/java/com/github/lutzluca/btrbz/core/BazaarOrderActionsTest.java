package com.github.lutzluca.btrbz.core;

import com.github.lutzluca.btrbz.data.BazaarData;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class BazaarOrderActionsTest {
    @Test
    void suspendingCancelsUnsentWorkButOnlyFullResetForgetsTheCancelledOrderShortcut() throws Exception {
        var actions = new BazaarOrderActions(new BazaarData());
        var context = new BazaarOrderActions.CancelledOrderContext(null, "Test Product");
        this.set(actions, "shouldReopenBazaar", true);
        this.set(actions, "remainingOrderAmount", 10);
        this.set(actions, "activeBuyOrderContext", context);
        this.set(actions, "lastCancelledBuyOrder", context);
        this.set(actions, "hideCancelledOrderButton", true);

        actions.cancelPendingActions();

        Assertions.assertEquals(false, this.get(actions, "shouldReopenBazaar"));
        Assertions.assertNull(this.get(actions, "remainingOrderAmount"));
        Assertions.assertNull(this.get(actions, "activeBuyOrderContext"));
        Assertions.assertSame(context, this.get(actions, "lastCancelledBuyOrder"));
        Assertions.assertEquals(true, this.get(actions, "hideCancelledOrderButton"));

        actions.resetSession();
        actions.resetSession();

        Assertions.assertNull(this.get(actions, "lastCancelledBuyOrder"));
        Assertions.assertEquals(false, this.get(actions, "hideCancelledOrderButton"));
    }

    private Object get(BazaarOrderActions actions, String name) throws Exception {
        return this.field(name).get(actions);
    }

    private void set(BazaarOrderActions actions, String name, Object value) throws Exception {
        this.field(name).set(actions, value);
    }

    private Field field(String name) throws Exception {
        var field = BazaarOrderActions.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
