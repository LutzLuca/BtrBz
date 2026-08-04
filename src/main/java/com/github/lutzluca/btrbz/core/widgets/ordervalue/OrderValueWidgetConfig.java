package com.github.lutzluca.btrbz.core.widgets.ordervalue;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetFrameConfig;
import com.github.lutzluca.btrbz.core.widgets.layout.WidgetPlacement;

public final class OrderValueWidgetConfig {
    public enum ValueDisplay { Detailed, Summary }
    public WidgetFrameConfig frame = new WidgetFrameConfig(WidgetPlacement.topLeft(0.393, 0.077));
    public int contentWidth = 205;
    public ValueDisplay display = ValueDisplay.Detailed;
    public static void resetPreferences(OrderValueWidgetConfig current, OrderValueWidgetConfig defaults) {
        current.contentWidth = defaults.contentWidth;
        current.display = defaults.display;
    }
}
