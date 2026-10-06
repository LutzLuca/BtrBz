package com.github.lutzluca.btrbz.core.iteminfo.charts;

import io.vavr.control.Try;

import java.time.Duration;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalQueries;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Local display and clock boundaries, keeping all market timestamps as absolute instants. */
public final class HistoryTime {
    private static final DateTimeFormatter DETAILED = DateTimeFormatter.ofPattern("MMM d, uuuu HH:mm:ss XXX z '('VV')'",
        Locale.ENGLISH);
    private static final DateTimeFormatter EDITOR = new DateTimeFormatterBuilder().appendPattern("uuuu-MM-dd HH:mm")
        .optionalStart().appendLiteral(' ').appendOffsetId().optionalEnd().toFormatter(Locale.ROOT)
        .withResolverStyle(ResolverStyle.STRICT);

    private HistoryTime() {}

    public static ZoneId zone() {
        return ZoneId.systemDefault();
    }

    public static String detailed(Instant time) {
        return detailed(time, zone());
    }

    public static String detailed(Instant time, ZoneId zone) {
        return DETAILED.format(time.atZone(zone));
    }

    public static String editor(Instant time) {
        return editor(time, zone());
    }

    public static String editor(Instant time, ZoneId zone) {
        return EDITOR.format(time.atZone(zone));
    }

    public static Try<Instant> parseEditor(String text) {
        return parseEditor(text, zone());
    }

    public static Try<Instant> parseEditor(String text, ZoneId zone) {
        return Try.of(() -> {
            var parsed = EDITOR.parse(text.strip());
            var local = LocalDateTime.from(parsed);
            var supplied = parsed.query(TemporalQueries.offset());
            var valid = zone.getRules().getValidOffsets(local);
            if (valid.isEmpty()) {
                throw new IllegalArgumentException(
                    "This local time does not exist in " + zone + " because the clock moves forward.");
            }
            if (supplied == null && valid.size() > 1) {
                throw new IllegalArgumentException("This local time occurs twice. Add "
                    + valid.stream().map(ZoneOffset::getId).collect(Collectors.joining(" or ")) + " after the time.");
            }
            if (supplied != null && !valid.contains(supplied)) {
                throw new IllegalArgumentException(
                    "Offset " + supplied + " is not valid for this local time in " + zone + ".");
            }
            return local.toInstant(supplied == null ? valid.getFirst() : supplied);
        });
    }

    public static String axis(Instant time, Duration span) {
        return axis(time, span, zone());
    }

    public static String axis(Instant time, Duration span, ZoneId zone) {
        var local = time.atZone(zone);
        String pattern = span.compareTo(Duration.ofMinutes(2)) < 0
            ? "HH:mm:ss"
            : span.compareTo(Duration.ofDays(2)) < 0
                ? "HH:mm"
                : span.compareTo(Duration.ofDays(90)) < 0 ? "MMM d" : "MMM uuuu";
        String label = DateTimeFormatter.ofPattern(pattern, Locale.ROOT).format(local);
        return zone.getRules().getValidOffsets(local.toLocalDateTime()).size() > 1
            ? label + " " + local.getOffset() : label;
    }

    public static List<Instant> ticks(Instant start, Instant end, int targetCount) {
        return ticks(start, end, targetCount, zone());
    }

    public static List<Instant> ticks(Instant start, Instant end, int targetCount, ZoneId zone) {
        if (!end.isAfter(start)) {
            return List.of();
        }
        var step = step(Duration.between(start, end).toMillis() / 1000.0 / Math.max(1, targetCount - 1));
        var startLocal = start.atZone(zone).toLocalDateTime();
        var endLocal = end.atZone(zone).toLocalDateTime();
        var first = startLocal.isBefore(endLocal) ? startLocal : endLocal;
        var last = startLocal.isAfter(endLocal) ? startLocal : endLocal;
        var rules = zone.getRules();
        var transition = rules.nextTransition(start.minusNanos(1));
        while (transition != null && !transition.getInstant().isAfter(end)) {
            var before = transition.getDateTimeBefore();
            var after = transition.getDateTimeAfter();
            if (before.isBefore(first)) {
                first = before;
            }
            if (after.isBefore(first)) {
                first = after;
            }
            if (before.isAfter(last)) {
                last = before;
            }
            if (after.isAfter(last)) {
                last = after;
            }
            transition = rules.nextTransition(transition.getInstant().plusNanos(1));
        }
        var result = new TreeSet<Instant>();
        var local = floor(first, step);
        while (!local.isAfter(last)) {
            for (var offset : rules.getValidOffsets(local)) {
                var time = local.toInstant(offset);
                if (!time.isBefore(start) && !time.isAfter(end)) {
                    result.add(time);
                }
            }
            local = local.plus(step.amount(), step.unit());
        }
        return List.copyOf(result);
    }

    private static Step step(double seconds) {
        seconds *= .8;
        for (int amount : new int[]{1, 2, 5, 10, 15, 30}) {
            if (amount >= seconds) {
                return new Step(ChronoUnit.SECONDS, amount);
            }
        }
        for (int amount : new int[]{1, 2, 5, 10, 15, 30}) {
            if (amount * 60 >= seconds) {
                return new Step(ChronoUnit.MINUTES, amount);
            }
        }
        for (int amount : new int[]{1, 2, 3, 6, 12}) {
            if (amount * 3600 >= seconds) {
                return new Step(ChronoUnit.HOURS, amount);
            }
        }
        for (int amount : new int[]{1, 2, 7}) {
            if (amount * 86400 >= seconds) {
                return new Step(ChronoUnit.DAYS, amount);
            }
        }
        for (int amount : new int[]{1, 2, 3, 6}) {
            if (amount * 30.0 * 86400 >= seconds) {
                return new Step(ChronoUnit.MONTHS, amount);
            }
        }
        int years = Math.max(1, (int) Math.ceil(seconds / (365.25 * 86400)));
        return new Step(ChronoUnit.YEARS, years);
    }

    private static LocalDateTime floor(LocalDateTime time, Step step) {
        int amount = step.amount();
        return switch (step.unit()) {
            case SECONDS -> time.withNano(0).withSecond(time.getSecond() / amount * amount);
            case MINUTES -> time.withSecond(0).withNano(0).withMinute(time.getMinute() / amount * amount);
            case HOURS -> time.withMinute(0).withSecond(0).withNano(0).withHour(time.getHour() / amount * amount);
            case DAYS -> amount == 7
                ? time.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay()
                : LocalDate.ofEpochDay(Math.floorDiv(time.toLocalDate().toEpochDay(), amount) * amount).atStartOfDay();
            case MONTHS -> {
                long month = Math.floorDiv(time.getYear() * 12L + time.getMonthValue() - 1, amount) * amount;
                yield LocalDate.of((int) Math.floorDiv(month, 12), (int) Math.floorMod(month, 12) + 1, 1)
                    .atStartOfDay();
            }
            case YEARS -> LocalDate.of(Math.floorDiv(time.getYear(), amount) * amount, 1, 1).atStartOfDay();
            default -> throw new IllegalArgumentException("Unsupported clock interval");
        };
    }

    private record Step(ChronoUnit unit, int amount) {}
}
