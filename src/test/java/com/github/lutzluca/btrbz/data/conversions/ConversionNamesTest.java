package com.github.lutzluca.btrbz.data.conversions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ConversionNamesTest {

    @Test
    void titleCasesDerivedProductNames() {
        assertEquals("Ultimate Wise", ConversionNames.titleCase("ULTIMATE WISE"));
    }

    @Nested
    @DisplayName("toRoman")
    class ToRoman {

        @Test
        void convertsRepresentativeAndBoundaryValues() {
            assertEquals("I", ConversionNames.toRoman(1));
            assertEquals("IV", ConversionNames.toRoman(4));
            assertEquals("IX", ConversionNames.toRoman(9));
            assertEquals("LVIII", ConversionNames.toRoman(58));
            assertEquals("MCMXCIV", ConversionNames.toRoman(1994));
            assertEquals("MMMCMXCIX", ConversionNames.toRoman(3999));
        }

        @Test
        void rejectsOutOfBoundsValues() {
            assertThrows(IllegalArgumentException.class, () -> ConversionNames.toRoman(0));
            assertThrows(IllegalArgumentException.class, () -> ConversionNames.toRoman(-1));
            assertThrows(IllegalArgumentException.class, () -> ConversionNames.toRoman(4000));
        }
    }

    @Nested
    @DisplayName("Roman parsing")
    class RomanParsing {

        @Test
        void acceptsValidNumeralsIncludingMixedCase() {
            assertTrue(ConversionNames.isValidRomanNumeral("XIV"));
            assertTrue(ConversionNames.isValidRomanNumeral("MMMCMXCIX"));
            assertTrue(ConversionNames.isValidRomanNumeral("mCmXcIv"));
            assertEquals(Optional.of(4), ConversionNames.parseRomanNumeral("IV"));
            assertEquals(Optional.of(58), ConversionNames.parseRomanNumeral("LVIII"));
            assertEquals(Optional.of(1994), ConversionNames.parseRomanNumeral("mCmXcIv"));
        }

        @Test
        void rejectsBlankOrInvalidValues() {
            assertFalse(ConversionNames.isValidRomanNumeral(null));
            assertFalse(ConversionNames.isValidRomanNumeral(""));
            assertFalse(ConversionNames.isValidRomanNumeral("   "));
            assertFalse(ConversionNames.isValidRomanNumeral("IIII"));
            assertFalse(ConversionNames.isValidRomanNumeral("VX"));
            assertFalse(ConversionNames.isValidRomanNumeral("ABC"));
            assertTrue(ConversionNames.parseRomanNumeral("").isEmpty());
            assertTrue(ConversionNames.parseRomanNumeral("IIII").isEmpty());
            assertTrue(ConversionNames.parseRomanNumeral("VX").isEmpty());
        }
    }
}
