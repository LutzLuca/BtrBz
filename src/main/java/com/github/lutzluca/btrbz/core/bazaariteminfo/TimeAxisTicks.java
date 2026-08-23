package com.github.lutzluca.btrbz.core.bazaariteminfo;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Produces bounded, range-specific labels for the shared time axis. */
public final class TimeAxisTicks {
    public static final int TARGET_PIXELS_PER_LABEL = 70;
    public static final int MINIMUM_LABELS = 3;
    public static final int MAXIMUM_LABELS = 8;
    public static final DateTimeFormatter TOOLTIP_FORMAT = DateTimeFormatter.ofPattern("d MMM uuuu, HH:mm",
        Locale.ENGLISH);

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    private TimeAxisTicks() {}

    public static List<Tick> generate(
        TimeProjection projection,
        BazaarItemInfoRange range,
        ZoneId zone
    ) {
        Objects.requireNonNull(projection, "projection");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(zone, "zone");
        int count = targetCount(projection.width());
        if (count == 0) {
            return List.of();
        }
        if (!projection.hasRange()) {
            Instant instant = Instant.ofEpochMilli(projection.minimumEpochMillis());
            return List.of(new Tick(projection.x(instant), label(instant, range, zone, null), instant));
        }
        var result = new ArrayList<Tick>();
        LocalDate previousDate = null;
        long span = projection.maximumEpochMillis() - projection.minimumEpochMillis();
        for (int index = 0; index < count; index++) {
            double fraction = count == 1 ? 0 : (double) index / (count - 1);
            Instant instant = Instant.ofEpochMilli(
                projection.minimumEpochMillis() + Math.round(span * fraction));
            LocalDate date = instant.atZone(zone).toLocalDate();
            result.add(new Tick(projection.x(instant), label(instant, range, zone, previousDate), instant));
            previousDate = date;
        }
        return List.copyOf(result);
    }

    public static int targetCount(int width) {
        if (width <= 0) {
            return 0;
        }
        if (width < TARGET_PIXELS_PER_LABEL * 2) {
            return Math.max(1, width / TARGET_PIXELS_PER_LABEL + 1);
        }
        return Math.max(MINIMUM_LABELS,
            Math.min(MAXIMUM_LABELS, Math.round((float) width / TARGET_PIXELS_PER_LABEL) + 1));
    }

    public static String tooltip(Instant instant, ZoneId zone) {
        return TOOLTIP_FORMAT.withZone(zone).format(instant);
    }

    private static String label(
        Instant instant,
        BazaarItemInfoRange range,
        ZoneId zone,
        LocalDate previousDate
    ) {
        var local = instant.atZone(zone);
        return switch (range) {
            case Hour -> TIME.format(local);
            case Day -> previousDate != null && !previousDate.equals(local.toLocalDate())
                ? TIME.format(local) + " " + DATE.format(local)
                : TIME.format(local);
            case Week -> DATE.format(local);
        };
    }

    public record Tick(int x, String label, Instant instant) {
        public Tick {
            label = Objects.requireNonNull(label, "label");
            instant = Objects.requireNonNull(instant, "instant");
        }
    }
}
