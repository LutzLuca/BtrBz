package com.github.lutzluca.btrbz.core.widgets.orderbook;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetFrameConfig;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetDisplayOptions.NumberStyle;
import com.github.lutzluca.btrbz.core.widgets.layout.WidgetPlacement;

public final class OrderBookWidgetConfig {
    public enum BookLayout { Split, BuyOnly, SellOnly }

    public WidgetFrameConfig frame = new WidgetFrameConfig(WidgetPlacement.topLeft(0.55, 0.34));
    public int contentWidth = 400;
    public int visibleRows = 10;
    public BookLayout layout = BookLayout.Split;
    public NumberStyle numberStyle = NumberStyle.Exact;
    public boolean showOrderCount = true;
    public static void resetPreferences(OrderBookWidgetConfig current, OrderBookWidgetConfig defaults) {
        current.contentWidth = defaults.contentWidth;
        current.visibleRows = defaults.visibleRows;
        current.layout = defaults.layout;
        current.numberStyle = defaults.numberStyle;
        current.showOrderCount = defaults.showOrderCount;
    }
}
