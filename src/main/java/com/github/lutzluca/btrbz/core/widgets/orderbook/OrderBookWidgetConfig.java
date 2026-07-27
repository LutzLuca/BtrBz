package com.github.lutzluca.btrbz.core.widgets.orderbook;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetFrameConfig;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetDisplayOptions.NumberStyle;
import com.github.lutzluca.btrbz.core.widgets.WidgetPlacement;

public final class OrderBookWidgetConfig {
    public enum BookLayout { Split, BuyOnly, SellOnly }

    public WidgetFrameConfig frame = new WidgetFrameConfig(WidgetPlacement.topLeft(0.55, 0.34));
    public int contentWidth = 330;
    public int visibleRows = 5;
    public BookLayout layout = BookLayout.Split;
    public NumberStyle numberStyle = NumberStyle.Exact;
    public boolean showOrderCount = true;
    public boolean showHeader = true;
    public boolean showItem = true;

    public int contentWidth() { return this.contentWidth; }
    public int visibleRows() { return this.visibleRows; }
    public BookLayout layout() { return this.layout; }
    public NumberStyle numberStyle() { return this.numberStyle; }
    public boolean showOrderCount() { return this.showOrderCount; }
    public boolean showHeader() { return this.showHeader; }
    public boolean showItem() { return this.showItem; }

    public static void resetPreferences(OrderBookWidgetConfig current, OrderBookWidgetConfig defaults) {
        current.contentWidth = defaults.contentWidth;
        current.visibleRows = defaults.visibleRows;
        current.layout = defaults.layout;
        current.numberStyle = defaults.numberStyle;
        current.showOrderCount = defaults.showOrderCount;
        current.showHeader = defaults.showHeader;
        current.showItem = defaults.showItem;
    }
}
