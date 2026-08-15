package com.github.lutzluca.btrbz.core.widgets.ui;

public final class WidgetLayoutTokens {
    public static final int PANEL_HORIZONTAL_PADDING = 6;
    public static final int PANEL_VERTICAL_PADDING = 5;

    public static final int HEADER_GAP = 6;
    public static final int SECTION_GAP = 3;
    public static final int LIST_GAP = 1;
    public static final int LINE_GAP = 1;
    public static final int VALUE_GAP = 8;

    public static final int ROW_HORIZONTAL_PADDING = 3;
    public static final int ROW_VERTICAL_PADDING = 1;
    public static final int ORDER_TEXT_GAP = 3;
    public static final int ROW_ACCENT_WIDTH = 2;
    public static final int SCROLLBAR_THICKNESS = 6;
    public static final int SCROLLBAR_CONTENT_GAP = 2;

    private WidgetLayoutTokens() {}

    public static int twoLineRowHeight(int fontLineHeight) {
        return Math.max(1, fontLineHeight) * 2 + LINE_GAP + ROW_VERTICAL_PADDING * 2;
    }

    public static int singleLineRowHeight(int fontLineHeight) {
        return Math.max(1, fontLineHeight) + ROW_VERTICAL_PADDING * 2;
    }

    public static int listViewportHeight(int rowHeight, int visibleRows) {
        int safeRows = Math.max(0, visibleRows);
        return Math.max(1, Math.max(1, rowHeight) * safeRows + LIST_GAP * Math.max(0, safeRows - 1));
    }

    public static int configuredListViewportHeight(
        int rowHeight,
        int itemCount,
        int maximumVisibleRows,
        boolean fitToContent
    ) {
        int maximumRows = Math.max(1, maximumVisibleRows);
        int visibleRows = fitToContent
            ? Math.min(Math.max(0, itemCount), maximumRows)
            : maximumRows;
        return listViewportHeight(rowHeight, visibleRows);
    }

    public static int panelWidth(int contentWidth) {
        return Math.max(1, contentWidth) + PANEL_HORIZONTAL_PADDING * 2;
    }

    public static int rowTrailingInset(boolean reserveScrollbarSpace) {
        return ROW_HORIZONTAL_PADDING + (reserveScrollbarSpace
            ? SCROLLBAR_THICKNESS + SCROLLBAR_CONTENT_GAP
            : 0);
    }

    public static boolean requiresScrollbar(int rowHeight, int rowCount, int viewportHeight) {
        return listViewportHeight(rowHeight, rowCount) > viewportHeight;
    }
}
