package com.github.lutzluca.btrbz.data.conversions;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import net.minecraft.nbt.CompoundTag;
import com.github.lutzluca.btrbz.utils.Utils;

final class EnchantedBookProductIds {

    static final String GENERIC_BOOK_ID = "ENCHANTED_BOOK";

    private static final Pattern ACTION_PREFIX = Pattern.compile("^(BUY|SELL)\\s+", Pattern.CASE_INSENSITIVE);
    private static final Pattern DISPLAY_NAME = Pattern.compile("^(.+?)\\s+([IVXLCDM]+|\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern RAW_ENCHANTMENT_ID = Pattern.compile("^([A-Z0-9_\\-]+);(\\d+)$", Pattern.CASE_INSENSITIVE);

    private EnchantedBookProductIds() { }

    static boolean isGenericBookId(String rawProductId) {
        return GENERIC_BOOK_ID.equals(normalizeToken(rawProductId));
    }

    static Optional<String> fromCustomData(CompoundTag customData) {
        if (customData == null) {
            return Optional.empty();
        }

        return customData
            .getCompound("enchantments")
            .filter(enchantments -> enchantments.size() == 1)
            .flatMap(enchantments -> {
                var enchantment = enchantments.keySet().iterator().next();
                return enchantments
                    .getInt(enchantment)
                    .flatMap(level -> toProductId(enchantment, level));
            });
    }

    static Optional<String> fromRawProductId(String rawProductId) {
        var matcher = RAW_ENCHANTMENT_ID.matcher(rawProductId == null ? "" : rawProductId.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }

        return parseArabicLevel(matcher.group(2))
            .flatMap(level -> toProductId(matcher.group(1), level));
    }

    static Optional<String> fromDisplayName(String displayName) {
        return parseDisplayName(displayName)
            .flatMap(parsed -> toProductId(parsed.enchantmentName(), parsed.level()));
    }

    static Optional<String> canonicalDisplayName(String displayName) {
        return parseDisplayName(displayName)
            .map(parsed -> parsed.enchantmentName() + " " + formatDisplayLevel(parsed.level()));
    }

    static String stripActionPrefix(String displayName) {
        return ACTION_PREFIX
            .matcher(Utils.cleanDisplayName(displayName))
            .replaceFirst("")
            .trim();
    }

    private static Optional<ParsedDisplayName> parseDisplayName(String displayName) {
        var name = stripActionPrefix(displayName);
        var matcher = DISPLAY_NAME.matcher(name);
        if (!matcher.matches()) {
            return Optional.empty();
        }

        var enchantmentName = matcher.group(1).trim();
        var level = matcher.group(2).trim();
        if (enchantmentName.isEmpty()) {
            return Optional.empty();
        }

        return parseLevel(level)
            .map(parsedLevel -> new ParsedDisplayName(enchantmentName, parsedLevel));
    }

    private static Optional<String> toProductId(String enchantmentName, int level) {
        var normalized = normalizeToken(enchantmentName);
        if (normalized.isBlank() || level < 0) {
            return Optional.empty();
        }

        return Optional.of("ENCHANTMENT_" + normalized + "_" + level);
    }

    private static String formatDisplayLevel(int level) {
        if (level > 0 && level <= 3999) {
            return Utils.intToRoman(level);
        }

        return Integer.toString(level);
    }

    private static Optional<Integer> parseLevel(String level) {
        if (level == null || level.isBlank()) {
            return Optional.empty();
        }

        if (level.chars().allMatch(Character::isDigit)) {
            return parseArabicLevel(level);
        }

        var roman = level.toUpperCase(Locale.US);
        if (!Utils.isValidRomanNumeral(roman)) {
            return Optional.empty();
        }

        var result = 0;
        var previous = 0;
        for (var i = roman.length() - 1; i >= 0; i--) {
            var value = romanValue(roman.charAt(i));
            if (value < previous) {
                result -= value;
            } else {
                result += value;
                previous = value;
            }
        }
        return Optional.of(result);
    }

    private static Optional<Integer> parseArabicLevel(String level) {
        try {
            return Optional.of(Integer.parseInt(level));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private static int romanValue(char c) {
        return switch (c) {
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

    private static String normalizeToken(String value) {
        if (value == null) {
            return "";
        }

        return value
            .trim()
            .toUpperCase(Locale.US)
            .replaceAll("[^A-Z0-9]+", "_")
            .replaceAll("^_+|_+$", "");
    }

    private record ParsedDisplayName(String enchantmentName, int level) { }
}
