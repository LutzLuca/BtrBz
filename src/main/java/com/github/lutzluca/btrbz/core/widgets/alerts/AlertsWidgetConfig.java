package com.github.lutzluca.btrbz.core.widgets.alerts;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetFrameConfig;
import com.github.lutzluca.btrbz.core.widgets.layout.WidgetPlacement;

public final class AlertsWidgetConfig {
    public WidgetFrameConfig frame = new WidgetFrameConfig(WidgetPlacement.topLeft(1.0, 0.08));
    public int contentWidth = 108;
    public boolean collapsed;

    public static void resetPreferences(AlertsWidgetConfig current, AlertsWidgetConfig defaults) {
        current.contentWidth = defaults.contentWidth;
        current.collapsed = defaults.collapsed;
    }
}
