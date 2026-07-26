package com.github.lutzluca.btrbz.widgets.framework;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WidgetInstanceStateTest {
    @Test
    void valuesAreStableWithinOneHostStateAndIsolatedAcrossHosts() {
        var first = new WidgetInstanceState();
        var second = new WidgetInstanceState();

        var firstValue = first.getOrCreate("scroll", ArrayList.class, ArrayList::new);

        assertSame(firstValue, first.getOrCreate("scroll", ArrayList.class, ArrayList::new));
        assertNotSame(firstValue, second.getOrCreate("scroll", ArrayList.class, ArrayList::new));
    }

    @Test
    void reusingAKeyWithADifferentTypeFailsFast() {
        var state = new WidgetInstanceState();
        state.getOrCreate("shared", StringBuilder.class, StringBuilder::new);

        assertThrows(
            IllegalStateException.class,
            () -> state.getOrCreate("shared", ArrayList.class, ArrayList::new)
        );
    }
}
