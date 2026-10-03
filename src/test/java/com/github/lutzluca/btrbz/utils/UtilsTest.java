package com.github.lutzluca.btrbz.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class UtilsTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "\u00a7a\u00a7lEnchanted Diamond")
    void stripsStandardLegacyFormattingCodes(String input) {
        Assertions.assertEquals(input == null ? "" : "Enchanted Diamond", Utils.stripFormattingCodes(input));
    }

    @Test
    void stripsNonstandardScoreboardFormattingTokens() {
        var line = "Purse: \u00a761,395,2\u00a7j\u00a7639,458";
        var stripped = Utils.stripScoreboardFormattingCodes(line);
        var parsed = Utils.parseUsFormattedNumber(stripped.replace("Purse:", "").trim());

        Assertions.assertEquals("Purse: 1,395,239,458", stripped);
        Assertions.assertTrue(parsed.isSuccess());
        Assertions.assertEquals(1_395_239_458L, parsed.get().longValue());
    }

    @ParameterizedTest(name = "{0}, places={1}, grouped={2} -> {3}")
    @CsvSource(delimiter = '|', value = {
        "1234.567 | 2 | true | 1,234.57",
        "1234.567 | 0 | true | 1,235",
        "1234.567 | 2 | false | 1234.57",
        "-12.3 | 2 | false | -12.30"
    })
    void formatsDecimals(double value, int places, boolean grouped, String expected) {
        Assertions.assertEquals(expected, Utils.formatDecimal(value, places, grouped));
    }

    @Test
    void rejectsNegativePrecisionInBothFormatters() {
        Assertions.assertAll(
            () -> Assertions.assertThrows(
                IllegalArgumentException.class, () -> Utils.formatDecimal(1.23, -1, true),
                "decimal precision"),
            () -> Assertions.assertThrows(
                IllegalArgumentException.class, () -> Utils.formatCompact(100, -1),
                "compact precision"));
    }

    @ParameterizedTest(name = "{0}, places={1} -> {2}")
    @CsvSource({
        "999, 0, 999",
        "1500, 1, 1.5k",
        "2500000, 1, 2.5M",
        "3100000000, 1, 3.1B",
        "1000, 1, 1.0k",
        "1000000, 1, 1.0M",
        "1000000000, 1, 1.0B",
        "999950, 1, 1.0M",
        "0, 0, 0",
        "-1500, 1, -1.5k"
    })
    void formatsCompactValuesAtRequestedPrecision(double value, int places, String expected) {
        Assertions.assertEquals(expected, Utils.formatCompact(value, places));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "999950, 1M",
        "26120000000, 26.12B",
        "26000000000, 26B",
        "21200000, 21.2M",
        "875, 875"
    })
    void formatsWidgetValuesWithoutForcedTrailingZeros(double value, String expected) {
        Assertions.assertEquals(expected, Utils.formatCompact(value));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', value = {
        "1234 | 1234",
        "12.75 | 12.75",
        "1,234,567.89 | 1234567.89"
    })
    void parsesUsFormattedNumbers(String input, double expected) {
        var parsed = Utils.parseUsFormattedNumber(input);

        Assertions.assertTrue(parsed.isSuccess());
        Assertions.assertEquals(expected, parsed.get().doubleValue(), 0.000001d);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", ""})
    void rejectsInvalidNumbers(String input) {
        Assertions.assertTrue(Utils.parseUsFormattedNumber(input).isFailure());
    }
}
