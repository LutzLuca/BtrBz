package com.github.lutzluca.btrbz.data.conversions;

import com.github.lutzluca.btrbz.utils.Utils;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import net.minecraft.nbt.CompoundTag;

final class EnchantedBookIdParser {

    static final String GENERIC_BOOK_ID = "ENCHANTED_BOOK";

    /*
     * Accepted formats:
     * - BUY|SELL <enchantment name> <roman-or-arabic-level>
     * - <enchantment name> <roman-or-arabic-level>
     * - <raw enchantment id>;<arabic level>
     */
    private static final Pattern ACTION_PREFIX = Pattern.compile("^(BUY|SELL)\\s+", Pattern.CASE_INSENSITIVE);
    private static final Pattern DISPLAY_NAME = Pattern.compile("^(.+?)\\s+([IVXLCDM]+|\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern RAW_ENCHANTMENT_ID = Pattern.compile("^([A-Z0-9_\\-]+);(\\d+)$", Pattern.CASE_INSENSITIVE);

    private EnchantedBookIdParser() { }

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

        return Utils.parseRomanNumeral(level);
    }

    private static Optional<Integer> parseArabicLevel(String level) {
        try {
            return Optional.of(Integer.parseInt(level));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
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
