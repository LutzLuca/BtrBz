package com.github.lutzluca.btrbz.core.widgets.orderbook;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetFrameConfig;
import com.github.lutzluca.btrbz.core.widgets.layout.WidgetPlacement;

public final class OrderBookWidgetConfig {
    static final int MIN_CONTENT_WIDTH = 220;

    public enum DepthMode {
        Cumulative, Relative, Hidden
    }

    public enum ScrollMode {
        Independent, Together
    }

    public WidgetFrameConfig frame = new WidgetFrameConfig(WidgetPlacement.topLeft(0.29, 0.31));
    public int contentWidth = 400;
    public int visibleRows = 8;
    public DepthMode depthMode = DepthMode.Hidden;
    public boolean showOrderCount = true;
    public ScrollMode scrolling = ScrollMode.Independent;

    public static void resetPreferences(OrderBookWidgetConfig current, OrderBookWidgetConfig defaults) {
        current.contentWidth = defaults.contentWidth;
        current.visibleRows = defaults.visibleRows;
        current.depthMode = defaults.depthMode;
        current.showOrderCount = defaults.showOrderCount;
        current.scrolling = defaults.scrolling;
    }
}
