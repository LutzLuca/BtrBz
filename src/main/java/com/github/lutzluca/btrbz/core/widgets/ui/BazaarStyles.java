package com.github.lutzluca.btrbz.core.widgets.ui;

import io.wispforest.owo.ui.core.Color;

public final class BazaarStyles {
    public static final int ROW_HOVER = 0x18FFFFFF;
    public static final int ROW_DRAG = 0x28FFFFFF;
    public static final int UNDERCUT_ROW = 0x303C1010;
    public static final int PROGRESS_TRACK = 0x503A414D;
    public static final int PROGRESS_FILL = 0xFFE3B64B;
    public static final int SCROLLBAR = 0xA0818A99;

    private BazaarStyles() {}

    public static Color color(int argb) {
        return Color.ofArgb(argb);
    }
}
