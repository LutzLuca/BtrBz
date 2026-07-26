package com.github.lutzluca.btrbz.core.widgets.ui;

import io.wispforest.owo.ui.core.Color;

public final class BazaarStyles {
    public static final int PRIMARY_TEXT = 0xFFF3F5F8;
    public static final int SECONDARY_TEXT = 0xFFAAB1BD;
    public static final int MUTED_TEXT = 0xFF808997;
    public static final int ROW_HOVER = 0x18FFFFFF;
    public static final int ROW_DRAG = 0x28FFFFFF;
    public static final int UNDERCUT_ROW = 0x303C1010;
    public static final int PROGRESS_TRACK = 0x503A414D;
    public static final int PROGRESS_FILL = 0xFFE3B64B;
    public static final int INSERTION = 0xFFEBCB5B;
    public static final int BUY_ACCENT = 0xFF58C77A;
    public static final int SELL_ACCENT = 0xFFE3B64B;
    public static final int STATUS_TOP = 0xFF58C77A;
    public static final int STATUS_MATCHED = 0xFF5DADE2;
    public static final int STATUS_UNDERCUT = 0xFFE06C75;
    public static final int STATUS_UNKNOWN = 0xFFB07CC6;
    public static final int STATUS_FILLED = 0xFFFFC857;
    public static final int SCROLLBAR = 0xA0818A99;

    private BazaarStyles() {}

    public static Color color(int argb) {
        return Color.ofArgb(argb);
    }
}
