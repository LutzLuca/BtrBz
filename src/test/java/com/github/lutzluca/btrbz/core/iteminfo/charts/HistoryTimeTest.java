package com.github.lutzluca.btrbz.core.iteminfo.charts;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

class HistoryTimeTest {
    @Test
    void localEditorRejectsNonexistentAndAmbiguousTimesAndPreservesExplicitOffsets() {
        var berlin = ZoneId.of("Europe/Berlin");
        Assertions.assertTrue(HistoryTime.parseEditor("2026-03-29 02:30", berlin).isFailure());
        var ambiguous = HistoryTime.parseEditor("2026-10-25 02:30", berlin);
        Assertions.assertTrue(ambiguous.isFailure());
        Assertions.assertTrue(ambiguous.getCause().getMessage().contains("+02:00 or +01:00"));
        Assertions.assertTrue(HistoryTime.parseEditor("2026-10-25 02:30 +03:00", berlin).isFailure());
        var early = HistoryTime.parseEditor("2026-10-25 02:30 +02:00", berlin).get();
        var late = HistoryTime.parseEditor("2026-10-25 02:30 +01:00", berlin).get();
        Assertions.assertEquals(Instant.parse("2026-10-25T00:30:00Z"), early);
        Assertions.assertEquals(Instant.parse("2026-10-25T01:30:00Z"), late);
        Assertions.assertEquals(late, HistoryTime.parseEditor(HistoryTime.editor(late, berlin), berlin).get());
        Assertions.assertTrue(HistoryTime.detailed(early, berlin).contains("+02:00 CEST (Europe/Berlin)"));
        Assertions.assertTrue(HistoryTime.detailed(late, berlin).contains("+01:00 CET (Europe/Berlin)"));
    }

    @Test
    void ticksFollowLocalClockBoundariesAndKeepBothRepeatedHoursInInstantOrder() {
        var berlin = ZoneId.of("Europe/Berlin");
        var start = Instant.parse("2026-10-24T23:00:00Z");
        var end = Instant.parse("2026-10-25T03:00:00Z");
        var ticks = HistoryTime.ticks(start, end, 5, berlin);
        Assertions.assertEquals(List.of(start, Instant.parse("2026-10-25T00:00:00Z"),
            Instant.parse("2026-10-25T01:00:00Z"), Instant.parse("2026-10-25T02:00:00Z"), end), ticks);
        Assertions.assertEquals("02:00 +02:00", HistoryTime.axis(ticks.get(1), Duration.ofHours(4), berlin));
        Assertions.assertEquals("02:00 +01:00", HistoryTime.axis(ticks.get(2), Duration.ofHours(4), berlin));
        var partial = HistoryTime.ticks(Instant.parse("2026-10-25T00:50:00Z"),
            Instant.parse("2026-10-25T01:10:00Z"), 5, berlin);
        Assertions.assertEquals(List.of(Instant.parse("2026-10-25T00:50:00Z"), Instant.parse("2026-10-25T00:55:00Z"),
            Instant.parse("2026-10-25T01:00:00Z"), Instant.parse("2026-10-25T01:05:00Z"),
            Instant.parse("2026-10-25T01:10:00Z")), partial);
        var minutes = HistoryTime.ticks(Instant.parse("2026-10-07T12:03:00Z"),
            Instant.parse("2026-10-07T12:29:00Z"), 6, ZoneOffset.UTC);
        Assertions.assertEquals(List.of(Instant.parse("2026-10-07T12:05:00Z"), Instant.parse("2026-10-07T12:10:00Z"),
            Instant.parse("2026-10-07T12:15:00Z"), Instant.parse("2026-10-07T12:20:00Z"),
            Instant.parse("2026-10-07T12:25:00Z")), minutes);
        var months = HistoryTime.ticks(Instant.parse("2026-01-15T00:00:00Z"),
            Instant.parse("2026-06-15T00:00:00Z"), 6, ZoneOffset.UTC);
        Assertions.assertEquals(List.of(Instant.parse("2026-02-01T00:00:00Z"), Instant.parse("2026-03-01T00:00:00Z"),
            Instant.parse("2026-04-01T00:00:00Z"), Instant.parse("2026-05-01T00:00:00Z"),
            Instant.parse("2026-06-01T00:00:00Z")), months);
    }
}
