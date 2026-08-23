package com.github.lutzluca.btrbz.core.bazaariteminfo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.lutzluca.btrbz.core.widgets.ui.WidgetDisplayOptions.NumberStyle;
import org.junit.jupiter.api.Test;

class BazaarItemInfoConfigTest {
    @Test
    void usesCleanItemInfoDefaults() {
        var config = new BazaarItemInfoConfig();

        assertEquals(BazaarItemInfoRange.Day, config.selectedRange);
        assertTrue(config.showBuy);
        assertTrue(config.showSell);
        assertTrue(config.showBands);
        assertEquals(BazaarItemInfoConfig.ActivityMode.IntervalItems, config.activityMode);
        assertEquals(10, config.visibleOrderBookRows);
        assertEquals(NumberStyle.Exact, config.volumeNumberStyle);
        assertTrue(config.showPerLevelOrderCount);
        assertTrue(config.showCumulativeVolume);
        assertTrue(config.showBazaarEntry);
    }
}
