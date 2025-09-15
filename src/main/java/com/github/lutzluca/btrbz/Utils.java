package com.github.lutzluca.btrbz;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import io.vavr.control.Try;

public final class Utils {
    private Utils() {}


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

            return Files.move(tmp.toPath(), path, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        });
    }
}
