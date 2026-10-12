package com.github.lutzluca.btrbz.core.appearance;

import lombok.Getter;
import lombok.experimental.Accessors;

/** BtrBz's own dark palette and adaptations of the upstream palettes linked in the README. */
@Getter
@Accessors(fluent = true)
public enum ThemePreset {
    BtrBzDark("BtrBz Dark", new int[]{
        0x1C1C1C, 0x282828, 0x434343, 0xF1F1F1, 0xAAAAAA, 0x8A919C, 0xFFAA00, 0x1C1C1C,
        0x55FF55, 0xFFFF55, 0xFF5555, 0xFFAA00, 0x55FF55, 0xFFAA00,
        0x55FF55, 0x5599FF, 0xFF5555, 0xFFC857, 0xAAAAAA, 0xBB77FF}, 0.85),
    Mocha("Catppuccin Mocha", new int[]{
        0x1B1B29, 0x292A3D, 0x5E6179, 0xD8DEF2, 0xB4B9CE, 0xA2A9C0, 0xB4BEFE, 0x1B1B29,
        0xA6E3A1, 0xF9E2AF, 0xF38BA8, 0xF7D58B, 0xA6E3A1, 0xF7D58B,
        0xA6E3A1, 0x89B4FA, 0xF38BA8, 0xF7D58B, 0xA6ADC8, 0xCBA6F7}, 0.92),
    Nord("Nord", new int[]{
        0x232A34, 0x2E3440, 0x536079, 0xE8ECF2, 0xB8C1CF, 0x9BA7B8, 0x88C0D0, 0x232A34,
        0x7DE89F, 0xEBCB8B, 0xFF7B88, 0xEBCB8B, 0x7DE89F, 0xEBCB8B,
        0x7DE89F, 0x79ACF7, 0xFF7B88, 0xF0D477, 0xA5AFBF, 0xC5A5BE}, 0.90),
    Dracula("Dracula", new int[]{
        0x232430, 0x2F3140, 0x6272A4, 0xF0F0E8, 0xCACDDD, 0x9DA9D0, 0xBD93F9, 0x232430,
        0x50FA7B, 0xF1FA8C, 0xFF7777, 0xFFB86C, 0x50FA7B, 0xFF79C6,
        0x50FA7B, 0x8DBBFF, 0xFF7777, 0xFFB86C, 0x9DA9D0, 0xBD93F9}, 0.92);

    private final UiTheme theme;

    ThemePreset(String name, int[] colors) {
        this(name, colors, 0.96);
    }

    ThemePreset(String name, int[] colors, double panelOpacity) {
        this.theme = new UiTheme(name, UiPalette.fromColors(colors, panelOpacity));
    }
}
