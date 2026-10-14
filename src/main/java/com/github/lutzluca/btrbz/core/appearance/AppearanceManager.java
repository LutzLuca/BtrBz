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
        return this.config.get().savedThemes.stream()
            .filter(theme -> !ThemePreset.hasName(theme.name())).toList();
    }

    public void saveTheme(UiTheme theme) {
        Objects.requireNonNull(theme, "theme");
        if (ThemePreset.hasName(theme.name())) {
            throw new IllegalArgumentException("Choose a name different from the presets");
        }
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
        var imported = theme.withName(this.uniqueName(theme.name()));
        this.saveTheme(imported);
        return imported;
    }

    UiTheme newTheme(UiTheme source) {
        return source.withName(this.uniqueName("My theme"));
    }

    private String uniqueName(String original) {
        var themes = this.savedThemes();
        String name = original;
        int copy = 2;
        while (ThemePreset.hasName(name) || containsName(themes, name)) {
            String suffix = " (" + copy++ + ")";
            name = original.substring(0, Math.min(original.length(), 64 - suffix.length())) + suffix;
        }
        return name;
    }

    private static boolean containsName(List<UiTheme> themes, String name) {
        return themes.stream().anyMatch(theme -> theme.name().equalsIgnoreCase(name));
    }

    public void removeTheme(String name) {
        if (ThemePreset.hasName(name)) {
            return;
        }
        if (this.config.get().savedThemes.removeIf(theme -> theme.name().equalsIgnoreCase(name))) {
            this.save.run();
        }
    }
}
