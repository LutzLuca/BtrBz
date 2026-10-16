package com.github.lutzluca.btrbz.core.widgets.pricedifference;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetFrameConfig;
import com.github.lutzluca.btrbz.core.widgets.layout.WidgetPlacement;

public final class PriceDifferenceWidgetConfig {
    public enum Mode {
        Compact, Detailed
    }

    public enum NumberFormat {
        Short, Full
    }

    public WidgetFrameConfig frame = new WidgetFrameConfig(WidgetPlacement.topLeft(0.4, 0.086));
    public int contentWidth = 260;
    public Mode mode = Mode.Compact;
    public NumberFormat numberFormat = NumberFormat.Short;
    public boolean showInventoryTotal = true;
    public boolean showBookTotals = true;
    public boolean showWeeklyActivity = true;

    public static void resetPreferences(PriceDifferenceWidgetConfig current, PriceDifferenceWidgetConfig defaults) {
        current.contentWidth = defaults.contentWidth;
        current.mode = defaults.mode;
        current.numberFormat = defaults.numberFormat;
        current.showInventoryTotal = defaults.showInventoryTotal;
        current.showBookTotals = defaults.showBookTotals;
        current.showWeeklyActivity = defaults.showWeeklyActivity;
    }
}
