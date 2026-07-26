package com.github.lutzluca.btrbz.widgets.framework;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WidgetPlacementTest {
    @Test
    void onePinsWidgetToBottomRightWithoutLeavingCanvas() {
        var bounds = WidgetPlacement.topLeft(1.0, 1.0).resolve(320, 180, 80, 30);

        assertEquals(240, bounds.x());
        assertEquals(150, bounds.y());
        assertEquals(80, bounds.width());
        assertEquals(30, bounds.height());
    }

    @Test
    void dragReleaseConvertsAbsolutePositionToFractions() {
        var placement = WidgetPlacement.fromAbsolute(120, 60, 320, 180, 80, 30);

        assertEquals(0.5, placement.x(), 0.0001);
        assertEquals(0.4, placement.y(), 0.0001);
    }

    @Test
    void oversizedWidgetPinsToOrigin() {
        var bounds = WidgetPlacement.topLeft(0.8, 0.7).resolve(100, 50, 180, 90);

        assertEquals(0, bounds.x());
        assertEquals(0, bounds.y());
        assertEquals(180, bounds.width());
        assertEquals(90, bounds.height());
    }

    @Test
    void scaleChangesBoundsButDoesNotRewriteFractions() {
        var placement = WidgetPlacement.topLeft(0.25, 0.5);

        var normal = placement.resolve(400, 200, 100, 40);
        var scaled = placement.resolve(400, 200, 150, 60);

        assertEquals(75, normal.x());
        assertEquals(80, normal.y());
        assertEquals(63, scaled.x());
        assertEquals(70, scaled.y());
        assertEquals(0.25, placement.x());
        assertEquals(0.5, placement.y());
    }
}
