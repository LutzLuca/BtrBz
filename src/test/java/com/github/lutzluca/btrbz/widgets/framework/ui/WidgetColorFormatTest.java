package com.github.lutzluca.btrbz.widgets.framework.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WidgetColorFormatTest {
    @Test
    void argbRoundTripsThroughEightDigitHex() {
        assertEquals("#D9181B22", WidgetColorFormat.formatArgb(0xD9181B22));
        assertEquals(0xD9181B22, WidgetColorFormat.parse("#D9181B22", 0).orElseThrow());
    }

    @Test
    void sixDigitHexPreservesTheCurrentAlpha() {
        assertEquals(0xD9112233, WidgetColorFormat.parse("112233", 0xD9000000).orElseThrow());
    }

    @Test
    void incompleteOrInvalidHexIsRejected() {
        assertTrue(WidgetColorFormat.parse("#12345", 0xFF000000).isEmpty());
        assertTrue(WidgetColorFormat.parse("#GG1122", 0xFF000000).isEmpty());
    }
}
