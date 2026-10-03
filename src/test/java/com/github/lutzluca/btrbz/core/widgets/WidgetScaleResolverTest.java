package com.github.lutzluca.btrbz.core.widgets;

import com.github.lutzluca.btrbz.core.widgets.layout.WidgetScaleResolver;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class WidgetScaleResolverTest {
    @Test
    void automaticFitPreservesRequestedScaleOrShrinksToTheSupportedFloor() {
        Assertions.assertEquals(1.25, WidgetScaleResolver.fitToCanvas(1.25, 800, 600, 350, 142),
            "a fitting requested scale stays unchanged");
        Assertions.assertEquals(1.0, WidgetScaleResolver.fitToCanvas(1.0, 800, 600, 350, 142),
            "a fitting ordinary scale stays unchanged");

        double fitted = WidgetScaleResolver.fitToCanvas(1.0, 333, 300, 350, 142);
        Assertions.assertTrue(fitted < 1.0, "an oversized widget shrinks");
        Assertions.assertTrue(WidgetScaleResolver.fitsCanvas(fitted, 333, 300, 350, 142),
            "the shrunken widget fits the available canvas");
        Assertions.assertEquals(0.5, WidgetScaleResolver.fitToCanvas(1.0, 120, 80, 350, 142),
            "automatic shrinking stops at the supported minimum");
    }

    @Test
    void readableFloorRaisesSmallRequestsAndReportsImpossibleFits() {
        Assertions.assertEquals(1.0, WidgetScaleResolver.fitToCanvas(0.75, 1.0, 800, 600, 350, 142),
            "a requested scale below the readable floor is raised");

        double readable = WidgetScaleResolver.fitToCanvas(1.0, 1.0, 120, 80, 350, 142);
        Assertions.assertEquals(1.0, readable, "readability wins when the canvas is too small");
        Assertions.assertFalse(WidgetScaleResolver.fitsCanvas(readable, 120, 80, 350, 142),
            "the caller can detect a readable widget that cannot fit");
        Assertions.assertTrue(WidgetScaleResolver.fitsCanvas(1.0, 800, 600, 350, 142),
            "an ordinary canvas can fit the readable widget");
    }
}
