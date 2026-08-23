package com.github.lutzluca.btrbz.core.bazaariteminfo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.lutzluca.btrbz.core.bazaariteminfo.BazaarItemInfoViewData.Failure;
import com.github.lutzluca.btrbz.data.LiveProductSnapshot;
import com.github.lutzluca.btrbz.data.MarketSide;
import com.github.lutzluca.btrbz.data.PriceLevel;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.data.Totals;
import com.github.lutzluca.coflnet.HistoryRange;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BazaarItemInfoControllerTest {
    @Test
    void activationRequiresKeyRealSlotNonEmptyStackAndResolvedBazaarTag() {
        assertTrue(BazaarItemInfoController.canOpen(true, false, true, true, true));
        assertFalse(BazaarItemInfoController.canOpen(false, false, true, true, true));
        assertFalse(BazaarItemInfoController.canOpen(true, false, false, true, true));
        assertFalse(BazaarItemInfoController.canOpen(true, false, true, false, true));
        assertFalse(BazaarItemInfoController.canOpen(true, false, true, true, false));
    }

    @Test
    void focusedTextInputSuppressesTheHotkey() {
        assertFalse(BazaarItemInfoController.canOpen(true, true, true, true, true));
    }

    @Test
    void visibleRangesMapDirectlyToCoflnetPresetEndpoints() {
        assertTrue(BazaarItemInfoRange.Hour.sdkRange() == HistoryRange.Preset.HOUR);
        assertTrue(BazaarItemInfoRange.Day.sdkRange() == HistoryRange.Preset.DAY);
        assertTrue(BazaarItemInfoRange.Week.sdkRange() == HistoryRange.Preset.WEEK);
    }

    @Test
    void headerNameWidthFitsTheResponsivePanel() {
        assertEquals(246, BazaarItemInfoScreen.panelContentWidth(320));
        assertEquals(390, BazaarItemInfoScreen.panelContentWidth(480));
        assertEquals(727, BazaarItemInfoScreen.panelContentWidth(854));
        assertEquals(92, BazaarItemInfoScreen.headerNameWidth(320));
        assertTrue(BazaarItemInfoScreen.headerNameWidth(854) > 92);
    }

    @Test
    void sharedSummaryCalculatesTheVisibleInstantSpread() {
        var product = ProductIdentity.fromRuntime("Item", "ITEM", null);
        var buyOrders = new MarketSide(
            List.of(new PriceLevel(300_001.4, 1, 1)), new Totals.Unavailable());
        var sellOffers = new MarketSide(
            List.of(new PriceLevel(332_789.6, 1, 1)), new Totals.Unavailable());
        var live = new LiveProductSnapshot(product, Optional.empty(), buyOrders, sellOffers);

        var spread = BazaarItemInfoScreen.spreadSummary(live).orElseThrow();

        assertEquals(32_788.2, spread.amount(), 0.001);
        assertEquals(9.8526, spread.percent(), 0.001);
    }

    @Test
    void sharedSummaryCompactsOnlyLargePrices() {
        assertEquals("2.31M", BazaarItemInfoScreen.summaryPrice(2_308_120));
        assertEquals("332.8k", BazaarItemInfoScreen.summaryPrice(332_789.6));
        assertEquals("9,999.9", BazaarItemInfoScreen.summaryPrice(9_999.9));
    }

    @Test
    void spreadTextKeepsFractionalAndInvertedAmounts() {
        var product = ProductIdentity.fromRuntime("Item", "ITEM", null);
        var fractional = new LiveProductSnapshot(
            product,
            Optional.empty(),
            new MarketSide(List.of(new PriceLevel(10, 1, 1)), new Totals.Unavailable()),
            new MarketSide(List.of(new PriceLevel(10.1, 1, 1)), new Totals.Unavailable()));
        var inverted = new LiveProductSnapshot(
            product,
            Optional.empty(),
            new MarketSide(List.of(new PriceLevel(10.1, 1, 1)), new Totals.Unavailable()),
            new MarketSide(List.of(new PriceLevel(10, 1, 1)), new Totals.Unavailable()));

        assertEquals("0.1  (1.0%)", BazaarItemInfoScreen.spreadText(fractional));
        assertEquals("-0.1  (-1.0%)", BazaarItemInfoScreen.spreadText(inverted));
    }

    @Test
    void failedWeekComparisonMarksRetainedValuesAsCached() {
        assertEquals("7d cached", BazaarItemInfoScreen.comparisonLabel(
            new Failure<>("offline", Optional.empty()), "7d avg"));
        assertEquals("7d avg", BazaarItemInfoScreen.comparisonLabel(
            new BazaarItemInfoViewData.Success<>("value"), "7d avg"));
    }
}
