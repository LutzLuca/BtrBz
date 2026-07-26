package com.github.lutzluca.btrbz.core.widgets;

import io.wispforest.owo.ui.core.Color;

final class BazaarStyles {
    static final int PRIMARY_TEXT = 0xFFF3F5F8;
    static final int SECONDARY_TEXT = 0xFFAAB1BD;
    static final int MUTED_TEXT = 0xFF808997;
    static final int ROW_HOVER = 0x18FFFFFF;
    static final int ROW_DRAG = 0x28FFFFFF;
    static final int UNDERCUT_ROW = 0x303C1010;
    static final int PROGRESS_TRACK = 0x503A414D;
    static final int PROGRESS_FILL = 0xFFE3B64B;
    static final int INSERTION = 0xFFEBCB5B;
    static final int BUY_ACCENT = 0xFF58C77A;
    static final int SELL_ACCENT = 0xFFE3B64B;
    static final int STATUS_TOP = 0xFF58C77A;
    static final int STATUS_MATCHED = 0xFF5DADE2;
    static final int STATUS_UNDERCUT = 0xFFE06C75;
    static final int STATUS_UNKNOWN = 0xFFB07CC6;
    static final int STATUS_FILLED = 0xFFFFC857;
    static final int SCROLLBAR = 0xA0818A99;

    private BazaarStyles() {}

    static Color color(int argb) {
        return Color.ofArgb(argb);
    }
}
