package com.github.lutzluca.btrbz.core.ui;

import java.util.Arrays;

/** Editable semantic colors and centrally derived component states, in ARGB format. */
public record UiPalette(
    int surface, int surfaceRaised, int outline,
    int textPrimary, int textSecondary, int textMuted, int accent, int onAccent,
    int success, int warning, int error,
    int money, int buy, int sell,
    int best, int matched, int undercut, int filled, int expired, int unknown,
    double panelOpacity
) {
    public UiPalette {
        if (!Double.isFinite(panelOpacity) || panelOpacity < 0 || panelOpacity > 1) {
            throw new IllegalArgumentException("Panel opacity must be between 0 and 1");
        }
    }

    public static UiPalette fromColors(int[] colors, double opacity) {
        if (colors.length != UiColor.values().length) {
            throw new IllegalArgumentException("A theme must supply every color");
        }
        int[] c = Arrays.stream(colors).map(color -> color | 0xFF000000).toArray();
        return new UiPalette(c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7], c[8], c[9],
            c[10], c[11], c[12], c[13], c[14], c[15], c[16], c[17], c[18], c[19], opacity);
    }

    public UiPalette withColor(UiColor role, int color) {
        int[] colors = Arrays.stream(UiColor.values()).mapToInt(value -> value.color(this)).toArray();
        colors[role.ordinal()] = color;
        return fromColors(colors, this.panelOpacity);
    }

    public UiPalette withOpacity(double opacity) {
        return fromColors(Arrays.stream(UiColor.values()).mapToInt(role -> role.color(this)).toArray(), opacity);
    }

    public int panelBackground() {
        return alpha(this.surface, this.panelOpacity);
    }

    public int screenBackdrop() {
        return alpha(this.surface, 0.65);
    }

    public int rowHover() {
        return alpha(this.textPrimary, 0.07);
    }

    public int rowDrag() {
        return alpha(this.accent, 0.18);
    }

    public int undercutRow() {
        return alpha(this.undercut, 0.12);
    }

    public int progressTrack() {
        return alpha(this.outline, 0.65);
    }

    public int progressFill() {
        return this.money;
    }

    public int scrollbar() {
        return alpha(this.textMuted, 0.8);
    }

    public ButtonColors buttons() {
        return new ButtonColors(this.surfaceRaised,
            blend(this.surfaceRaised, this.textPrimary, 0.1),
            blend(this.surfaceRaised, this.accent, 0.22),
            blend(this.surfaceRaised, this.accent, 0.32),
            this.accent, blend(this.accent, this.textPrimary, 0.12),
            blend(this.surface, this.surfaceRaised, 0.5), this.outline,
            this.textPrimary, this.onAccent, this.textMuted);
    }

    public static int alpha(int color, double opacity) {
        return (int) Math.round(Math.clamp(opacity, 0, 1) * 255) << 24 | color & 0xFFFFFF;
    }

    public static int blend(int background, int foreground, double amount) {
        double t = Math.clamp(amount, 0, 1);
        int red = (int) Math.round((background >> 16 & 255) * (1 - t) + (foreground >> 16 & 255) * t);
        int green = (int) Math.round((background >> 8 & 255) * (1 - t) + (foreground >> 8 & 255) * t);
        int blue = (int) Math.round((background & 255) * (1 - t) + (foreground & 255) * t);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    public record ButtonColors(
        int background, int hovered, int selected, int selectedHovered,
        int primary, int primaryHovered, int disabled, int borderHovered,
        int text, int primaryText, int disabledText
    ) {}
}
