package com.github.lutzluca.btrbz.core.orderbook;

import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderBookScreenControllerTest {
    @Test
    void hookRequiresEnabledWidgetProductSlotAndSupportedMenu() {
        assertTrue(OrderBookScreenController.hookEligible(
            true, true, false, 8, BazaarMenuType.Item
        ));
        assertTrue(OrderBookScreenController.hookEligible(
            true, true, false, 8, BazaarMenuType.BuyOrderSetupVolume
        ));
        assertTrue(OrderBookScreenController.hookEligible(
            true, true, false, 8, BazaarMenuType.BuyOrderSetupPrice
        ));
        assertTrue(OrderBookScreenController.hookEligible(
            true, true, false, 8, BazaarMenuType.SellOfferSetup
        ));
        assertFalse(OrderBookScreenController.hookEligible(
            false, true, false, 8, BazaarMenuType.Item
        ));
        assertFalse(OrderBookScreenController.hookEligible(
            true, false, false, 8, BazaarMenuType.Item
        ));
        assertFalse(OrderBookScreenController.hookEligible(
            true, true, true, 8, BazaarMenuType.Item
        ));
        assertFalse(OrderBookScreenController.hookEligible(
            true, true, false, 7, BazaarMenuType.Item
        ));
        assertFalse(OrderBookScreenController.hookEligible(
            true, true, false, 8, BazaarMenuType.Main
        ));
    }
}
