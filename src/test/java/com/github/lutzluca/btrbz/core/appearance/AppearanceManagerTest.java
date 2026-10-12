package com.github.lutzluca.btrbz.core.appearance;

import com.github.lutzluca.btrbz.core.ui.UiStyles;
import com.github.lutzluca.btrbz.core.config.ConfigStore;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppearanceManagerTest {
    @TempDir
    Path directory;

    @AfterEach
    void restoreAppearance() {
        UiStyles.configure(new AppearanceConfig());
    }

    @Test
    void applyingAppearancePersistsSharedHighlightOpacityAndPreservesWidgetOverrides() {
        var path = this.directory.resolve("appearance.json");
        var store = new ConfigStore(path);
        store.config().widgets.globalBackgroundOverride = 0xA0123456;
        store.config().widgets.trackedOrders.frame.backgroundOverride = 0x80234567;
        var saves = new AtomicInteger();
        var manager = new AppearanceManager(store::config, () -> {
            saves.incrementAndGet();
            store.save();
        });
        var theme = ThemeJson.importTheme(ThemeJson.exportTheme(new UiTheme("Custom Nord",
            ThemePreset.Nord.theme().palette().withColor(UiColor.Best, 0xFF55BB77).withOpacity(0.8), false, 80, 73)))
            .get();

        manager.apply(theme);
        manager.apply(theme);

        var reloaded = new ConfigStore(path);
        Assertions.assertTrue(reloaded.load());
        Assertions.assertEquals(theme, reloaded.config().appearance.theme);
        Assertions.assertFalse(reloaded.config().appearance.theme.formattedProductNames());
        Assertions.assertEquals(80, reloaded.config().appearance.theme.productColorStrength());
        Assertions.assertEquals(73, reloaded.config().orderHighlight.opacity);
        Assertions.assertEquals(0xA0123456, reloaded.config().widgets.globalBackgroundOverride);
        Assertions.assertEquals(0x80234567, reloaded.config().widgets.trackedOrders.frame.backgroundOverride);
        Assertions.assertEquals(1, saves.get());
        Assertions.assertEquals(theme.palette(), UiStyles.palette());
        Assertions.assertEquals(theme.palette().textPrimary() & 0xFFFFFF,
            UiStyles.productName("§aEnchanted Sugar").getStyle().getColor().getValue());

        store.config().orderHighlight.opacity = 41;
        Assertions.assertEquals(41, manager.theme().orderHighlightOpacity());
        manager.apply(theme.withOrderHighlightOpacity(62));
        Assertions.assertEquals(2, saves.get());
        Assertions.assertTrue(reloaded.load());
        Assertions.assertEquals(62, reloaded.config().orderHighlight.opacity);
    }

    @Test
    void savedThemesPersistCompleteProfilesWithoutChangingTheActiveTheme() {
        var store = new ConfigStore(this.directory.resolve("library.json"));
        var saves = new AtomicInteger();
        var manager = new AppearanceManager(store::config, () -> {
            saves.incrementAndGet();
            store.save();
        });
        var active = manager.theme();
        var custom = ThemePreset.Nord.theme().withName("Weekend").withProductNames(false, 68)
            .withOrderHighlightOpacity(81);
        manager.saveTheme(custom);
        manager.saveTheme(custom);
        Assertions.assertEquals(active, manager.theme());
        Assertions.assertEquals(1, saves.get());
        var updated = custom.withName("WEEKEND").withPalette(custom.palette().withOpacity(0.73));
        manager.saveTheme(updated);
        var reloaded = new ConfigStore(this.directory.resolve("library.json"));
        Assertions.assertTrue(reloaded.load());
        Assertions.assertEquals(List.of(updated), reloaded.config().appearance.savedThemes);
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> manager.saveTheme(custom.withName("Nord")));

        manager.apply(updated);
        store.config().orderHighlight.opacity = 23;
        var exported = ThemeJson.importTheme(ThemeJson.exportTheme(manager.theme())).get();
        Assertions.assertEquals(23, exported.orderHighlightOpacity());
        Assertions.assertEquals(81, manager.savedThemes().getFirst().orderHighlightOpacity());
        manager.removeTheme("weekend");
        Assertions.assertEquals(updated.withOrderHighlightOpacity(23), manager.theme());
        Assertions.assertTrue(reloaded.load());
        Assertions.assertTrue(reloaded.config().appearance.savedThemes.isEmpty());
        Assertions.assertEquals(4, saves.get());
    }

}
