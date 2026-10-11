package com.github.lutzluca.btrbz.core.ui;

import java.util.Objects;

public record UiTheme(String name, UiPalette palette) {
    public UiTheme {
        name = Objects.requireNonNull(name, "name").strip();
        if (name.isEmpty() || name.length() > 64 || name.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Theme name must contain 1 to 64 characters");
        }
        Objects.requireNonNull(palette, "palette");
    }
}
