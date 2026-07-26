package com.github.lutzluca.btrbz.core.widgets.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BazaarConfigAdapterTest {
    @Test
    void fixedDefaultsMapToTheCompleteImmutableOptionMatrix() {
        var defaults = BazaarWidgetOptions.defaults();

        assertEquals(defaults, BazaarConfigAdapter.read(new WidgetsConfig()));
        assertTrue(defaults.trackedOrders().fitToContent());
        assertTrue(defaults.trackedOrders().hideWhenEmpty());
        assertTrue(defaults.bookmarks().fitToContent());
        assertTrue(defaults.bookmarks().hideWhenEmpty());
    }

    @Test
    void eachFixedWidgetGroupMapsToItsOwnPresentationOptions() {
        var config = new WidgetsConfig();
        config.bazaarOrders.visibleOrders = 9;
        config.trackedOrders.visibleRows = 8;
        config.trackedOrders.fitToContent = false;
        config.trackedOrders.hideWhenEmpty = false;
        config.orderValue.contentWidth = 240;
        config.orderBookScreen.showHeader = false;
        config.orderBookPrice.showSell = false;
        config.bookmarks.showIndicators = false;
        config.bookmarks.fitToContent = false;
        config.bookmarks.hideWhenEmpty = false;
        config.orderPresets.clipboard = false;
        config.orderLimit.criticalThreshold = 95;
        config.priceDiff.showProduct = false;

        var options = BazaarConfigAdapter.read(config);
        assertEquals(9, options.hud().visibleOrders());
        assertEquals(8, options.trackedOrders().visibleRows());
        assertFalse(options.trackedOrders().fitToContent());
        assertFalse(options.trackedOrders().hideWhenEmpty());
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
        assertFalse(options.bookmarks().fitToContent());
        assertFalse(options.bookmarks().hideWhenEmpty());
        assertFalse(options.presets().clipboard());
        assertEquals(95, options.orderLimit().criticalThreshold());
        assertFalse(options.priceDiff().showProduct());
    }

}
