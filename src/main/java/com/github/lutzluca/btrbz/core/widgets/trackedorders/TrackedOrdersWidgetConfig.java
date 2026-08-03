package com.github.lutzluca.btrbz.core.widgets.trackedorders;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetFrameConfig;
import com.github.lutzluca.btrbz.core.widgets.layout.WidgetPlacement;

public final class TrackedOrdersWidgetConfig {
    public enum TrackedLayout { Standard, Compact }
    public enum TrackedSort { Manual, Newest, Status }

    public WidgetFrameConfig frame = new WidgetFrameConfig(WidgetPlacement.topLeft(0.04, 0.18));
    public int contentWidth = 200;
    public int visibleRows = 5;
    public TrackedLayout layout = TrackedLayout.Standard;
    public TrackedSort sort = TrackedSort.Manual;
    public static void resetPreferences(TrackedOrdersWidgetConfig current, TrackedOrdersWidgetConfig defaults) {
        current.contentWidth = defaults.contentWidth;
        current.visibleRows = defaults.visibleRows;
        current.layout = defaults.layout;
        current.sort = defaults.sort;
    }
}
