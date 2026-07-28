package com.github.lutzluca.btrbz.core.widgets;

import com.github.lutzluca.btrbz.core.widgets.bookmarks.BookmarksWidgetConfig;
import com.github.lutzluca.btrbz.core.widgets.hud.BazaarOrdersWidgetConfig;
import com.github.lutzluca.btrbz.core.widgets.trackedorders.TrackedOrdersWidgetConfig;
import com.github.lutzluca.btrbz.core.widgets.config.WidgetStateStore;
import com.github.lutzluca.btrbz.core.widgets.config.WidgetsConfig;
import com.github.lutzluca.btrbz.core.widgets.layout.WidgetPlacement;
import com.github.lutzluca.btrbz.core.widgets.layout.WidgetScaleResolver;
import com.github.lutzluca.btrbz.core.widgets.presets.OrderPresetsWidgetConfig;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetDisplayOptions;
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
        assertEquals(BazaarOrdersWidgetConfig.HudMode.Detailed, config.bazaarOrders.mode);
        assertEquals(TrackedOrdersWidgetConfig.TrackedSort.Manual, config.trackedOrders.sort);
        assertEquals(BookmarksWidgetConfig.BookmarkSort.Manual, config.bookmarks.sort);
        assertEquals(15_000_000_000d, config.orderLimit.dailyLimit);
        assertTrue(config.bookmarks.items.isEmpty());
        assertTrue(config.orderPresets.volumes.isEmpty());
    }

    @Test
    void mutationsUseDefinitionOwnedFrameAndSaveCompletedChanges() {
        var config = new WidgetsConfig();
        var saves = new AtomicInteger();
        var store = new WidgetStateStore(() -> config, saves::incrementAndGet);
        var bookmarks = bookmarksDefinition(() -> config.bookmarks);
        var placement = WidgetPlacement.topLeft(0.2, 0.3);

        store.setActive(bookmarks, false);
        store.setWidgetScale(bookmarks, 3.0);
        store.setBackgroundColor(bookmarks, 0x7F102030);
        store.setPlacement(bookmarks, "default", placement, true);

        assertFalse(config.bookmarks.frame.enabled);
        assertEquals(WidgetScaleResolver.MAX_SCALE, config.bookmarks.frame.scale);
        assertEquals(0x7F102030, config.bookmarks.frame.background);
        assertEquals(placement, config.bookmarks.frame.placements.get("default"));
        assertEquals(4, saves.get());
    }

    @Test
    void placementProfilesAreResolvedWithoutWidgetIdSwitches() {
        var config = new WidgetsConfig();
        var store = new WidgetStateStore(() -> config, () -> {});
        var definition = WidgetDefinition.<Object, OrderPresetsWidgetConfig, Void>builder(
                WidgetId.parse("btrbz:order_presets"), "Order Presets")
            .config(() -> config.orderPresets, OrderPresetsWidgetConfig::new,
                value -> value.frame, OrderPresetsWidgetConfig::resetPreferences)
            .runtimeData(_ -> new Object())
            .preview(() -> null)
            .viewFactory(() -> null)
            .placementProfile("sign", "Sign")
            .build();
        var container = WidgetPlacement.topLeft(0.25, 0.35);
        var sign = WidgetPlacement.topLeft(0.15, 0.2);

        store.setPlacement(definition, "default", container, false);
        store.setPlacement(definition, "sign", sign, false);

        assertEquals(container, config.orderPresets.frame.placements.get("default"));
        assertEquals(sign, config.orderPresets.frame.placements.get("sign"));
        assertEquals(container, store.placement(definition, "default"));
        assertEquals(sign, store.placement(definition, "sign"));
    }

    @Test
    void definitionResolvesAReplacedConfigObject() {
        var holder = new BookmarksWidgetConfig[] {new BookmarksWidgetConfig()};
        var definition = bookmarksDefinition(() -> holder[0]);
        var replacement = new BookmarksWidgetConfig();
        replacement.frame.enabled = false;
        holder[0] = replacement;
        assertSame(replacement, definition.config());
        assertFalse(definition.frame().enabled);
    }

    private static WidgetDefinition<Object, BookmarksWidgetConfig, Void> bookmarksDefinition(
        java.util.function.Supplier<BookmarksWidgetConfig> supplier
    ) {
        return WidgetDefinition.<Object, BookmarksWidgetConfig, Void>builder(
                WidgetId.parse("btrbz:bookmarks"), "Bookmarks")
            .config(supplier, BookmarksWidgetConfig::new,
                value -> value.frame, BookmarksWidgetConfig::resetPreferences)
            .runtimeData(_ -> new Object())
            .preview(() -> null)
            .viewFactory(() -> null)
            .build();
    }
}
