package com.github.lutzluca.btrbz.core.widgets.hud;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("HUD widget visibility")
class HudWidgetBridgeTest {
    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "player list visible, true, false, false, true",
        "F3 overlay visible, false, true, false, true",
        "level missing, false, false, true, true",
        "ordinary gameplay, false, false, false, false"
    })
    void suppressesHudForEachGameplayGuard(
        String description,
        boolean playerListVisible,
        boolean debugOverlayVisible,
        boolean missingLevel,
        boolean suppressed
    ) {
        Assertions.assertEquals(suppressed,
            HudWidgetBridge.shouldSuppressHud(playerListVisible, debugOverlayVisible, missingLevel),
            description);
    }
}
