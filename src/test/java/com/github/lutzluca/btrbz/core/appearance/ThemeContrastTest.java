package com.github.lutzluca.btrbz.core.appearance;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ThemeContrastTest {
    @Test
    void builtInTextRolesRemainReadableOnBothSurfacesAndPrimaryButtons() {
        for (var preset : ThemePreset.values()) {
            var palette = preset.theme().palette();
            for (var role : UiColor.values()) {
                if (role == UiColor.Surface || role == UiColor.SurfaceRaised
                    || role == UiColor.Outline
                    || role == UiColor.OnAccent) {
                    continue;
                }
                for (int background : new int[]{palette.surface(), palette.surfaceRaised()}) {
                    Assertions.assertTrue(contrast(role.color(palette), background) >= 4.5,
                        preset.theme().name() + " " + role.label());
                }
            }
            Assertions.assertTrue(contrast(palette.onAccent(), palette.accent()) >= 4.5, preset.name());
        }
    }

    private static double contrast(int first, int second) {
        double a = luminance(first);
        double b = luminance(second);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    private static double luminance(int color) {
        return linear(color >> 16 & 255) * 0.2126 + linear(color >> 8 & 255) * 0.7152 + linear(color & 255) * 0.0722;
    }

    private static double linear(int value) {
        double channel = value / 255.0;
        return channel <= 0.04045 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
    }
}
