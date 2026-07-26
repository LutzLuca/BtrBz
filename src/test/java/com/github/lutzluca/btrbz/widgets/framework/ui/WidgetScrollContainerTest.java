package com.github.lutzluca.btrbz.widgets.framework.ui;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WidgetScrollContainerTest {
    @Test
    void scrollContainerRatherThanListShellOwnsPersistentCapture() {
        assertTrue(PersistentMouseCapture.class.isAssignableFrom(WidgetScrollContainer.class));
        assertFalse(PersistentMouseCapture.class.isAssignableFrom(WidgetScrollListComponent.class));
        assertFalse(Arrays.stream(WidgetScrollListComponent.class.getMethods())
            .anyMatch(method -> method.getName().equals("synchronizeContentPosition")));
    }
}
