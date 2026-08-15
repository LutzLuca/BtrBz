package com.github.lutzluca.btrbz.core.widgets.manager;

import com.github.lutzluca.btrbz.core.widgets.layout.WidgetCanvas;
import com.github.lutzluca.btrbz.core.widgets.layout.WidgetPlacement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Widget manager launcher layout")
class WidgetManagerLauncherLayoutTest {
    @Nested
    @DisplayName("automatic scaling")
    class AutomaticScaling {
        @Test
        @DisplayName("uses scaled physical bounds while preserving its logical size")
        void resolvesScaledBounds() {
            var layout = WidgetManagerLauncherLayout.resolve(
                new WidgetCanvas(0, 0, 320, 180),
                WidgetPlacement.topLeft(0.0, 1.0),
                22,
                0.71);

            assertEquals(0.71, layout.scale());
            assertEquals(16, layout.localBounds().width());
            assertEquals(16, layout.localBounds().height());
            assertEquals(164, layout.localBounds().y());
        }

        @Test
        @DisplayName("translates local placement into screen hit bounds")
        void translatesScreenBounds() {
            var layout = WidgetManagerLauncherLayout.resolve(
                new WidgetCanvas(12, 18, 320, 180),
                WidgetPlacement.topLeft(0.5, 0.5),
                22,
                1.0);

            assertEquals(160, layout.localBounds().x());
            assertEquals(90, layout.localBounds().y());
            assertEquals(172, layout.screenBounds().x());
            assertEquals(108, layout.screenBounds().y());
            assertEquals(22, layout.screenBounds().width());
            assertEquals(22, layout.screenBounds().height());
        }
    }
}
