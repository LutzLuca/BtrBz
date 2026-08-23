package com.github.lutzluca.btrbz.core.bazaariteminfo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.lutzluca.btrbz.core.config.Config;
import com.github.lutzluca.btrbz.core.widgets.orderbook.OrderBookWidgetConfig;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetDisplayOptions.NumberStyle;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

class BazaarItemInfoConfigMigrationTest {
    private final Gson gson = new Gson();

    @Test
    void migratesAnOldJsonShapeOnce() {
        MigrationShape config = this.gson.fromJson("""
            {
              "widgets": {
                "orderBookScreen": {
                  "frame": {"enabled": false},
                  "visibleRows": 17,
                  "numberStyle": "Compact",
                  "showOrderCount": false
                }
              }
            }
            """, MigrationShape.class);

        assertTrue(config.bazaarItemInfo.migrateFrom(config.widgets.orderBookScreen));
        assertEquals(17, config.bazaarItemInfo.visibleOrderBookRows);
        assertEquals(NumberStyle.Compact, config.bazaarItemInfo.volumeNumberStyle);
        assertFalse(config.bazaarItemInfo.showPerLevelOrderCount);
        assertFalse(config.bazaarItemInfo.showBazaarEntry);
        assertEquals(2, config.bazaarItemInfo.migrationVersion);
        assertFalse(config.bazaarItemInfo.migrateFrom(config.widgets.orderBookScreen));
    }

    @Test
    void upgradesVersionOnePreferencesWithoutOverwritingThem() {
        MigrationShape config = this.gson.fromJson("""
            {"bazaarItemInfo":{"showBuy":false,"visibleOrderBookRows":6,
              "showCumulativeVolume":false,"migrationVersion":1}}
            """, MigrationShape.class);

        assertTrue(config.bazaarItemInfo.migrateFrom(config.widgets.orderBookScreen));
        assertFalse(config.bazaarItemInfo.showBuy);
        assertTrue(config.bazaarItemInfo.showSell);
        assertEquals(6, config.bazaarItemInfo.visibleOrderBookRows);
        assertFalse(config.bazaarItemInfo.showCumulativeVolume);
        assertEquals(2, config.bazaarItemInfo.migrationVersion);
        assertFalse(config.bazaarItemInfo.migrateFrom(config.widgets.orderBookScreen));
    }

    @Test
    void newDefaultsReachTheCurrentMigrationVersion() {
        var config = new Config();

        assertTrue(config.bazaarItemInfo.migrateFrom(config.widgets.orderBookScreen));
        assertEquals(BazaarItemInfoRange.Day, config.bazaarItemInfo.selectedRange);
        assertEquals(10, config.bazaarItemInfo.visibleOrderBookRows);
        assertTrue(config.bazaarItemInfo.showCumulativeVolume);
        assertEquals(2, config.bazaarItemInfo.migrationVersion);
    }

    private static final class MigrationShape {
        private LegacyWidgets widgets = new LegacyWidgets();
        private BazaarItemInfoConfig bazaarItemInfo = new BazaarItemInfoConfig();
    }

    private static final class LegacyWidgets {
        private OrderBookWidgetConfig orderBookScreen = new OrderBookWidgetConfig();
    }
}
