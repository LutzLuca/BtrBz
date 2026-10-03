package com.github.lutzluca.btrbz.core.widgets.ui;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ReorderableScrollListComponentTest {
    @Test
    void equalElapsedTimeScrollsTheSameDistanceAtDifferentFrameRates() {
        for (int framesPerSecond : new int[]{30, 60, 144, 240}) {
            double distance = 0.0;
            for (int frame = 0; frame < framesPerSecond; frame++) {
                distance += ReorderableScrollListComponent.autoScrollDistance(
                    200, 100, 100, 20.0f / framesPerSecond);
            }
            Assertions.assertEquals(180.0, distance, 0.0001);
        }
    }

    @Test
    void pointerDepthIncreasesSpeedUpToTheCap() {
        double nearEdge = ReorderableScrollListComponent.autoScrollDistance(187, 100, 100, 1.0f);
        double deeper = ReorderableScrollListComponent.autoScrollDistance(193, 100, 100, 1.0f);
        double atEdge = ReorderableScrollListComponent.autoScrollDistance(200, 100, 100, 1.0f);
        double outside = ReorderableScrollListComponent.autoScrollDistance(500, 100, 100, 1.0f);

        Assertions.assertTrue(nearEdge > 0.0);
        Assertions.assertTrue(deeper > nearEdge);
        Assertions.assertTrue(atEdge > deeper);
        Assertions.assertEquals(9.0, atEdge);
        Assertions.assertEquals(atEdge, outside);
    }

    @Test
    void centerDoesNotScrollAndBothEdgesHaveTheSameSpeed() {
        Assertions.assertEquals(0.0,
            ReorderableScrollListComponent.autoScrollDistance(150, 100, 100, 1.0f));
        Assertions.assertEquals(-9.0,
            ReorderableScrollListComponent.autoScrollDistance(100, 100, 100, 1.0f));
        Assertions.assertEquals(9.0,
            ReorderableScrollListComponent.autoScrollDistance(200, 100, 100, 1.0f));
    }

    @Test
    void stalledFramesHaveBoundedMovement() {
        Assertions.assertEquals(18.0,
            ReorderableScrollListComponent.autoScrollDistance(200, 100, 100, 20.0f));
        Assertions.assertEquals(0.0,
            ReorderableScrollListComponent.autoScrollDistance(200, 100, 100, 0.0f));
    }
}
