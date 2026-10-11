package com.github.lutzluca.btrbz.core.widgets.orderbook;

import com.github.lutzluca.btrbz.core.ui.UiStyles;
import net.minecraft.util.ARGB;

/** Shared separator styling for the full and compact order books. */
final class OrderBookStyles {
    private OrderBookStyles() {}

    static int dividerColor() {
        return ARGB.multiplyAlpha(UiStyles.palette().border(), 0.65F);
    }
}
