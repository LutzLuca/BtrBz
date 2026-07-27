package com.github.lutzluca.btrbz.core.widgets.dailylimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DailyLimitComponentTest {
    @Test
    void accountsWhilePresentationIsDisabledAndPersistsTheMutation() {
        var config = new DailyLimitWidgetConfig();
        config.frame.enabled = false;
        config.lastResetEpochDay = 20_000;
        var saves = new java.util.concurrent.atomic.AtomicInteger();
        var component = new DailyLimitComponent(() -> config, saves::incrementAndGet, () -> 20_000);

        component.onTransaction(1_250);

        assertEquals(1_250, config.usedToday);
        assertEquals(1, saves.get());
    }

    @Test
    void resetsOnlyWhenUtcEpochDayChanges() {
        var config = new DailyLimitWidgetConfig();
        config.usedToday = 12_345;
        assertTrue(DailyLimitComponent.resetForDay(config, 20_000));
        assertEquals(0, config.usedToday);
        assertEquals(20_000, config.lastResetEpochDay);
        config.usedToday = 6_789;
        assertFalse(DailyLimitComponent.resetForDay(config, 20_000));
        assertEquals(6_789, config.usedToday);
        assertTrue(DailyLimitComponent.resetForDay(config, 20_001));
        assertEquals(0, config.usedToday);
    }
}
