package com.github.lutzluca.btrbz.core.widgets;

import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BazaarWidgetActionHandlerTest {
    @Test
    void rejectsPriceActionsFromStaleProductSideOrScreenWorkflow() {
        var source = signSession(10, "COOKIE", OrderType.Buy);
        assertTrue(BazaarWidgetActionHandler.validPriceSession(
            source, signSession(10, "COOKIE", OrderType.Buy)
        ));
        assertFalse(BazaarWidgetActionHandler.validPriceSession(
            source, signSession(11, "COOKIE", OrderType.Buy)
        ));
        assertFalse(BazaarWidgetActionHandler.validPriceSession(
            source, signSession(10, "DIAMOND", OrderType.Buy)
        ));
        assertFalse(BazaarWidgetActionHandler.validPriceSession(
            source, signSession(10, "COOKIE", OrderType.Sell)
        ));
    }

    @Test
    void presetActionRequiresSameWorkflowAndCurrentEligibility() {
        var source = presetSession(20);
        assertTrue(BazaarWidgetActionHandler.validPresetSession(source, presetSession(20), true));
        assertFalse(BazaarWidgetActionHandler.validPresetSession(source, presetSession(21), true));
        assertFalse(BazaarWidgetActionHandler.validPresetSession(source, presetSession(20), false));
    }

    private static BtrBzWidgetSession signSession(long id, String product, OrderType side) {
        return new BtrBzWidgetSession(
            id, BtrBzWidgetSession.HostKind.SIGN, Optional.empty(),
            Optional.of(BazaarMenuType.BuyOrderSetupPrice), true,
            Optional.of(product), Optional.of(side), 1, null
        );
    }

    private static BtrBzWidgetSession presetSession(long id) {
        return new BtrBzWidgetSession(
            id, BtrBzWidgetSession.HostKind.CONTAINER,
            Optional.of(BazaarMenuType.BuyOrderSetupVolume), Optional.empty(), true,
            Optional.empty(), Optional.empty(), 1, null
        );
    }
}
