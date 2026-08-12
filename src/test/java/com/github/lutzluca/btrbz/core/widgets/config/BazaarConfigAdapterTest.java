package com.github.lutzluca.btrbz.core.widgets.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BazaarConfigAdapterTest {
    @Test
    void fixedDefaultsMapToTheCompleteImmutableOptionMatrix() {
        assertEquals(BazaarWidgetOptions.defaults(), BazaarConfigAdapter.read(new WidgetsConfig()));
    }

    @Test
    void eachFixedWidgetGroupMapsToItsOwnPresentationOptions() {
        var config = new WidgetsConfig();
        config.bazaarOrders.visibleOrders = 9;
        config.trackedOrders.visibleRows = 8;
        config.orderValue.contentWidth = 240;
        config.orderBookScreen.showHeader = false;
        config.orderBookPrice.showSell = false;
        config.bookmarks.showIndicators = false;
        config.orderPresets.clipboard = false;
        config.orderLimit.criticalThreshold = 95;
        config.priceDiff.showProduct = false;

        var options = BazaarConfigAdapter.read(config);
        assertEquals(9, options.hud().visibleOrders());
        assertEquals(8, options.trackedOrders().visibleRows());
        assertEquals(240, options.orderValue().contentWidth());
        assertFalse(options.orderBook().showHeader());
        assertFalse(options.embeddedOrderBook().showSell());
        assertTrue(options.orderBook().showItem());
        assertTrue(options.embeddedOrderBook().showItem());
        assertEquals(
            BazaarWidgetOptions.EmbeddedSideDisplay.Relevant,
            options.embeddedOrderBook().sideDisplay()
        );
        assertFalse(options.bookmarks().showIndicators());
        assertFalse(options.presets().clipboard());
        assertEquals(95, options.orderLimit().criticalThreshold());
        assertFalse(options.priceDiff().showProduct());
    }

}
