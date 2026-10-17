package com.github.lutzluca.btrbz.utils;

import io.vavr.control.Try;
import java.text.NumberFormat;
import java.text.DecimalFormat;
import java.text.ParsePosition;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.apache.commons.lang3.tuple.Pair;

public final class Utils {

    private static final Pattern LEGACY_FORMATTING_CODE = Pattern.compile("(?i)\u00a7[0-9A-FK-OR]");
    private static final Pattern SCOREBOARD_FORMATTING_TOKEN = Pattern.compile("\u00a7.");

    private Utils() {}

    public static String stripFormattingCodes(String text) {
        return text == null ? "" : LEGACY_FORMATTING_CODE.matcher(text).replaceAll("");
    }

    public static String stripScoreboardFormattingCodes(String text) {
        return SCOREBOARD_FORMATTING_TOKEN.matcher(stripFormattingCodes(text)).replaceAll("").trim();
    }

    public static String cleanDisplayName(String displayName) {
        if (displayName == null) {
            return "";
        }

        return stripFormattingCodes(displayName)
            .replaceAll("\\s+", " ")
            .trim();
    }

    public static String normalizeDisplayName(String displayName) {
        return cleanDisplayName(displayName).toLowerCase(Locale.US);
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

    /** Parses a complete US decimal, retaining precision and rejecting malformed thousands groups. */
    public static Try<BigDecimal> parseUsFormattedNumber(String text) {
        return Try.of(() -> {
            String token = text.trim();
            var format = (DecimalFormat) NumberFormat.getNumberInstance(Locale.US);
            format.setParseBigDecimal(true);
            var position = new ParsePosition(0);
            var number = format.parse(token, position);

            if (!(number instanceof BigDecimal value) || position.getIndex() != token.length()
                || token.indexOf('E') >= 0
                || !Character.isDigit(token.charAt(token.length() - 1))) {
                throw new IllegalArgumentException("Invalid US decimal: " + text);
            }

            int decimal = token.indexOf('.');
            String whole = decimal < 0 ? token : token.substring(0, decimal);
            if (whole.indexOf(',') >= 0) {
                format.setMaximumFractionDigits(0);
                format.setRoundingMode(RoundingMode.DOWN);
                if (!format.format(value).equals(whole)) {
                    throw new IllegalArgumentException("Invalid thousands grouping: " + text);
                }
            }

            return value;
        });
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
        return list.isEmpty() ? Optional.empty() : Optional.ofNullable(list.getFirst());
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public static <T, U> Optional<Pair<T, U>> zipOptionals(Optional<T> first, Optional<U> second) {
        if (first.isEmpty() || second.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Pair.of(first.get(), second.get()));
    }

    public static String formatCompact(double value, int places) {
        if (places < 0) {
            throw new IllegalArgumentException("places must be >= 0");
        }

        var compact = compactValue(value, places);
        return formatDecimal(compact.value(), places, false) + compact.suffix();
    }

    public static String formatCompact(double value) {
        int places = Math.abs(value) >= 1_000_000_000 ? 2 : 1;
        var compact = compactValue(value, places);
        var formatter = NumberFormat.getNumberInstance(Locale.US);
        formatter.setMinimumFractionDigits(0);
        formatter.setMaximumFractionDigits(places);
        formatter.setGroupingUsed(false);
        return formatter.format(compact.value()) + compact.suffix();
    }

    private static CompactValue compactValue(double value, int places) {
        double absolute = Math.abs(value);
        double rollover = 1_000d - 0.5d * Math.pow(10d, -places);

        if (absolute >= 1_000_000d * rollover) {
            return new CompactValue(value / 1_000_000_000d, "B");
        }
        if (absolute >= 1_000d * rollover) {
            return new CompactValue(value / 1_000_000d, "M");
        }
        if (absolute >= rollover) {
            return new CompactValue(value / 1_000d, "k");
        }
        return new CompactValue(value, "");
    }

    private record CompactValue(double value, String suffix) {}
}
