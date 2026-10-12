package com.github.lutzluca.btrbz.core.appearance;

import com.github.lutzluca.btrbz.core.ui.UiStyles;
import com.github.lutzluca.btrbz.core.config.Config;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.List;
import java.util.Arrays;

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
        var config = this.config.get();
        // The shared Order Highlighting option can be changed independently of the appearance editor.
        return config.appearance.theme.withOrderHighlightOpacity(config.orderHighlight.opacity);
    }

    public void apply(UiTheme theme) {
        Objects.requireNonNull(theme, "theme");
        var config = this.config.get();
        var settings = config.appearance;
        if (settings.theme.equals(theme) && config.orderHighlight.opacity == theme.orderHighlightOpacity()) {
            return;
        }
        settings.theme = theme;
        config.orderHighlight.opacity = theme.orderHighlightOpacity();
        UiStyles.configure(settings);
        this.save.run();
    }

    public List<UiTheme> savedThemes() {
        return List.copyOf(this.config.get().appearance.savedThemes);
    }

    public void saveTheme(UiTheme theme) {
        Objects.requireNonNull(theme, "theme");
        if (Arrays.stream(ThemePreset.values())
            .anyMatch(preset -> preset.theme().name().equalsIgnoreCase(theme.name()))) {
            throw new IllegalArgumentException("Choose a name different from the built-in themes");
        }
        var themes = this.config.get().appearance.savedThemes;
        for (int index = 0; index < themes.size(); index++) {
            if (themes.get(index).name().equalsIgnoreCase(theme.name())) {
                if (!themes.get(index).equals(theme)) {
                    themes.set(index, theme);
                    this.save.run();
                }
                return;
            }
        }
        themes.add(theme);
        this.save.run();
    }

    public void removeTheme(String name) {
        if (this.config.get().appearance.savedThemes.removeIf(theme -> theme.name().equalsIgnoreCase(name))) {
            this.save.run();
        }
    }
}
