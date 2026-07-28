package com.github.lutzluca.btrbz.core.widgets.presets;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetFrameConfig;
import com.github.lutzluca.btrbz.core.widgets.layout.WidgetPlacement;
import java.util.ArrayList;
import java.util.List;

public final class OrderPresetsWidgetConfig {
    public WidgetFrameConfig frame = new WidgetFrameConfig(
        WidgetPlacement.topLeft(0.55, 0.58),
        "sign",
        WidgetPlacement.topLeft(0.62, 0.08)
    );
    public int contentWidth = 100;
    public boolean maximum = true;
    public boolean clipboard = true;
    public boolean showDisabled = true;
    public boolean showTooltips = true;
    public List<Integer> volumes = new ArrayList<>();
    public static void resetPreferences(OrderPresetsWidgetConfig current, OrderPresetsWidgetConfig defaults) {
        current.contentWidth = defaults.contentWidth;
        current.maximum = defaults.maximum;
        current.clipboard = defaults.clipboard;
        current.showDisabled = defaults.showDisabled;
        current.showTooltips = defaults.showTooltips;
    }
}
