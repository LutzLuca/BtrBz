package com.github.lutzluca.btrbz.core.widgets.pricedifference;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetFrameConfig;
import com.github.lutzluca.btrbz.core.widgets.layout.WidgetPlacement;

public final class PriceDifferenceWidgetConfig {
    public WidgetFrameConfig frame = new WidgetFrameConfig(WidgetPlacement.topLeft(0.4, 0.086));
    public int contentWidth = 190;

    public static void resetPreferences(PriceDifferenceWidgetConfig current, PriceDifferenceWidgetConfig defaults) {
        current.contentWidth = defaults.contentWidth;
    }
}
