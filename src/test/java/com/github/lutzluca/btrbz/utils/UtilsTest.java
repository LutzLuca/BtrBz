package com.github.lutzluca.btrbz.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UtilsTest {

    @Nested
    @DisplayName("formatting codes")
    class FormattingCodes {

        @Test
        void stripsStandardLegacyFormattingCodes() {
            assertEquals("Enchanted Diamond", Utils.stripFormattingCodes("§a§lEnchanted Diamond"));
            assertEquals("", Utils.stripFormattingCodes(null));
        }

        @Test
        void stripsNonstandardScoreboardFormattingTokens() {
            var line = "Purse: §61,395,2§j§639,458";
            var stripped = Utils.stripScoreboardFormattingCodes(line);
            var parsed = Utils.parseUsFormattedNumber(stripped.replace("Purse:", "").trim());

            assertEquals("Purse: 1,395,239,458", stripped);
            assertTrue(parsed.isSuccess());
            assertEquals(1_395_239_458L, parsed.get().longValue());
        }
    }

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
        void promotesValuesThatRoundIntoTheNextTier() {
            assertEquals("1M", Utils.formatCompact(999_950));
            assertEquals("1.0M", Utils.formatCompact(999_950, 1));
        }

        @Test
        void formatsZero() {
            assertEquals("0", Utils.formatCompact(0, 0));
        }

        @Test
        void formatsNegativeValues() {
            assertEquals("-1.5k", Utils.formatCompact(-1500, 1));
        }

        @Test
        void formatsWidgetValuesWithoutForcedTrailingZeros() {
            assertEquals("26.12B", Utils.formatCompact(26_120_000_000d));
            assertEquals("26B", Utils.formatCompact(26_000_000_000d));
            assertEquals("21.2M", Utils.formatCompact(21_200_000d));
            assertEquals("875", Utils.formatCompact(875d));
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
}
