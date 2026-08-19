package com.github.lutzluca.btrbz.core.widgets.hud;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Bazaar HUD keybind")
class BtrBzWidgetKeybindsTest {
    @Test
    @DisplayName("accepts toggles only during normal gameplay")
    void acceptsOnlyNormalGameplay() {
        assertTrue(BtrBzWidgetKeybinds.canToggleHud(false, false, false));
        assertFalse(BtrBzWidgetKeybinds.canToggleHud(true, false, false));
        assertFalse(BtrBzWidgetKeybinds.canToggleHud(false, true, false));
        assertFalse(BtrBzWidgetKeybinds.canToggleHud(false, false, true));
    }
}
