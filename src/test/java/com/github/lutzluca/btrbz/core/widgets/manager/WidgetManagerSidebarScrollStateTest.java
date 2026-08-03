package com.github.lutzluca.btrbz.core.widgets.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Widget manager sidebar scroll state")
class WidgetManagerSidebarScrollStateTest {
    @Test
    @DisplayName("overview and detail offsets are restored independently")
    void restoresIndependentOffsets() {
        var state = new WidgetManagerSidebarScrollState();
        state.mount(false);
        state.saveMountedOffset(42);
        state.openDetail();
        state.mount(true);
        state.saveMountedOffset(17);

        assertEquals(42, state.mount(false));
        assertEquals(17, state.mount(true));
    }

    @Test
    @DisplayName("opening another detail starts at the top")
    void resetsNewDetailToTop() {
        var state = new WidgetManagerSidebarScrollState();
        state.mount(true);
        state.saveMountedOffset(31);

        state.openDetail();

        assertEquals(0, state.mount(true));
    }
}
