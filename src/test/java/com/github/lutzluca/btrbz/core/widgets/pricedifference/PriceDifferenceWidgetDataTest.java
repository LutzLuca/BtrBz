package com.github.lutzluca.btrbz.core.widgets.pricedifference;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Price difference widget data")
class PriceDifferenceWidgetDataTest {
    @Test
    @DisplayName("preserve fractional spreads until total calculation")
    void preservesFractionalSpreads() {
        var snapshot = new PriceDifferenceWidgetData.Snapshot("Product", Optional.empty(), 0.4, 100_000);

        assertEquals(0.4, snapshot.perItem());
        assertEquals(40_000.0, snapshot.total());
    }
}
