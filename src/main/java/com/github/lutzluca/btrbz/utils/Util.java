package com.github.lutzluca.btrbz.utils;

import io.vavr.control.Try;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

public final class Util {

    public static final Set<Item> orderScreenNonOrderItem = Set.of(
        Items.BLACK_STAINED_GLASS_PANE,
        Items.ARROW,
        Items.HOPPER
    );

    private Util() { }


    public static String formatUtcTimestampMillis(long utcMillis) {
        Instant instant = Instant.ofEpochMilli(utcMillis);
        ZonedDateTime zonedDateTime = instant.atZone(ZoneId.systemDefault());
        LocalDateTime localDateTime = zonedDateTime.toLocalDateTime();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        return localDateTime.format(formatter);
    }

    public static Try<Path> atomicDumpToFile(String filename, String content) {
        return Try.of(() -> {
            var path = Path.of(filename);
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
}
