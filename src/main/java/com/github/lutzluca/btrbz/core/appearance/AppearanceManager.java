package com.github.lutzluca.btrbz.core.appearance;

import com.github.lutzluca.btrbz.core.ui.UiStyles;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.List;

/** Owns applying and persisting appearance changes, including retained UI refresh. */
public final class AppearanceManager {
    private final Supplier<AppearanceConfig> config;
    private final Runnable save;

    public AppearanceManager(Supplier<AppearanceConfig> config, Runnable save) {
        this.config = Objects.requireNonNull(config, "config");
        this.save = Objects.requireNonNull(save, "save");
        UiStyles.configure(this.config.get().theme);
    }

    public UiTheme theme() {
        return this.config.get().theme;
    }

    public void apply(UiTheme theme) {
        Objects.requireNonNull(theme, "theme");
        var settings = this.config.get();
        if (settings.theme.equals(theme)) {
            return;
        }
        settings.theme = theme;
        UiStyles.configure(theme);
        this.save.run();
    }

    public List<UiTheme> savedThemes() {
        return List.copyOf(this.config.get().savedThemes);
    }

    public void saveTheme(UiTheme theme) {
        Objects.requireNonNull(theme, "theme");
        var themes = this.config.get().savedThemes;
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

    public UiTheme importTheme(UiTheme theme) {
        Objects.requireNonNull(theme, "theme");
        var themes = this.savedThemes();
        String name = theme.name();
        int copy = 2;
        while (containsName(themes, name)) {
            String suffix = " (" + copy++ + ")";
            name = theme.name().substring(0, Math.min(theme.name().length(), 64 - suffix.length())) + suffix;
        }
        var imported = theme.withName(name);
        this.saveTheme(imported);
        return imported;
    }

    private static boolean containsName(List<UiTheme> themes, String name) {
        return themes.stream().anyMatch(theme -> theme.name().equalsIgnoreCase(name));
    }

    public void removeTheme(String name) {
        if (this.config.get().savedThemes.removeIf(theme -> theme.name().equalsIgnoreCase(name))) {
            this.save.run();
        }
    }
}
