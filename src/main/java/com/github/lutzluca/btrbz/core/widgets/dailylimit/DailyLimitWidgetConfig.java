package com.github.lutzluca.btrbz.core.widgets.dailylimit;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetFrameConfig;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetDisplayOptions.NumberStyle;
import com.github.lutzluca.btrbz.core.widgets.WidgetPlacement;

public final class DailyLimitWidgetConfig {
    public enum LimitDisplay { UsedLimit, Remaining, Percentage, Compact }

    public WidgetFrameConfig frame = new WidgetFrameConfig(WidgetPlacement.topLeft(0.76, 0.58));
    public int contentWidth = 180;
    public LimitDisplay display = LimitDisplay.UsedLimit;
    public NumberStyle numberStyle = NumberStyle.Compact;
    public boolean showHeader = true;
    public int warningThreshold = 75;
    public int criticalThreshold = 90;
    public double dailyLimit = 15_000_000_000d;
    public double usedToday = 0;
    public long lastResetEpochDay = -1;

    public int contentWidth() { return this.contentWidth; }
    public LimitDisplay display() { return this.display; }
    public NumberStyle numberStyle() { return this.numberStyle; }
    public boolean showHeader() { return this.showHeader; }
    public int warningThreshold() { return this.warningThreshold; }
    public int criticalThreshold() { return this.criticalThreshold; }

    public static void resetPreferences(DailyLimitWidgetConfig current, DailyLimitWidgetConfig defaults) {
        current.contentWidth = defaults.contentWidth;
        current.display = defaults.display;
        current.numberStyle = defaults.numberStyle;
        current.showHeader = defaults.showHeader;
        current.warningThreshold = defaults.warningThreshold;
        current.criticalThreshold = defaults.criticalThreshold;
        current.dailyLimit = defaults.dailyLimit;
    }
}
