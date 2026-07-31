package com.github.lutzluca.btrbz.core.widgets.config;

import com.github.lutzluca.btrbz.core.widgets.bookmarks.BookmarksWidgetConfig;
import com.github.lutzluca.btrbz.core.widgets.dailylimit.DailyLimitWidgetConfig;
import com.github.lutzluca.btrbz.core.widgets.hud.BazaarOrdersWidgetConfig;
import com.github.lutzluca.btrbz.core.widgets.orderbook.OrderBookPriceWidgetConfig;
import com.github.lutzluca.btrbz.core.widgets.orderbook.OrderBookWidgetConfig;
import com.github.lutzluca.btrbz.core.widgets.ordervalue.OrderValueWidgetConfig;
import com.github.lutzluca.btrbz.core.widgets.presets.OrderPresetsWidgetConfig;
import com.github.lutzluca.btrbz.core.widgets.pricedifference.PriceDifferenceWidgetConfig;
import com.github.lutzluca.btrbz.core.widgets.trackedorders.TrackedOrdersWidgetConfig;
import com.github.lutzluca.btrbz.core.widgets.layout.WidgetPlacement;

/** Serialized aggregate; each concrete widget owns its persisted config type. */
public final class WidgetsConfig {
    public static final int DEFAULT_BACKGROUND = 0x840C0C0C;
    public static final WidgetPlacement DEFAULT_MANAGER_LAUNCHER_POSITION =
        WidgetPlacement.topLeft(0.05, 0.91);

    public double globalFineTuneScale = 1.0;
    public int globalBackground = DEFAULT_BACKGROUND;
    public int managerPanelWidth = 210;
    public int managerPanelHeightPercent = 75;
    public boolean runtimeDragging = false;
    public boolean managerLauncherVisible = true;
    public WidgetPlacement managerLauncherPosition = DEFAULT_MANAGER_LAUNCHER_POSITION;

    public BazaarOrdersWidgetConfig bazaarOrders = new BazaarOrdersWidgetConfig();
    public TrackedOrdersWidgetConfig trackedOrders = new TrackedOrdersWidgetConfig();
    public OrderValueWidgetConfig orderValue = new OrderValueWidgetConfig();
    public OrderBookWidgetConfig orderBookScreen = new OrderBookWidgetConfig();
    public OrderBookPriceWidgetConfig orderBookPrice = new OrderBookPriceWidgetConfig();
    public BookmarksWidgetConfig bookmarks = new BookmarksWidgetConfig();
    public OrderPresetsWidgetConfig orderPresets = new OrderPresetsWidgetConfig();
    public DailyLimitWidgetConfig orderLimit = new DailyLimitWidgetConfig();
    public PriceDifferenceWidgetConfig priceDiff = new PriceDifferenceWidgetConfig();
}
