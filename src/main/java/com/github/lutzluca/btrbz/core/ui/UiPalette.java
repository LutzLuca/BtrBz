package com.github.lutzluca.btrbz.core.ui;

/** Semantic UI colors in ARGB format */
public record UiPalette(
    int primary,
    int label,
    int muted,
    int quantity,
    int action,
    int buy,
    int sell,
    int success,
    int matched,
    int error,
    int unknown,
    int filled
) {
    public static final UiPalette DEFAULT_PALETTE = new UiPalette(
        0xFFF3F5F8,
        0xFFAAAAAA,
        0xFF808997,
        0xFFFFFFFF,
        0xFFF3F5F8,
        0xFF58C77A,
        0xFFE3B64B,
        0xFF58C77A,
        0xFF8DAFFF,
        0xFFFF5555,
        0xFF808997,
        0xFFFFC857);
}
