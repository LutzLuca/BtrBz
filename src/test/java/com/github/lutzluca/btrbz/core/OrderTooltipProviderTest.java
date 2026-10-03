package com.github.lutzluca.btrbz.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OrderTooltipProviderTest {

    @ParameterizedTest(name = "{0} minutes -> {1}")
    @CsvSource({
        "0.5, < 1m",
        "120, 2h",
        "125, 2h 5m",
        "0, < 1m"
    })
    void formatsDuration(double minutes, String expected) {
        Assertions.assertEquals(expected, OrderTooltipProvider.formatDuration(minutes));
    }
}
