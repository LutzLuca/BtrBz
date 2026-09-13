package com.github.lutzluca.btrbz.data.conversions;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

final class ConversionNames {

    private static final Pattern ROMAN_NUMERAL = Pattern.compile(
        "^M{0,3}(CM|CD|D?C{0,3})?(XC|XL|L?X{0,3})?(IX|IV|V?I{0,3})$",
        Pattern.CASE_INSENSITIVE);

    private ConversionNames() {}

    static String titleCase(String value) {
        var words = value.toLowerCase(Locale.US).split("\\s+");
        var builder = new StringBuilder();
        for (var word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return builder.toString();
    }

    static boolean isValidRomanNumeral(String roman) {
        return roman != null
            && !roman.isBlank()
            && ROMAN_NUMERAL.matcher(roman.trim()).matches();
    }

    static Optional<Integer> parseRomanNumeral(String roman) {
        if (roman == null || roman.isBlank()) {
            return Optional.empty();
        }

        var normalized = roman.trim().toUpperCase(Locale.US);
        if (!isValidRomanNumeral(normalized)) {
            return Optional.empty();
        }

        var result = 0;
        var previous = 0;
        for (var index = normalized.length() - 1; index >= 0; index--) {
            var value = romanValue(normalized.charAt(index));
            if (value < previous) {
                result -= value;
            } else {
                result += value;
                previous = value;
            }
        }

        if (result <= 0 || result > 3999 || !toRoman(result).equals(normalized)) {
            return Optional.empty();
        }
        return Optional.of(result);
    }

    static String toRoman(int number) {
        if (number <= 0 || number > 3999) {
            throw new IllegalArgumentException("Input out of bounds valid range of [1; 3999]");
        }

        final int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        final String[] symbols = {
            "M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"
        };

        var result = new StringBuilder();
        for (var index = 0; index < values.length; index++) {
            while (number >= values[index]) {
                number -= values[index];
                result.append(symbols[index]);
            }
            if (number == 0) {
                break;
            }
        }
        return result.toString();
    }

    private static int romanValue(char character) {
        return switch (character) {
            case 'I' -> 1;
            case 'V' -> 5;
            case 'X' -> 10;
            case 'L' -> 50;
            case 'C' -> 100;
            case 'D' -> 500;
            case 'M' -> 1000;
            default -> 0;
        };
    }
}
