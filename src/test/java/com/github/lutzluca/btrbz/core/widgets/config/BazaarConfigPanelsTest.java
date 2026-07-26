package com.github.lutzluca.btrbz.core.widgets.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BazaarConfigPanelsTest {
    @Test
    void humanizesPascalCaseEnumValues() {
        assertEquals("Used Limit", BazaarConfigPanels.enumLabel(BazaarWidgetOptions.LimitDisplay.UsedLimit));
        assertEquals(
            "Price Gap and Queue",
            BazaarConfigPanels.enumLabel(BazaarWidgetOptions.UndercutDetail.PriceGapAndQueue)
        );
    }
}
