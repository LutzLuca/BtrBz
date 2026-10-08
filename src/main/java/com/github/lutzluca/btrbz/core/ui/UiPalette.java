package com.github.lutzluca.btrbz.core.ui;

/** Semantic UI colors in ARGB format */
public record UiPalette(
    int primary,
    int label,
    int muted,
    int quantity,
    int action,
    int modLabel,
    int money,
    int buy,
    int sell,
    int success,
    int matched,
    int error,
    int unknown,
    int filled,
    int rowHover,
    int rowDrag,
    int undercutRow,
    int progressTrack,
    int progressFill,
    int scrollbar,
    ButtonColors buttons
) {
    public static final UiPalette DEFAULT_PALETTE = new UiPalette(
        0xFFF3F5F8,
        0xFFAAAAAA,
        0xFF808997,
        0xFFFFFFFF,
        0xFFF3F5F8,
        0xFFFFAA00,
        0xFFFFAA00,
        0xFF55FF55,
        0xFFFFAA00,
        0xFF55FF55,
        0xFF8DAFFF,
        0xFFFF5555,
        0xFF808997,
        0xFFFFC857,
        0x18FFFFFF,
        0x28FFFFFF,
        0x303C1010,
        0x503A414D,
        0xFFFFAA00,
        0xA0818A99,
        new ButtonColors(
            0xFF25282C, 0xFF484D54, 0xFF3B3F44,
            0xFF3F454C, 0xFF555C65,
            0xFF22252A, 0xFF2D3136,
            0xFF45494E, 0xFF707780, 0xFF89929C,
            0xFFFFFFFF, 0xFFA0A0A0));

    public record ButtonColors(
        int background,
        int hovered,
        int selected,
        int primary,
        int primaryHovered,
        int disabled,
        int disabledHovered,
        int border,
        int borderHovered,
        int accent,
        int text,
        int disabledText
    ) {}
}
