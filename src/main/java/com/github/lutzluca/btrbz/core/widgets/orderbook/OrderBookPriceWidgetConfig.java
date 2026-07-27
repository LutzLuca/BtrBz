package com.github.lutzluca.btrbz.core.widgets.orderbook;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetFrameConfig;
import com.github.lutzluca.btrbz.core.widgets.WidgetPlacement;

public final class OrderBookPriceWidgetConfig {
    public enum EmbeddedSideDisplay { Relevant, Both }

    public WidgetFrameConfig frame = new WidgetFrameConfig(WidgetPlacement.topLeft(0.04, 0.50));
    public int contentWidth = 240;
    public int visibleRows = 3;
    public boolean showBuy = true;
    public boolean showSell = true;
    public boolean showAmounts = true;
    public boolean showOrderCount = true;
    public boolean showHeader = true;
    public boolean showItem = true;
    public EmbeddedSideDisplay sideDisplay = EmbeddedSideDisplay.Relevant;

    public int contentWidth() { return this.contentWidth; }
    public int visibleRows() { return this.visibleRows; }
    public boolean showBuy() { return this.showBuy; }
    public boolean showSell() { return this.showSell; }
    public boolean showAmounts() { return this.showAmounts; }
    public boolean showOrderCount() { return this.showOrderCount; }
    public boolean showHeader() { return this.showHeader; }
    public boolean showItem() { return this.showItem; }
    public EmbeddedSideDisplay sideDisplay() { return this.sideDisplay; }

    public static void resetPreferences(OrderBookPriceWidgetConfig current, OrderBookPriceWidgetConfig defaults) {
        current.contentWidth = defaults.contentWidth;
        current.visibleRows = defaults.visibleRows;
        current.showBuy = defaults.showBuy;
        current.showSell = defaults.showSell;
        current.showAmounts = defaults.showAmounts;
        current.showOrderCount = defaults.showOrderCount;
        current.showHeader = defaults.showHeader;
        current.showItem = defaults.showItem;
        current.sideDisplay = defaults.sideDisplay;
    }
}
