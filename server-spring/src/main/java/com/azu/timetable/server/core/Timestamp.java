package com.azu.timetable.server.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

public final class Timestamp {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private Timestamp() {
    }

    public static String fromFile(Path file) throws IOException {
        long micros = Files.getLastModifiedTime(file).to(TimeUnit.MICROSECONDS);
        return format(micros);
    }

    public static String now() {
        Instant instant = Instant.now();
        return format(Math.addExact(
                Math.multiplyExact(instant.getEpochSecond(), 1_000_000L),
                instant.getNano() / 1_000L));
    }

    private static String format(long micros) {
        long seconds = Math.floorDiv(micros, 1_000_000L);
        int fraction = (int) (micros - Math.multiplyExact(seconds, 1_000_000L));
        ZonedDateTime time = Instant.ofEpochSecond(seconds).atZone(ZoneOffset.UTC);
        return FORMAT.format(time) + String.format(".%06d", fraction) + "+00:00";
    }
}