package com.github.lutzluca.btrbz.core.widgets.orderbook;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetFrameConfig;
import com.github.lutzluca.btrbz.core.widgets.layout.WidgetPlacement;

public final class OrderBookPriceWidgetConfig {
    public enum EmbeddedSideDisplay { Relevant, Both }

    public WidgetFrameConfig frame = new WidgetFrameConfig(WidgetPlacement.topLeft(0.04, 0.50));
    public int contentWidth = 400;
    public int visibleRows = 8;
    public boolean showOrderCount = true;
    public EmbeddedSideDisplay sideDisplay = EmbeddedSideDisplay.Relevant;

    public static void resetPreferences(OrderBookPriceWidgetConfig current, OrderBookPriceWidgetConfig defaults) {
        current.contentWidth = defaults.contentWidth;
        current.visibleRows = defaults.visibleRows;
        current.showOrderCount = defaults.showOrderCount;
        current.sideDisplay = defaults.sideDisplay;
    }
}
