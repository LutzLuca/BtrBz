package com.github.lutzluca.btrbz.widgets.framework.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoundedRectangleElementRenderStateTest {
    @Test
    void perimeterIsClosedAndContainedByThePanelBounds() {
        int x = 7;
        int y = 11;
        int width = 100;
        int height = 40;
        int radius = 5;
        int segments = 5;

        float[] perimeter = RoundedRectangleElementRenderState.buildPerimeter(
            x,
            y,
            width,
            height,
            radius,
            segments
        );

        assertEquals((5 + segments * 4) * 2, perimeter.length);
        assertEquals(perimeter[0], perimeter[perimeter.length - 2]);
        assertEquals(perimeter[1], perimeter[perimeter.length - 1]);

        for (int i = 0; i < perimeter.length; i += 2) {
            assertTrue(perimeter[i] >= x && perimeter[i] <= x + width);
            assertTrue(perimeter[i + 1] >= y && perimeter[i + 1] <= y + height);
        }

        assertTrue(signedArea(perimeter) < 0, "winding must match owo's circle triangle fan");
    }

    @Test
    void radiusIsClampedForSmallPanels() {
        float[] perimeter = RoundedRectangleElementRenderState.buildPerimeter(0, 0, 8, 4, 9, 3);

        for (int i = 0; i < perimeter.length; i += 2) {
            assertTrue(perimeter[i] >= 0 && perimeter[i] <= 8);
            assertTrue(perimeter[i + 1] >= 0 && perimeter[i + 1] <= 4);
        }
    }

    private static double signedArea(float[] perimeter) {
        double twiceArea = 0.0;
        for (int i = 0; i < perimeter.length - 2; i += 2) {
            twiceArea += perimeter[i] * perimeter[i + 3]
                - perimeter[i + 1] * perimeter[i + 2];
        }
        return twiceArea / 2.0;
    }
}
