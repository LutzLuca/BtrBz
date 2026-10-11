package com.github.lutzluca.btrbz.core.ui;

import java.util.Objects;
import java.util.function.Supplier;

/** Owns applying and persisting appearance changes, including retained UI refresh. */
public final class AppearanceManager {
    private final Supplier<AppearanceConfig> config;
    private final Runnable save;

    public AppearanceManager(Supplier<AppearanceConfig> config, Runnable save) {
        this.config = Objects.requireNonNull(config, "config");
        this.save = Objects.requireNonNull(save, "save");
        UiStyles.configure(this.config.get());
    }

    public UiTheme theme() {
        return this.config.get().theme;
    }

    public boolean formattedProductNames() {
        return this.config.get().formattedProductNames;
    }

    public void apply(UiTheme theme, boolean formattedNames) {
        var settings = this.config.get();
        if (settings.theme.equals(theme) && settings.formattedProductNames == formattedNames) {
            return;
        }
        settings.theme = Objects.requireNonNull(theme, "theme");
        settings.formattedProductNames = formattedNames;
        UiStyles.configure(settings);
        this.save.run();
    }
}
