package com.github.lutzluca.btrbz.core.widgets.trackedorders;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrackedOrdersWidgetTest {
    @Test
    void placesTheFilledCountInTheHeaderStatus() {
        var data = new BazaarWidgetViewData.OrdersData(List.of(), 3);

        assertEquals("2 active · 3 filled", TrackedOrdersWidget.headerStatus(data, 2, true));
        assertEquals("2 active", TrackedOrdersWidget.headerStatus(data, 2, false));
    }
}
