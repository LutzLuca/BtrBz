package com.github.lutzluca.btrbz.core.appearance;

import com.github.lutzluca.btrbz.core.ui.UiStyles;
import com.github.lutzluca.btrbz.core.config.ConfigStore;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
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
    void applyingAnImportedThemePersistsPreferencesAndPreservesWidgetOverrides() {
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
            ThemePreset.Nord.theme().palette().withColor(UiColor.Best, 0xFF55BB77).withOpacity(0.8)))).get();

        manager.apply(theme, false);
        manager.apply(theme, false);

        var reloaded = new ConfigStore(path);
        Assertions.assertTrue(reloaded.load());
        Assertions.assertEquals(theme, reloaded.config().appearance.theme);
        Assertions.assertFalse(reloaded.config().appearance.formattedProductNames);
        Assertions.assertEquals(0xA0123456, reloaded.config().widgets.globalBackgroundOverride);
        Assertions.assertEquals(0x80234567, reloaded.config().widgets.trackedOrders.frame.backgroundOverride);
        Assertions.assertEquals(1, saves.get());
        Assertions.assertEquals(theme.palette(), UiStyles.palette());
        Assertions.assertEquals(theme.palette().textPrimary() & 0xFFFFFF,
            UiStyles.productName("§aEnchanted Sugar").getStyle().getColor().getValue());
    }

}
