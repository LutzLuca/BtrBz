package com.github.lutzluca.btrbz.core.widgets.dailylimit;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetFrameConfig;
import com.github.lutzluca.btrbz.core.widgets.layout.WidgetPlacement;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetDisplayOptions.NumberStyle;

public final class DailyLimitWidgetConfig {
    public WidgetFrameConfig frame = new WidgetFrameConfig(WidgetPlacement.topLeft(0.76, 0.58));
    public int contentWidth = 220;
    public NumberStyle numberStyle = NumberStyle.Compact;
    public double dailyLimit = 15_000_000_000d;
    public double usedToday = 0;
    public long lastResetEpochDay = -1;

    public DailyLimitWidgetConfig() {
        this.frame.enabled = false;
    }

    public static void resetPreferences(DailyLimitWidgetConfig current, DailyLimitWidgetConfig defaults) {
        current.contentWidth = defaults.contentWidth;
        current.numberStyle = defaults.numberStyle;
        current.dailyLimit = defaults.dailyLimit;
    }
}
