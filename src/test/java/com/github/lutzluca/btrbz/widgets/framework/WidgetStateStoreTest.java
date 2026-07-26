package com.github.lutzluca.btrbz.widgets.framework;

import com.github.lutzluca.btrbz.core.widgets.BazaarWidgetOptions;
import com.github.lutzluca.btrbz.core.widgets.WidgetsConfig;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WidgetStateStoreTest {
    @Test
    void fixedConfigStartsFromProductionDefaults() {
        var config = new WidgetsConfig();
        assertEquals(1.0, config.globalFineTuneScale);
        assertEquals(6, config.bazaarOrders.visibleOrders);
        assertEquals(200, config.bazaarOrders.contentWidth);
        assertEquals(BazaarWidgetOptions.HudMode.DETAILED, config.bazaarOrders.mode);
        assertEquals(BazaarWidgetOptions.TrackedSort.MANUAL, config.trackedOrders.sort);
        assertEquals(BazaarWidgetOptions.BookmarkSort.MANUAL, config.bookmarks.sort);
        assertEquals(15_000_000_000d, config.orderLimit.dailyLimit);
        assertEquals(75, config.orderLimit.warningThreshold);
        assertEquals(90, config.orderLimit.criticalThreshold);
        assertTrue(config.bookmarks.items.isEmpty());
        assertTrue(config.orderPresets.volumes.isEmpty());
    }

    @Test
    void mutationsWriteOnlyTheFixedYaclFieldsAndSaveCompletedChanges() {
        var config = new WidgetsConfig();
        var saves = new AtomicInteger();
        var store = new WidgetStateStore(() -> config, saves::incrementAndGet);
        var bookmarks = definition("btrbz:bookmarks");

        store.setActive(bookmarks, false);
        store.setWidgetScale(bookmarks, 3.0);
        store.setBackgroundColor(bookmarks, 0x7F102030);
        var placement = WidgetPlacement.topLeft(0.2, 0.3);
        store.setPlacement(bookmarks, WidgetScreenSession.DEFAULT_PLACEMENT_PROFILE, placement, true);

        assertFalse(config.bookmarks.enabled);
        assertEquals(WidgetStateStore.MAX_SCALE, config.bookmarks.scale);
        assertEquals(0x7F102030, config.bookmarks.background);
        assertEquals(placement, config.bookmarks.position);
        assertEquals(4, saves.get());
    }

    @Test
    void presetProfilesUseIndependentFixedPlacementFields() {
        var config = new WidgetsConfig();
        var store = new WidgetStateStore(() -> config, () -> {});
        var presets = WidgetDefinition.<Object, Void>builder(
                WidgetId.parse("btrbz:order_presets"), "Order Presets")
            .defaultPlacement(WidgetPlacement.topLeft(0.1, 0.2))
            .defaultPlacement("sign", WidgetPlacement.topLeft(0.7, 0.1))
            .dataProvider(ignored -> new Object())
            .previewDataProvider(ignored -> new Object())
            .componentFactory((ignored, context) -> null)
            .build();
        var container = WidgetPlacement.topLeft(0.25, 0.35);
        var sign = WidgetPlacement.topLeft(0.15, 0.2);

        store.setPlacement(presets, WidgetScreenSession.DEFAULT_PLACEMENT_PROFILE, container, false);
        store.setPlacement(presets, "sign", sign, false);

        assertEquals(container, config.orderPresets.containerPosition);
        assertEquals(sign, config.orderPresets.signPosition);
        assertEquals(container, store.placement(presets, WidgetScreenSession.DEFAULT_PLACEMENT_PROFILE));
        assertEquals(sign, store.placement(presets, "sign"));
    }

    private static WidgetDefinition<Object, Void> definition(String id) {
        return WidgetDefinition.<Object, Void>builder(WidgetId.parse(id), id)
            .dataProvider(ignored -> new Object())
            .previewDataProvider(ignored -> new Object())
            .componentFactory((ignored, context) -> null)
            .build();
    }
}
