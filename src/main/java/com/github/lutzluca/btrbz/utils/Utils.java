package com.github.lutzluca.btrbz.utils;

import io.vavr.control.Try;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import static java.util.Locale.ROOT;

public final class Utils {

    public static final long WEEK_DURATION_MS = 7L * 24 * 60 * 60 * 1000;
    public static final long MONTH_DURATION_MS = 30L * 24 * 60 * 60 * 1000;
    private static final Pattern ROMAN_NUMERAL_PATTERN =
        Pattern.compile(
            "^M{0,3}(CM|CD|D?C{0,3})?(XC|XL|L?X{0,3})?(IX|IV|V?I{0,3})$",
            Pattern.CASE_INSENSITIVE
        );

    private Utils() { }

    public static String formatUtcTimestampMillis(long utcMillis) {
        Instant instant = Instant.ofEpochMilli(utcMillis);
        ZonedDateTime zonedDateTime = instant.atZone(ZoneId.systemDefault());
        LocalDateTime localDateTime = zonedDateTime.toLocalDateTime();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        return localDateTime.format(formatter);
    }

    public static Try<Path> atomicDumpToFile(String filename, String content) {
        return atomicDumpToFile(Path.of(filename), content);
    }

    public static Try<Path> atomicDumpToFile(Path path, String content) {
        return Try.of(() -> {
            var target = path.toAbsolutePath();
            var parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            var tmp = parent != null
                ? Files.createTempFile(parent, "btrbz-", ".tmp")
                : Files.createTempFile("btrbz-", ".tmp");

            try {
                Files.writeString(tmp, content);
                return Files.move(
                    tmp,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException ignored) {
                return Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(tmp);
            }
        });
    }

    public static String cleanDisplayName(String displayName) {
        if (displayName == null) {
            return "";
        }

        return Optional
            .ofNullable(ChatFormatting.stripFormatting(displayName))
            .orElse(displayName)
            .replaceAll("\\s+", " ")
            .trim();
    }

    public static String normalizeDisplayName(String displayName) {
        return cleanDisplayName(displayName).toLowerCase(Locale.US);
    }

    public static String titleCase(String value) {
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

    public static Optional<String> customDataId(ItemStack stack) {
        return Optional
            .ofNullable(stack.get(DataComponents.CUSTOM_DATA))
            .flatMap(data -> data.copyTag().getString("id"))
            .map(String::trim)
            .filter(id -> !id.isEmpty());
    }

    public static Optional<String> matchingCustomNameLegacy(ItemStack stack, String expectedName) {
        return Optional
            .ofNullable(stack.get(DataComponents.CUSTOM_NAME))
            .filter(name -> normalizeDisplayName(name.getString()).equals(normalizeDisplayName(expectedName)))
            .map(Utils::legacyFormattedText);
    }

    public static Optional<String> matchingLegacySuffix(Component component, String expectedSuffix) {
        if (component == null || expectedSuffix == null || expectedSuffix.isBlank()) {
            return Optional.empty();
        }

        var plainText = component.getString();
        if (!plainText.endsWith(expectedSuffix)) {
            return Optional.empty();
        }

        return legacyFormattedRange(component, plainText.length() - expectedSuffix.length(), plainText.length())
            .filter(legacy -> normalizeDisplayName(legacy).equals(normalizeDisplayName(expectedSuffix)));
    }

    public static String legacyFormattedText(Component component) {
        return legacyFormattedRange(component, 0, component.getString().length()).orElse("");
    }

    private static Optional<String> legacyFormattedRange(Component component, int startInclusive, int endExclusive) {
        if (component == null || startInclusive < 0 || endExclusive < startInclusive) {
            return Optional.empty();
        }

        var segments = new ArrayList<StyledTextSegment>();
        component.visit((Style style, String content) -> {
            segments.add(new StyledTextSegment(content, style));
            return Optional.empty();
        }, Style.EMPTY);

        var out = new StringBuilder();
        var cursor = 0;
        for (var segment : segments) {
            var content = segment.content();
            if (content.isEmpty()) {
                continue;
            }

            var segmentStart = cursor;
            var segmentEnd = cursor + content.length();
            cursor = segmentEnd;

            var copyStart = Math.max(startInclusive, segmentStart);
            var copyEnd = Math.min(endExclusive, segmentEnd);
            if (copyStart >= copyEnd) {
                continue;
            }

            appendLegacyStyle(out, segment.style());
            out.append(content, copyStart - segmentStart, copyEnd - segmentStart);
        }

        return out.isEmpty() ? Optional.empty() : Optional.of(out.toString());
    }

    private static void appendLegacyStyle(StringBuilder out, Style style) {
        TextColor color = style.getColor();
        if (color != null) {
            //? if <26.2 {
            var formatting = ChatFormatting.getByName(color.serialize());
            if (formatting != null && formatting.isColor()) {
                out.append(formatting);
            }
            //?} else {
            /*String serialized = color.serialize();
            if (serialized.startsWith("#")) {
                out.append("§x");
                for (char c : serialized.substring(1).toCharArray()) {
                    out.append('§').append(c);
                }
            } else {
                out.append(ChatFormatting.valueOf(serialized.toUpperCase(ROOT)));
            }
            *///?}
        }

        if (style.isObfuscated()) {
            out.append(ChatFormatting.OBFUSCATED);
        }
        if (style.isBold()) {
            out.append(ChatFormatting.BOLD);
        }
        if (style.isStrikethrough()) {
            out.append(ChatFormatting.STRIKETHROUGH);
        }
        if (style.isUnderlined()) {
            out.append(ChatFormatting.UNDERLINE);
        }
        if (style.isItalic()) {
            out.append(ChatFormatting.ITALIC);
        }
    }

    public static String formatDecimal(double value, int places, boolean groupings) {
        if (places < 0) {
            throw new IllegalArgumentException("Decimal places must be non-negative");
        }
        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.US);
        formatter.setMinimumFractionDigits(places);
        formatter.setMaximumFractionDigits(places);
        formatter.setGroupingUsed(groupings);

        return formatter.format(value);
    }

    public static Try<Number> parseUsFormattedNumber(String str) {
        var nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setParseIntegerOnly(false);
        return Try.of(() -> nf.parse(str.trim()));
    }

    public static <T> List<T> removeIfAndReturn(Collection<T> coll, Predicate<? super T> pred) {
        List<T> removed = new ArrayList<>();
        var it = coll.iterator();
        while (it.hasNext()) {
            var val = it.next();
            if (pred.test(val)) {
                removed.add(val);
                it.remove();
            }
        }
        return removed;
    }

    public static <T> Optional<T> getFirst(List<T> list) {
        return Try.of(list::getFirst).toJavaOptional();
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public static <T, U> Optional<Pair<T, U>> zipOptionals(Optional<T> first, Optional<U> second) {
        if (first.isEmpty() || second.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Pair.of(first.get(), second.get()));
    }

    public static <T, U> @NotNull Optional<@NotNull Pair<@NotNull T, @NotNull U>> zipNullables(@Nullable T first, @Nullable U second) {
        if (first == null || second == null) {
            return Optional.empty();
        }

        return Optional.of(Pair.of(first, second));
    }

    public static String formatCompact(double value, int places) {
        if (places < 0) {
            throw new IllegalArgumentException("places must be >= 0");
        }

        double abs = Math.abs(value);
        String suffix;
        double scaled;

        if (abs >= 1_000_000_000) {
            scaled = value / 1_000_000_000d;
            suffix = "B";
        } else if (abs >= 1_000_000) {
            scaled = value / 1_000_000d;
            suffix = "M";
        } else if (abs >= 1_000) {
            scaled = value / 1_000d;
            suffix = "k";
        } else {
            scaled = value;
            suffix = "";
        }

        StringBuilder pattern = new StringBuilder("0");
        if (places > 0) {
            pattern.append(".");
            pattern.append("0".repeat(places));
        }

        return new DecimalFormat(pattern.toString(), DecimalFormatSymbols.getInstance(Locale.US)).format(scaled) + suffix;
    }


    public static boolean isValidRomanNumeral(String roman) {
        return roman != null
            && !roman.isBlank()
            && ROMAN_NUMERAL_PATTERN.matcher(roman.trim()).matches();
    }

    public static Optional<Integer> parseRomanNumeral(String roman) {
        if (roman == null || roman.isBlank()) {
            return Optional.empty();
        }

        var normalized = roman.trim().toUpperCase(Locale.US);
        if (!isValidRomanNumeral(normalized)) {
            return Optional.empty();
        }

        var result = 0;
        var previous = 0;
        for (var i = normalized.length() - 1; i >= 0; i--) {
            var value = romanValue(normalized.charAt(i));
            if (value < previous) {
                result -= value;
            } else {
                result += value;
                previous = value;
            }
        }

        if (result <= 0 || result > 3999 || !intToRoman(result).equals(normalized)) {
            return Optional.empty();
        }
        return Optional.of(result);
    }

    public static String intToRoman(int num) {
        if (num <= 0 || num > 3999) {
            throw new IllegalArgumentException("Input out of bounds valid range of [1; 3999]");
        }

        final int[] vals = { 1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1 };
        final String[] symbols = {
            "M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"
        };

        var ret = new StringBuilder();

        for (int i = 0; i < vals.length; i++) {
            while (num >= vals[i]) {
                num -= vals[i];
                ret.append(symbols[i]);
            }

            if (num == 0) {
                break;
            }
        }

        return ret.toString();
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

    public static String formatDuration(double totalMinutes) {
        if (totalMinutes < 1) {
            return "< 1m";
        }

        long hours = (long) (totalMinutes / 60);
        long minutes = (long) (totalMinutes % 60);

        if (hours > 0) {
            if (minutes > 0) {
                return String.format("%dh %dm", hours, minutes);
            }

            return String.format("%dh", hours);
        }

        return String.format("%dm", minutes);
    }

    private record StyledTextSegment(String content, Style style) { }
}
