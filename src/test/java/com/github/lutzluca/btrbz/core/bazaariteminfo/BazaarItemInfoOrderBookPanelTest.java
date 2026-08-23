package com.github.lutzluca.btrbz.core.bazaariteminfo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.lutzluca.btrbz.core.widgets.ui.WidgetDisplayOptions.NumberStyle;
import com.github.lutzluca.btrbz.data.PriceLevel;
import com.github.lutzluca.btrbz.data.Totals;
import java.util.List;
import org.junit.jupiter.api.Test;

class BazaarItemInfoOrderBookPanelTest {
    @Test
    void responsiveDecisionNeverScalesThePanel() {
        assertTrue(BazaarItemInfoOrderBookPanel.useStacked(399, 200));
        assertFalse(BazaarItemInfoOrderBookPanel.useStacked(406, 200));
        assertTrue(BazaarItemInfoOrderBookPanel.useStacked(BazaarItemInfoScreen.panelContentWidth(320), 200));
    }

    @Test
    void clipboardPriceUsesOneDecimalWithoutGrouping() {
        assertEquals("1234567.9", BazaarItemInfoOrderBookPanel.clipboardPrice(1_234_567.85));
    }

    @Test
    void formatsExactCompactAndUnavailableTotals() {
        assertEquals("12,345", BazaarItemInfoOrderBookPanel.number(12_345, NumberStyle.Exact));
        assertEquals("12.3k", BazaarItemInfoOrderBookPanel.number(12_345, NumberStyle.Compact));
        assertEquals("12,345 items, 7 orders",
            BazaarItemInfoOrderBookPanel.totalsText(new Totals.Available(7, 12_345), NumberStyle.Exact));
        assertEquals("Totals unavailable",
            BazaarItemInfoOrderBookPanel.totalsText(new Totals.Unavailable(), NumberStyle.Exact));
    }

    @Test
    void cumulativeVolumeRunsFromBestPriceOutwardAndSaturates() {
        assertEquals(
            List.of(5L, 12L, Long.MAX_VALUE),
            BazaarItemInfoOrderBookPanel.accumulatedItems(List.of(
                new PriceLevel(100, 5, 1),
                new PriceLevel(99, 7, 1),
                new PriceLevel(98, Long.MAX_VALUE, 1))));
    }

    @Test
    void itemDepthBarsScaleAgainstTheLargestVisibleLevel() {
        assertEquals(0, BazaarItemInfoOrderBookPanel.itemFraction(0, 100));
        assertEquals(0.25, BazaarItemInfoOrderBookPanel.itemFraction(25, 100));
        assertEquals(1, BazaarItemInfoOrderBookPanel.itemFraction(100, 100));
        assertEquals(1, BazaarItemInfoOrderBookPanel.itemFraction(150, 100));
    }

    @Test
    void wideLayoutUsesAvailableDepthWhileStackedLayoutKeepsConfiguredRows() {
        assertEquals(30, BazaarItemInfoOrderBookPanel.effectiveVisibleRows(false, 10, 500, 11, 30));
        assertEquals(10, BazaarItemInfoOrderBookPanel.effectiveVisibleRows(true, 10, 500, 11, 30));
        assertEquals(10, BazaarItemInfoOrderBookPanel.effectiveVisibleRows(false, 10, 500, 11, 4));
    }

    @Test
    void orderRowsLeaveThreePixelsAroundTheNativeFont() {
        assertEquals(15, BazaarItemInfoOrderBookPanel.orderRowHeight(9));
    }

    @Test
    void headingsRowsAndHitTestingShareContiguousColumnBoundaries() {
        var layout = BazaarItemInfoOrderColumns.layout(500, true, true);

        assertEquals(layout.price().end(), layout.items().start());
        assertEquals(layout.items().end(), layout.cumulative().start());
        assertEquals(layout.cumulative().end(), layout.orders().start());
        assertTrue(layout.price().end() < layout.items().end());
        assertTrue(layout.items().end() < layout.cumulative().end());
        assertTrue(layout.cumulative().end() < layout.orders().end());
    }
}
