package com.github.lutzluca.btrbz.core.widgets.dailylimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.lutzluca.btrbz.core.widgets.cache.UtcDayTracker;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetDisplayOptions.NumberStyle;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DailyLimitComponentTest {
    @Test
    void formatsCompactAndExactValues() {
        var preview = DailyLimitWidgetData.preview();

        assertEquals("11.25B / 15B", DailyLimitWidgetView.formattedValue(preview, NumberStyle.Compact));
        assertEquals(
            "11,250,000,000 / 15,000,000,000",
            DailyLimitWidgetView.formattedValue(preview, NumberStyle.Exact));
    }

    @Test
    void defaultsToCompactAndResetsTheNumberFormat() {
        var config = new DailyLimitWidgetConfig();
        assertEquals(NumberStyle.Compact, config.numberStyle);

        config.numberStyle = NumberStyle.Exact;
        DailyLimitWidgetConfig.resetPreferences(config, new DailyLimitWidgetConfig());

        assertEquals(NumberStyle.Compact, config.numberStyle);
    }

    @Test
    void accountsWhilePresentationIsDisabledAndPersistsTheMutation() {
        var config = new DailyLimitWidgetConfig();
        config.frame.enabled = false;
        config.lastResetEpochDay = 20_000;
        var saves = new AtomicInteger();
        var dayTracker = new UtcDayTracker(() -> 20_000);
        var component = new DailyLimitComponent(() -> config, saves::incrementAndGet, dayTracker);

        component.onTransaction(1_250);

        assertSame(dayTracker, component.utcDayTracker());
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
