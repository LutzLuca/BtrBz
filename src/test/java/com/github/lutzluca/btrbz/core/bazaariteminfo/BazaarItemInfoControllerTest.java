package com.github.lutzluca.btrbz.core.bazaariteminfo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.lutzluca.coflnet.HistoryRange;
import org.junit.jupiter.api.Test;

class BazaarItemInfoControllerTest {
    @Test
    void activationRequiresKeyRealSlotNonEmptyStackAndResolvedBazaarTag() {
        assertTrue(BazaarItemInfoController.canOpen(true, false, true, true, true));
        assertFalse(BazaarItemInfoController.canOpen(false, false, true, true, true));
        assertFalse(BazaarItemInfoController.canOpen(true, false, false, true, true));
        assertFalse(BazaarItemInfoController.canOpen(true, false, true, false, true));
        assertFalse(BazaarItemInfoController.canOpen(true, false, true, true, false));
    }

    @Test
    void focusedTextInputSuppressesTheHotkey() {
        assertFalse(BazaarItemInfoController.canOpen(true, true, true, true, true));
    }

    @Test
    void visibleRangesMapDirectlyToCoflnetPresetEndpoints() {
        assertTrue(BazaarItemInfoRange.Hour.sdkRange() == HistoryRange.Preset.HOUR);
        assertTrue(BazaarItemInfoRange.Day.sdkRange() == HistoryRange.Preset.DAY);
        assertTrue(BazaarItemInfoRange.Week.sdkRange() == HistoryRange.Preset.WEEK);
    }

    @Test
    void headerNameWidthFitsTheResponsivePanel() {
        assertEquals(201, BazaarItemInfoScreen.headerNameWidth(320));
        assertTrue(BazaarItemInfoScreen.headerNameWidth(854) > 201);
    }
}
