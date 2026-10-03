package com.github.lutzluca.btrbz.core.widgets.dailylimit;

import com.github.lutzluca.btrbz.core.widgets.cache.UtcDayTracker;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetDisplayOptions.NumberStyle;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DailyLimitComponentTest {
    @Test
    void formatsCompactAndExactValues() {
        var preview = DailyLimitWidgetData.preview();

        Assertions.assertEquals("11.25B / 15B", DailyLimitWidgetView.formattedValue(preview, NumberStyle.Compact));
        Assertions.assertEquals(
            "11,250,000,000 / 15,000,000,000",
            DailyLimitWidgetView.formattedValue(preview, NumberStyle.Exact));
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

        Assertions.assertSame(dayTracker, component.utcDayTracker());
        Assertions.assertEquals(1_250, config.usedToday);
        Assertions.assertEquals(1, saves.get());
    }

    @Test
    void resetsOnlyWhenUtcEpochDayChanges() {
        var config = new DailyLimitWidgetConfig();
        config.usedToday = 12_345;
        Assertions.assertTrue(DailyLimitComponent.resetForDay(config, 20_000));
        Assertions.assertEquals(0, config.usedToday);
        Assertions.assertEquals(20_000, config.lastResetEpochDay);
        config.usedToday = 6_789;
        Assertions.assertFalse(DailyLimitComponent.resetForDay(config, 20_000));
        Assertions.assertEquals(6_789, config.usedToday);
        Assertions.assertTrue(DailyLimitComponent.resetForDay(config, 20_001));
        Assertions.assertEquals(0, config.usedToday);
    }
}
