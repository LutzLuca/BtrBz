package com.github.lutzluca.btrbz.core.widgets;

import com.github.lutzluca.btrbz.core.widgets.bookmarks.BookmarksWidgetConfig;
import com.github.lutzluca.btrbz.core.widgets.hud.BazaarOrdersWidgetConfig;
import com.github.lutzluca.btrbz.core.widgets.trackedorders.TrackedOrdersWidgetConfig;
import com.github.lutzluca.btrbz.core.widgets.config.WidgetStateStore;
import com.github.lutzluca.btrbz.core.widgets.config.WidgetConfigHandle;
import com.github.lutzluca.btrbz.core.widgets.cache.CacheDependencies;
import com.github.lutzluca.btrbz.core.widgets.cache.WidgetDataSource;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import com.github.lutzluca.btrbz.core.widgets.config.WidgetsConfig;
import com.github.lutzluca.btrbz.core.widgets.layout.WidgetPlacement;
import com.github.lutzluca.btrbz.core.widgets.layout.WidgetScaleResolver;
import com.github.lutzluca.btrbz.core.widgets.presets.OrderPresetsWidgetConfig;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetDisplayOptions;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WidgetStateStoreTest {
    @Test
    void fixedConfigStartsFromProductionDefaults() {
        var config = new WidgetsConfig();
        assertEquals(1.0, config.globalFineTuneScale);
        assertEquals(WidgetsConfig.DEFAULT_BACKGROUND, config.globalBackground);
        assertEquals(4, config.bazaarOrders.visibleOrders);
        assertEquals(200, config.bazaarOrders.contentWidth);
        assertEquals(BazaarOrdersWidgetConfig.HudMode.Detailed, config.bazaarOrders.mode);
        assertEquals(TrackedOrdersWidgetConfig.TrackedSort.Manual, config.trackedOrders.sort);
        assertEquals(200, config.trackedOrders.contentWidth);
        assertEquals(5, config.trackedOrders.visibleRows);
        assertEquals(BookmarksWidgetConfig.BookmarkSort.Manual, config.bookmarks.sort);
        assertEquals(200, config.bookmarks.contentWidth);
        assertEquals(5, config.bookmarks.visibleRows);
        assertEquals(400, config.orderBookScreen.contentWidth);
        assertEquals(10, config.orderBookScreen.visibleRows);
        assertEquals(400, config.orderBookPrice.contentWidth);
        assertEquals(8, config.orderBookPrice.visibleRows);
        assertEquals(50, config.orderPresets.contentWidth);
        assertEquals(5, config.orderPresets.visibleRows);
        assertEquals(WidgetDisplayOptions.NumberStyle.Compact, config.orderLimit.numberStyle);
        assertTrue(config.orderLimit.frame.enabled);
        assertFalse(config.orderPresets.showDisabled);
        assertEquals(210, config.managerPanelWidth);
        assertEquals(85, config.managerPanelHeightPercent);
        assertFalse(config.runtimeDragging);
        assertTrue(config.managerLauncherVisible);
        assertEquals(
            WidgetPlacement.topLeft(0.0, 1.0),
            WidgetsConfig.DEFAULT_MANAGER_LAUNCHER_POSITION
        );
        assertEquals(WidgetsConfig.DEFAULT_MANAGER_LAUNCHER_POSITION, config.managerLauncherPosition);
        assertEquals(15_000_000_000d, config.orderLimit.dailyLimit);
        assertTrue(config.bookmarks.items.isEmpty());
        assertTrue(config.orderPresets.volumes.isEmpty());
    }

    @Test
    void managerPreferencesPersistThroughTheSharedStore() {
        var config = new WidgetsConfig();
        var saves = new AtomicInteger();
        var store = new WidgetStateStore(() -> config, saves::incrementAndGet);

        store.setManagerPanelWidth(190, true);
        store.setManagerPanelHeightPercent(70, true);
        store.setRuntimeDragging(true, true);
        store.setManagerLauncherVisible(false, true);
        var launcherPosition = WidgetPlacement.topLeft(0.4, 0.2);
        store.setManagerLauncherPosition(launcherPosition, true);

        assertEquals(190, config.managerPanelWidth);
        assertEquals(70, config.managerPanelHeightPercent);
        assertTrue(config.runtimeDragging);
        assertFalse(config.managerLauncherVisible);
        assertEquals(launcherPosition, config.managerLauncherPosition);
        assertEquals(5, saves.get());
    }

    @Test
    void resettingTheManagerLauncherRestoresOnlyItsPosition() {
        var config = new WidgetsConfig();
        var saves = new AtomicInteger();
        var store = new WidgetStateStore(() -> config, saves::incrementAndGet);
        config.managerLauncherVisible = false;
        config.managerLauncherPosition = WidgetPlacement.topLeft(0.4, 0.2);

        store.resetManagerLauncherPosition(true);

        assertFalse(config.managerLauncherVisible);
        assertEquals(WidgetPlacement.topLeft(0.0, 1.0), config.managerLauncherPosition);
        assertEquals(1, saves.get());
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
    void frameTokensSeparatePerWidgetGlobalAndManagerOnlyState() {
        var config = new WidgetsConfig();
        var store = new WidgetStateStore(() -> config, () -> {});
        var bookmarks = bookmarksDefinition(() -> config.bookmarks);
        var otherId = WidgetId.parse("btrbz:other");
        var bookmarksToken = store.frameChanges(bookmarks.getId());
        var otherToken = store.frameChanges(otherId);

        store.setPlacement(bookmarks, "default", WidgetPlacement.topLeft(0.2, 0.3), false);

        assertEquals(1, bookmarksToken.revision());
        assertEquals(0, otherToken.revision());
        assertEquals(0, store.globalFrameChanges().revision());

        store.setGlobalFineTuneScale(1.2, false);
        assertEquals(1, store.globalFrameChanges().revision());
        assertEquals(1, bookmarksToken.revision());
        assertEquals(0, otherToken.revision());

        store.setManagerPanelWidth(180, false);
        assertEquals(1, store.globalFrameChanges().revision());
        assertEquals(1, bookmarksToken.revision());
    }

    @Test
    void placementProfilesAreResolvedWithoutWidgetIdSwitches() {
        var config = new WidgetsConfig();
        var store = new WidgetStateStore(() -> config, () -> {});
        var definition = WidgetDefinition.<Object, OrderPresetsWidgetConfig, Void>builder(
                WidgetId.parse("btrbz:order_presets"), "Order Presets")
            .config(new WidgetConfigHandle<>(
                WidgetId.parse("btrbz:order_presets"), () -> config.orderPresets,
                OrderPresetsWidgetConfig::new, value -> value.frame,
                OrderPresetsWidgetConfig::resetPreferences
            ))
            .data(source())
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

    @Nested
    @DisplayName("global appearance overrides")
    class GlobalAppearanceOverrides {
        @Test
        @DisplayName("widgets inherit global appearance by default")
        void inheritsGlobalAppearance() {
            var config = new WidgetsConfig();
            var store = new WidgetStateStore(() -> config, () -> {});
            var bookmarks = bookmarksDefinition(() -> config.bookmarks);

            store.setGlobalFineTuneScale(1.35, false);
            store.setGlobalBackgroundColor(0xAA102030, false);

            assertFalse(store.hasWidgetScaleOverride(bookmarks));
            assertFalse(store.hasBackgroundOverride(bookmarks));
            assertEquals(1.35, store.globalFineTuneScale());
            assertEquals(0xAA102030, store.backgroundColor(bookmarks));
        }

        @Test
        @DisplayName("disabled overrides preserve their custom values")
        void preservesDisabledOverrideValues() {
            var config = new WidgetsConfig();
            var store = new WidgetStateStore(() -> config, () -> {});
            var bookmarks = bookmarksDefinition(() -> config.bookmarks);
            store.setWidgetScaleOverride(bookmarks, true, false);
            store.setWidgetScale(bookmarks, 1.6, false);
            store.setBackgroundOverride(bookmarks, true, false);
            store.setBackgroundColor(bookmarks, 0xCC304050, false);

            store.setWidgetScaleOverride(bookmarks, false, false);
            store.setBackgroundOverride(bookmarks, false, false);
            store.setGlobalFineTuneScale(0.8, false);
            store.setGlobalBackgroundColor(0xDD405060, false);

            assertEquals(0.8, store.globalFineTuneScale());
            assertEquals(0xDD405060, store.backgroundColor(bookmarks));
            assertEquals(1.6, config.bookmarks.frame.scale);
            assertEquals(0xCC304050, config.bookmarks.frame.background);

            store.setWidgetScaleOverride(bookmarks, true, false);
            store.setBackgroundOverride(bookmarks, true, false);

            assertEquals(1.6, store.widgetScale(bookmarks));
            assertEquals(0xCC304050, store.backgroundColor(bookmarks));
        }

    }

    private static WidgetDefinition<Object, BookmarksWidgetConfig, Void> bookmarksDefinition(
        java.util.function.Supplier<BookmarksWidgetConfig> supplier
    ) {
        var id = WidgetId.parse("btrbz:bookmarks");
        return WidgetDefinition.<Object, BookmarksWidgetConfig, Void>builder(id, "Bookmarks")
            .config(new WidgetConfigHandle<>(
                id, supplier, BookmarksWidgetConfig::new,
                value -> value.frame, BookmarksWidgetConfig::resetPreferences
            ))
            .data(source())
            .preview(() -> null)
            .viewFactory(() -> null)
            .build();
    }

    private static WidgetDataSource<Object> source() {
        return new WidgetDataSource<>() {
            @Override public CacheDependencies cacheDependencies() { return CacheDependencies.none(); }
            @Override public Object snapshot(WidgetSession session) { return new Object(); }
        };
    }
}
