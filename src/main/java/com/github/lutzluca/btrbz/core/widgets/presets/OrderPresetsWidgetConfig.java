package com.github.lutzluca.btrbz.core.widgets.presets;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetFrameConfig;
import com.github.lutzluca.btrbz.core.widgets.layout.WidgetPlacement;
import java.util.ArrayList;
import java.util.List;

public final class OrderPresetsWidgetConfig {
    public WidgetFrameConfig frame = new WidgetFrameConfig(
        WidgetPlacement.topLeft(0.642, 0.30),
        "sign",
        WidgetPlacement.topLeft(0.60, 0.183));
    public int contentWidth = 50;
    public int visibleRows = 5;
    public boolean clipboard = true;
    public boolean showDisabled = false;
    public List<Integer> volumes = new ArrayList<>();

    public static void resetPreferences(OrderPresetsWidgetConfig current, OrderPresetsWidgetConfig defaults) {
        current.contentWidth = defaults.contentWidth;
        current.visibleRows = defaults.visibleRows;
        current.clipboard = defaults.clipboard;
        current.showDisabled = defaults.showDisabled;
    }
}
