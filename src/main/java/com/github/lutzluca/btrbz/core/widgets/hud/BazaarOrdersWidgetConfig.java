package com.github.lutzluca.btrbz.core.widgets.hud;

import com.github.lutzluca.btrbz.core.widgets.WidgetMath;
import com.github.lutzluca.btrbz.core.widgets.config.WidgetFrameConfig;
import com.github.lutzluca.btrbz.core.widgets.layout.WidgetPlacement;

public final class BazaarOrdersWidgetConfig {
    public static final int MIN_VISIBLE_ORDERS = 1;
    public static final int MAX_VISIBLE_ORDERS = 10;

    public enum HudMode { Detailed, StatusCounts }
    public WidgetFrameConfig frame = new WidgetFrameConfig(WidgetPlacement.topLeft(1, 0.006));
    public HudMode mode = HudMode.Detailed;
    public int visibleOrders = 4;
    public int contentWidth = 200;
    public boolean abbreviateEnchanted = false;
    public boolean showQueue = true;
    public boolean showUndercutGap = false;

    public int supportedVisibleOrders() {
        return WidgetMath.clamp(this.visibleOrders, MIN_VISIBLE_ORDERS, MAX_VISIBLE_ORDERS);
    }

    public static void resetPreferences(BazaarOrdersWidgetConfig current, BazaarOrdersWidgetConfig defaults) {
        current.mode = defaults.mode;
        current.visibleOrders = defaults.visibleOrders;
        current.contentWidth = defaults.contentWidth;
        current.abbreviateEnchanted = defaults.abbreviateEnchanted;
        current.showQueue = defaults.showQueue;
        current.showUndercutGap = defaults.showUndercutGap;
    }
}
