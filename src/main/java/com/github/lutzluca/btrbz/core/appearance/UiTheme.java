package com.github.lutzluca.btrbz.core.appearance;

import java.util.Objects;

public record UiTheme(
    String name, UiPalette palette, boolean formattedProductNames,
    int productColorStrength, int orderHighlightOpacity
) {
    public UiTheme {
        name = Objects.requireNonNull(name, "name").strip();
        if (name.isEmpty() || name.length() > 64 || name.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Theme name must contain 1 to 64 characters");
        }
        Objects.requireNonNull(palette, "palette");
        if (productColorStrength < 0 || productColorStrength > 100
            || orderHighlightOpacity < 0
            || orderHighlightOpacity > 100) {
            throw new IllegalArgumentException("Theme percentages must be between 0 and 100");
        }
    }

    public UiTheme(String name, UiPalette palette) {
        this(name, palette, true, 100, 75);
    }

    public UiTheme withName(String name) {
        return new UiTheme(name, this.palette, this.formattedProductNames,
            this.productColorStrength, this.orderHighlightOpacity);
    }

    public UiTheme withPalette(UiPalette palette) {
        return new UiTheme(this.name, palette, this.formattedProductNames,
            this.productColorStrength, this.orderHighlightOpacity);
    }

    public UiTheme withProductNames(boolean formatted, int strength) {
        return new UiTheme(this.name, this.palette, formatted, strength, this.orderHighlightOpacity);
    }

    public UiTheme withOrderHighlightOpacity(int opacity) {
        return new UiTheme(this.name, this.palette, this.formattedProductNames, this.productColorStrength, opacity);
    }
}
