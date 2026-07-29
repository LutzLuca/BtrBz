package com.github.lutzluca.btrbz.core.widgets.ordervalue;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetFrameConfig;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetDisplayOptions.NumberStyle;
import com.github.lutzluca.btrbz.core.widgets.layout.WidgetPlacement;

public final class OrderValueWidgetConfig {
    public enum ValueDisplay { Detailed, Summary }
    public enum ColorMode { Semantic, Neutral }

    public WidgetFrameConfig frame = new WidgetFrameConfig(WidgetPlacement.topLeft(0.65, 0.16));
    public int contentWidth = 205;
    public boolean fitToContent = true;
    public ValueDisplay display = ValueDisplay.Detailed;
    public NumberStyle numberStyle = NumberStyle.Compact;
    public boolean showCoinsSuffix = true;
    public ColorMode colorMode = ColorMode.Semantic;
    public boolean buyLocked = true;
    public boolean buyItems = true;
    public boolean sellClaimable = true;
    public boolean sellPending = true;
    public static void resetPreferences(OrderValueWidgetConfig current, OrderValueWidgetConfig defaults) {
        current.contentWidth = defaults.contentWidth;
        current.fitToContent = defaults.fitToContent;
        current.display = defaults.display;
        current.numberStyle = defaults.numberStyle;
        current.showCoinsSuffix = defaults.showCoinsSuffix;
        current.colorMode = defaults.colorMode;
        current.buyLocked = defaults.buyLocked;
        current.buyItems = defaults.buyItems;
        current.sellClaimable = defaults.sellClaimable;
        current.sellPending = defaults.sellPending;
    }
}
