package com.github.lutzluca.btrbz.core.widgets.hud;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("HUD widget visibility")
class HudWidgetBridgeTest {
    @Test
    @DisplayName("suppresses widgets while the F3 debug screen is visible")
    void suppressesForDebugScreen() {
        assertTrue(HudWidgetBridge.shouldSuppressHud(false, false, true, false));
    }

    @Test
    @DisplayName("allows widgets during ordinary gameplay")
    void allowsOrdinaryGameplay() {
        assertFalse(HudWidgetBridge.shouldSuppressHud(false, false, false, false));
    }
}
