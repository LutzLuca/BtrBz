package com.github.lutzluca.btrbz.core.widgets.manager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WidgetManagerPanelStateTest {
    @Test
    void configuredDimensionsStayWithinTheSupportedRange() {
        assertEquals(WidgetManagerPanelState.MINIMUM_WIDTH, WidgetManagerPanelState.configuredWidth(1));
        assertEquals(200, WidgetManagerPanelState.configuredWidth(200));
        assertEquals(WidgetManagerPanelState.MAXIMUM_WIDTH, WidgetManagerPanelState.configuredWidth(1_000));
        assertEquals(
            WidgetManagerPanelState.MINIMUM_HEIGHT_PERCENT,
            WidgetManagerPanelState.configuredHeightPercent(1));
        assertEquals(
            WidgetManagerPanelState.MAXIMUM_HEIGHT_PERCENT,
            WidgetManagerPanelState.configuredHeightPercent(100));
    }

    @Test
    void defaultsToTopRightWithTheRequestedMargin() {
        var state = new WidgetManagerPanelState();

        state.fitToViewport(800, 450, 222, 380, 18);

        assertEquals(560, state.x());
        assertEquals(18, state.y());
    }

    @Test
    void draggingKeepsThePanelInsideTheViewport() {
        var state = new WidgetManagerPanelState();
        state.fitToViewport(800, 450, 222, 380, 18);
        state.beginDrag(570, 28);

        assertTrue(state.dragTo(-100, -100, 800, 450, 222, 380));
        assertEquals(0, state.x());
        assertEquals(0, state.y());

        assertTrue(state.dragTo(2_000, 2_000, 800, 450, 222, 380));
        assertEquals(578, state.x());
        assertEquals(70, state.y());
        assertTrue(state.endDrag());
        assertFalse(state.isDragging());
    }

    @Test
    void minimizingAndRestoringKeepTheRightEdgeStationary() {
        var state = new WidgetManagerPanelState();
        state.fitToViewport(800, 450, 222, 380, 18);
        state.beginDrag(570, 28);
        state.dragTo(410, 110, 800, 450, 222, 380);
        state.endDrag();

        int expandedRightEdge = state.x() + 222;
        state.resizePanel(222, 150, 800, 450, 32, 18);

        assertEquals(expandedRightEdge, state.x() + 150);

        state.resizePanel(150, 222, 800, 450, 380, 18);
        assertEquals(expandedRightEdge, state.x() + 222);
    }

    @Test
    void defaultPositionContinuesToFollowTheRightEdgeAfterResize() {
        var state = new WidgetManagerPanelState();
        state.fitToViewport(800, 450, 222, 380, 18);

        state.fitToViewport(1_000, 600, 222, 510, 18);

        assertEquals(760, state.x());
        assertEquals(18, state.y());
    }
}
