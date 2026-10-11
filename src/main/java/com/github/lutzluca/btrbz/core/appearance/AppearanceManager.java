package com.github.lutzluca.btrbz.core.appearance;

import com.github.lutzluca.btrbz.core.ui.UiStyles;
import com.github.lutzluca.btrbz.core.config.Config;
import java.util.Objects;
import java.util.function.Supplier;

/** Owns applying and persisting appearance changes, including retained UI refresh. */
public final class AppearanceManager {
    private final Supplier<Config> config;
    private final Runnable save;

    public AppearanceManager(Supplier<Config> config, Runnable save) {
        this.config = Objects.requireNonNull(config, "config");
        this.save = Objects.requireNonNull(save, "save");
        UiStyles.configure(this.config.get().appearance);
    }

    public UiTheme theme() {
        return this.config.get().appearance.theme;
    }

    public boolean formattedProductNames() {
        return this.config.get().appearance.formattedProductNames;
    }

    public int orderHighlightOpacity() {
        return this.config.get().orderHighlight.opacity;
    }

    public void apply(UiTheme theme, boolean formattedNames, int highlightOpacity) {
        var config = this.config.get();
        var settings = config.appearance;
        if (settings.theme.equals(theme) && settings.formattedProductNames == formattedNames
            && config.orderHighlight.opacity == highlightOpacity) {
            return;
        }
        settings.theme = Objects.requireNonNull(theme, "theme");
        settings.formattedProductNames = formattedNames;
        config.orderHighlight.opacity = highlightOpacity;
        UiStyles.configure(settings);
        this.save.run();
    }
}
