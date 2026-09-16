package com.github.lutzluca.btrbz.core.widgets.hud;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("HUD widget visibility")
class HudWidgetBridgeTest {
    @Test
    @DisplayName("suppresses widgets while the player list is visible")
    void suppressesForPlayerList() {
        assertTrue(HudWidgetBridge.shouldSuppressHud(true, false, false));
    }

    @Test
    @DisplayName("suppresses widgets while the F3 overlay is visible")
    void suppressesForDebugOverlay() {
        assertTrue(HudWidgetBridge.shouldSuppressHud(false, true, false));
    }

    @Test
    @DisplayName("suppresses widgets while the level is missing")
    void suppressesForMissingLevel() {
        assertTrue(HudWidgetBridge.shouldSuppressHud(false, false, true));
    }

    @Test
    @DisplayName("allows widgets during ordinary gameplay")
    void allowsOrdinaryGameplay() {
        assertFalse(HudWidgetBridge.shouldSuppressHud(false, false, false));
    }
}
