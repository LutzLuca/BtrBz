package com.github.lutzluca.btrbz.core.widgets.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BazaarOrderCountPolicyTest {
    @Test
    void productionDefaultShowsSixRowsAndReservesOverflowLine() {
        int height = BazaarOrderCountPolicy.panelHeight(6, true, 11, 9);
        assertEquals(6, BazaarOrderCountPolicy.DEFAULT);
        assertEquals(6, BazaarOrderCountPolicy.visibleCount(
            BazaarOrderCountPolicy.DEFAULT, 8, height, 11, 9
        ));
    }

    @Test
    void configuredCountIsLimitedToTheSupportedRange() {
        assertEquals(1, BazaarOrderCountPolicy.configuredCount(-5));
        assertEquals(5, BazaarOrderCountPolicy.configuredCount(5));
        assertEquals(10, BazaarOrderCountPolicy.configuredCount(15));
    }

    @Test
    void desiredCountIsUsedWhenItFits() {
        assertEquals(5, BazaarOrderCountPolicy.visibleCount(5, 9, 200, 11, 9));
        assertEquals(4, BazaarOrderCountPolicy.visibleCount(10, 4, 200, 11, 9));
    }

    @Test
    void visibleCountShrinksWithoutChangingTheConfiguredPreference() {
        int threeRowsWithOverflow = BazaarOrderCountPolicy.panelHeight(3, true, 11, 9);

        assertEquals(3, BazaarOrderCountPolicy.visibleCount(10, 10, threeRowsWithOverflow, 11, 9));
        assertEquals(2, BazaarOrderCountPolicy.visibleCount(10, 10, threeRowsWithOverflow - 1, 11, 9));
        assertEquals(10, BazaarOrderCountPolicy.configuredCount(10));
    }

    @Test
    void atLeastOneOrderIsRetainedForTheFinalScaleFit() {
        assertEquals(1, BazaarOrderCountPolicy.visibleCount(10, 10, 1, 11, 9));
        assertEquals(0, BazaarOrderCountPolicy.visibleCount(10, 0, 1, 11, 9));
    }
}
