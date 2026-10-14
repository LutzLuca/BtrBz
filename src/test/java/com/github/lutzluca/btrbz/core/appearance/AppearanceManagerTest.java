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
        UiStyles.configure(ThemePreset.BtrBzDark.theme());
    }

    @Test
    void applyingAppearancePersistsSharedHighlightOpacityAndPreservesWidgetOverrides() {
        var path = this.directory.resolve("appearance.json");
        var store = new ConfigStore(path);
        store.config().widgets.globalBackgroundOverride = 0xA0123456;
        store.config().widgets.trackedOrders.frame.backgroundOverride = 0x80234567;
        var saves = new AtomicInteger();
        var manager = new AppearanceManager(() -> store.config().appearance, () -> {
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
        Assertions.assertEquals(73, reloaded.config().appearance.theme.orderHighlightOpacity());
        Assertions.assertEquals(0xA0123456, reloaded.config().widgets.globalBackgroundOverride);
        Assertions.assertEquals(0x80234567, reloaded.config().widgets.trackedOrders.frame.backgroundOverride);
        Assertions.assertEquals(1, saves.get());
        Assertions.assertEquals(theme.palette(), UiStyles.palette());
        Assertions.assertEquals(theme.palette().textPrimary() & 0xFFFFFF,
            UiStyles.productName("§aEnchanted Sugar").getStyle().getColor().getValue());

        var highlightOpacity = store.config().appearance.createHighlightOpacityOption();
        highlightOpacity.requestSet(41);
        Assertions.assertEquals(73, manager.theme().orderHighlightOpacity());
        Assertions.assertTrue(highlightOpacity.applyValue());
        Assertions.assertEquals(41, manager.theme().orderHighlightOpacity());
        manager.apply(theme.withOrderHighlightOpacity(62));
        highlightOpacity.forgetPendingValue();
        Assertions.assertEquals(62, highlightOpacity.pendingValue());
        Assertions.assertFalse(highlightOpacity.applyValue());
        Assertions.assertEquals(2, saves.get());
        Assertions.assertTrue(reloaded.load());
        Assertions.assertEquals(62, reloaded.config().appearance.theme.orderHighlightOpacity());
    }

    @Test
    void savedThemesPersistCompleteProfilesWithoutChangingTheActiveTheme() {
        var store = new ConfigStore(this.directory.resolve("library.json"));
        var saves = new AtomicInteger();
        var manager = new AppearanceManager(() -> store.config().appearance, () -> {
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
        Assertions.assertEquals(updated, reloaded.config().appearance.savedThemes.getLast());
        Assertions.assertEquals(1, reloaded.config().appearance.savedThemes.size());

        manager.apply(updated);
        store.config().appearance.theme = store.config().appearance.theme.withOrderHighlightOpacity(23);
        var exported = ThemeJson.importTheme(ThemeJson.exportTheme(manager.theme())).get();
        Assertions.assertEquals(23, exported.orderHighlightOpacity());
        Assertions.assertEquals(81, manager.savedThemes().getLast().orderHighlightOpacity());
        manager.removeTheme("weekend");
        Assertions.assertEquals(updated.withOrderHighlightOpacity(23), manager.theme());
        Assertions.assertTrue(reloaded.load());
        Assertions.assertEquals(4, saves.get());

        var changedDefault = custom.withName("nOrD");
        Assertions.assertThrows(IllegalArgumentException.class, () -> manager.saveTheme(changedDefault));
        manager.removeTheme("nord");
        Assertions.assertTrue(manager.savedThemes().isEmpty());
        Assertions.assertEquals(4, saves.get());
        Assertions.assertTrue(reloaded.load());
        Assertions.assertTrue(reloaded.config().appearance.savedThemes.isEmpty());
        Assertions.assertEquals(updated.withOrderHighlightOpacity(23), reloaded.config().appearance.theme);
    }

    @Test
    void importingAddsDistinctEntriesWithoutOverwritingProfilesOrApplyingAppearance() {
        var store = new ConfigStore(this.directory.resolve("import.json"));
        var manager = new AppearanceManager(() -> store.config().appearance, store::save);
        var active = manager.theme();
        var original = ThemePreset.Nord.theme();
        var imported = original.withName("nord").withProductNames(false, 42).withOrderHighlightOpacity(19);

        var first = manager.importTheme(imported);
        var second = manager.importTheme(imported);
        Assertions.assertEquals(imported.withName("nord (2)"), first);
        Assertions.assertEquals(imported.withName("nord (3)"), second);
        Assertions.assertFalse(manager.savedThemes().contains(original));
        Assertions.assertEquals(active, manager.theme());

        var longName = "A".repeat(64);
        manager.saveTheme(imported.withName(longName));
        var shortened = manager.importTheme(imported.withName(longName));
        Assertions.assertEquals("A".repeat(60) + " (2)", shortened.name());
        var reloaded = new ConfigStore(this.directory.resolve("import.json"));
        Assertions.assertTrue(reloaded.load());
        Assertions.assertTrue(
            reloaded.config().appearance.savedThemes.containsAll(List.of(first, second, shortened)));
        Assertions.assertEquals(active, reloaded.config().appearance.theme);
    }

}
