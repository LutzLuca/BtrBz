package com.github.lutzluca.btrbz.core.widgets.hud;

import com.github.lutzluca.btrbz.core.widgets.ui.WidgetLayoutTokens;

public final class BazaarOrderCountPolicy {
    public static final int MINIMUM = 1;
    public static final int MAXIMUM = 10;
    public static final int DEFAULT = 6;

    private BazaarOrderCountPolicy() {}

    static int configuredCount(int value) {
        return Math.max(MINIMUM, Math.min(MAXIMUM, value));
    }

    static int visibleCount(
        int configuredCount,
        int availableOrders,
        int availablePanelHeight,
        int rowHeight,
        int lineHeight
    ) {
        int desired = Math.min(configuredCount(configuredCount), Math.max(0, availableOrders));
        if (desired == 0) return 0;

        for (int visible = desired; visible >= MINIMUM; visible--) {
            boolean hasOverflow = availableOrders > visible;
            if (panelHeight(visible, hasOverflow, rowHeight, lineHeight) <= availablePanelHeight) {
                return visible;
            }
        }

        return MINIMUM;
    }

    static int panelHeight(int visibleRows, boolean hasOverflow, int rowHeight, int lineHeight) {
        int safeRows = Math.max(0, visibleRows);
        int contentHeight = Math.max(1, lineHeight);
        int childCount = 1;

        if (safeRows > 0) {
            contentHeight += WidgetLayoutTokens.listViewportHeight(rowHeight, safeRows);
            childCount++;
        }
        if (hasOverflow) {
            contentHeight += Math.max(1, lineHeight);
            childCount++;
        }

        contentHeight += WidgetLayoutTokens.SECTION_GAP * Math.max(0, childCount - 1);
        return contentHeight + WidgetLayoutTokens.PANEL_VERTICAL_PADDING * 2;
    }
}
