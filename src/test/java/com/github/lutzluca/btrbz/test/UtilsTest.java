package com.github.lutzluca.btrbz.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.github.lutzluca.btrbz.utils.Utils;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UtilsTest {

    @Nested
    @DisplayName("formatDecimal")
    class FormatDecimal {

        @Test
        void formatsWithGroupingAndRounding() {
            assertEquals("1,234.57", Utils.formatDecimal(1234.567, 2, true));
        }

        @Test
        void formatsWithZeroDecimalPlaces() {
            assertEquals("1,235", Utils.formatDecimal(1234.567, 0, true));
        }

        @Test
        void formatsWithoutGrouping() {
            assertEquals("1234.57", Utils.formatDecimal(1234.567, 2, false));
        }

        @Test
        void formatsNegativeValues() {
            assertEquals("-12.30", Utils.formatDecimal(-12.3, 2, false));
        }

        @Test
        void rejectsNegativePlaces() {
            assertThrows(IllegalArgumentException.class, () -> Utils.formatDecimal(1.23, -1, true));
        }
    }

    @Nested
    @DisplayName("formatCompact")
    class FormatCompact {

        @Test
        void rejectsNegativePlaces() {
            assertThrows(IllegalArgumentException.class, () -> Utils.formatCompact(100, -1));
        }

        @Test
        void formatsPlainValues() {
            assertEquals("999", Utils.formatCompact(999, 0));
        }

        @Test
        void formatsThousands() {
            assertEquals("1.5k", Utils.formatCompact(1500, 1));
        }

        @Test
        void formatsMillions() {
            assertEquals("2.5M", Utils.formatCompact(2_500_000, 1));
        }

        @Test
        void formatsBillions() {
            assertEquals("3.1B", Utils.formatCompact(3_100_000_000d, 1));
        }

        @Test
        void formatsTierBoundaries() {
            assertEquals("999", Utils.formatCompact(999, 0));
            assertEquals("1.0k", Utils.formatCompact(1_000, 1));
            assertEquals("1.0M", Utils.formatCompact(1_000_000, 1));
            assertEquals("1.0B", Utils.formatCompact(1_000_000_000d, 1));
        }

        @Test
        void formatsZero() {
            assertEquals("0", Utils.formatCompact(0, 0));
        }

        @Test
        void formatsNegativeValues() {
            assertEquals("-1.5k", Utils.formatCompact(-1500, 1));
        }
    }

    @Nested
    @DisplayName("parseUsFormattedNumber")
    class ParseUsFormattedNumber {

        @Test
        void parsesValidIntegers() {
            var parsed = Utils.parseUsFormattedNumber("1234");

            assertTrue(parsed.isSuccess());
            assertEquals(1234L, parsed.get().longValue());
        }

        @Test
        void parsesValidDecimals() {
            var parsed = Utils.parseUsFormattedNumber("12.75");

            assertTrue(parsed.isSuccess());
            assertEquals(12.75d, parsed.get().doubleValue(), 0.000001d);
        }

        @Test
        void parsesCommaGroupedNumbers() {
            var parsed = Utils.parseUsFormattedNumber("1,234,567.89");

            assertTrue(parsed.isSuccess());
            assertEquals(1_234_567.89d, parsed.get().doubleValue(), 0.000001d);
        }

        @Test
        void failsForInvalidStrings() {
            assertTrue(Utils.parseUsFormattedNumber("abc").isFailure());
        }

        @Test
        void failsForEmptyString() {
            assertTrue(Utils.parseUsFormattedNumber("").isFailure());
        }
    }

    @Nested
    @DisplayName("intToRoman")
    class IntToRoman {

        @Test
        void convertsRepresentativeValues() {
            assertEquals("IV", Utils.intToRoman(4));
            assertEquals("IX", Utils.intToRoman(9));
            assertEquals("LVIII", Utils.intToRoman(58));
            assertEquals("MCMXCIV", Utils.intToRoman(1994));
        }

        @Test
        void convertsBoundaryValues() {
            assertEquals("I", Utils.intToRoman(1));
            assertEquals("MMMCMXCIX", Utils.intToRoman(3999));
        }

        @Test
        void rejectsOutOfBoundsValues() {
            assertThrows(IllegalArgumentException.class, () -> Utils.intToRoman(0));
            assertThrows(IllegalArgumentException.class, () -> Utils.intToRoman(-1));
            assertThrows(IllegalArgumentException.class, () -> Utils.intToRoman(4000));
        }
    }

    @Nested
    @DisplayName("isValidRomanNumeral")
    class IsValidRomanNumeral {

        @Test
        void acceptsValidNumerals() {
            assertTrue(Utils.isValidRomanNumeral("XIV"));
            assertTrue(Utils.isValidRomanNumeral("MMMCMXCIX"));
        }

        @Test
        void rejectsInvalidStrings() {
            assertFalse(Utils.isValidRomanNumeral("IIII"));
            assertFalse(Utils.isValidRomanNumeral("VX"));
            assertFalse(Utils.isValidRomanNumeral("ABC"));
        }

        @Test
        void acceptsEmptyStringWithCurrentRegex() {
            assertTrue(Utils.isValidRomanNumeral(""));
        }

        @Test
        void acceptsMixedCase() {
            assertTrue(Utils.isValidRomanNumeral("mCmXcIv"));
        }
    }

    @Nested
    @DisplayName("parseRomanNumeral")
    class ParseRomanNumeral {

        @Test
        void parsesRepresentativeValues() {
            assertEquals(Optional.of(4), Utils.parseRomanNumeral("IV"));
            assertEquals(Optional.of(58), Utils.parseRomanNumeral("LVIII"));
            assertEquals(Optional.of(1994), Utils.parseRomanNumeral("MCMXCIV"));
        }

        @Test
        void parsesMixedCase() {
            assertEquals(Optional.of(1994), Utils.parseRomanNumeral("mCmXcIv"));
        }

        @Test
        void rejectsBlankOrInvalidValues() {
            assertTrue(Utils.parseRomanNumeral("").isEmpty());
            assertTrue(Utils.parseRomanNumeral("IIII").isEmpty());
            assertTrue(Utils.parseRomanNumeral("VX").isEmpty());
        }
    }

    @Nested
    @DisplayName("formatDuration")
    class FormatDuration {

        @Test
        void formatsSubMinuteDurations() {
            assertEquals("< 1m", Utils.formatDuration(0.5));
        }

        @Test
        void formatsExactHours() {
            assertEquals("2h", Utils.formatDuration(120));
        }

        @Test
        void formatsHoursAndMinutes() {
            assertEquals("2h 5m", Utils.formatDuration(125));
        }

        @Test
        void formatsZero() {
            assertEquals("< 1m", Utils.formatDuration(0));
        }
    }

    @Nested
    @DisplayName("zipNullables")
    class ZipNullables {

        @Test
        void returnsPairWhenBothPresent() {
            assertEquals(Optional.of(Pair.of("left", 1)), Utils.zipNullables("left", 1));
        }

        @Test
        void returnsEmptyWhenEitherIsNull() {
            assertTrue(Utils.zipNullables(null, 1).isEmpty());
            assertTrue(Utils.zipNullables("left", null).isEmpty());
        }

        @Test
        void returnsEmptyWhenBothAreNull() {
            assertTrue(Utils.zipNullables(null, null).isEmpty());
        }
    }

    @Nested
    @DisplayName("zipOptionals")
    class ZipOptionals {

        @Test
        void returnsPairWhenBothPresent() {
            assertEquals(Optional.of(Pair.of("left", 1)), Utils.zipOptionals(Optional.of("left"), Optional.of(1)));
        }

        @Test
        void returnsEmptyWhenEitherOptionalIsEmpty() {
            assertTrue(Utils.zipOptionals(Optional.empty(), Optional.of(1)).isEmpty());
            assertTrue(Utils.zipOptionals(Optional.of("left"), Optional.empty()).isEmpty());
        }

        @Test
        void returnsEmptyWhenBothOptionalsAreEmpty() {
            assertTrue(Utils.zipOptionals(Optional.empty(), Optional.empty()).isEmpty());
        }
    }

    @Nested
    @DisplayName("getFirst")
    class GetFirst {

        @Test
        void returnsFirstElementForNonEmptyList() {
            assertEquals(Optional.of("first"), Utils.getFirst(List.of("first", "second")));
        }

        @Test
        void returnsEmptyOptionalForEmptyList() {
            assertTrue(Utils.getFirst(List.<String>of()).isEmpty());
        }
    }

    @Nested
    @DisplayName("removeIfAndReturn")
    class RemoveIfAndReturn {

        @Test
        void removesAndReturnsMatchingItems() {
            var values = new ArrayList<>(List.of(1, 2, 3, 4));

            var removed = Utils.removeIfAndReturn(values, value -> value % 2 == 0);

            assertIterableEquals(List.of(2, 4), removed);
            assertIterableEquals(List.of(1, 3), values);
        }

        @Test
        void returnsEmptyWhenNothingMatches() {
            var values = new ArrayList<>(List.of(1, 3, 5));

            var removed = Utils.removeIfAndReturn(values, value -> value % 2 == 0);

            assertTrue(removed.isEmpty());
            assertIterableEquals(List.of(1, 3, 5), values);
        }

        @Test
        void removesAllWhenEverythingMatches() {
            var values = new ArrayList<>(List.of(2, 4, 6));

            var removed = Utils.removeIfAndReturn(values, value -> value % 2 == 0);

            assertIterableEquals(List.of(2, 4, 6), removed);
            assertTrue(values.isEmpty());
        }
    }
}
