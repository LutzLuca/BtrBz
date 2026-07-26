package com.github.lutzluca.btrbz.widgets.framework.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WidgetLayoutTokensTest {
    @Test
    void rowAndViewportHeightsShareTheFontBasedBaseline() {
        int rowHeight = WidgetLayoutTokens.twoLineRowHeight(9);

        assertEquals(21, rowHeight);
        assertEquals(109, WidgetLayoutTokens.listViewportHeight(rowHeight, 5));
        assertEquals(87, WidgetLayoutTokens.listViewportHeight(rowHeight, 4));
        assertEquals(11, WidgetLayoutTokens.singleLineRowHeight(9));
    }

    @Test
    void panelWidthIncludesSharedLogicalPaddingOnce() {
        assertEquals(342, WidgetLayoutTokens.panelWidth(330));
        assertEquals(210, WidgetLayoutTokens.panelWidth(198));
    }

    @Test
    void configuredViewportCanFitContentOrReserveTheMaximumHeight() {
        assertEquals(1, WidgetLayoutTokens.configuredListViewportHeight(21, 0, 5, true));
        assertEquals(43, WidgetLayoutTokens.configuredListViewportHeight(21, 2, 5, true));
        assertEquals(109, WidgetLayoutTokens.configuredListViewportHeight(21, 5, 5, true));
        assertEquals(109, WidgetLayoutTokens.configuredListViewportHeight(21, 8, 5, true));
        assertEquals(109, WidgetLayoutTokens.configuredListViewportHeight(21, 0, 5, false));
        assertEquals(109, WidgetLayoutTokens.configuredListViewportHeight(21, 2, 5, false));
    }
}
