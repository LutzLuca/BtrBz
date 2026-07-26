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
}
