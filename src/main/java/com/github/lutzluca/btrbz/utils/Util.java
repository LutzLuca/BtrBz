package com.github.lutzluca.btrbz.utils;

import io.vavr.control.Try;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.DecimalFormat;
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
import net.minecraft.client.MinecraftClient;
import org.apache.commons.lang3.tuple.Pair;

public final class Util {

    public static final long WEEK_DURATION_MS = 7L * 24 * 60 * 60 * 1000;
    public static final long MONTH_DURATION_MS = 30L * 24 * 60 * 60 * 1000;

    private Util() { }

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
            var parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            var tmp = File.createTempFile("btrbz-", ".tmp");

            Files.writeString(tmp.toPath(), content);
            tmp.deleteOnExit();

            return Files.move(
                tmp.toPath(),
                path,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            );
        });
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

    public static <T, U> Optional<Pair<T, U>> zipNullables(T first, U second) {
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

        return new DecimalFormat(pattern.toString()).format(scaled) + suffix;
    }

    public static void runCommand(String command) {
        var client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.networkHandler.sendChatCommand(command);
        }
    }

    public static boolean isValidRomanNumeral(String roman) {
        return roman
            .toUpperCase()
            .matches("^M{0,3}(CM|CD|D?C{0,3})?(XC|XL|L?X{0,3})?(IX|IV|V?I{0,3})$");
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
}
