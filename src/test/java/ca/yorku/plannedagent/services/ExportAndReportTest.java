package ca.yorku.plannedagent.services;

import ca.yorku.plannedagent.ai.LlmException;
import ca.yorku.plannedagent.ai.LlmRequest;
import ca.yorku.plannedagent.model.FixedEvent;
import ca.yorku.plannedagent.model.Priority;
import ca.yorku.plannedagent.model.Task;
import ca.yorku.plannedagent.model.TimeBlock;
import ca.yorku.plannedagent.model.Workspace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExportAndReportTest {

    private static final ZoneId ZONE = ZoneId.of("America/Toronto");
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);
    private static final Clock CLOCK = Clock.fixed(MONDAY.atTime(12, 0).atZone(ZONE).toInstant(), ZONE);

    @TempDir
    Path dir;

    private Workspace workspace;

    @BeforeEach
    void setUp() {
        workspace = new Workspace();
        Task report = new Task("EECS report, draft; v2", 120, null, Priority.HIGH, false);
        Task gym = new Task("Gym", 60, null, Priority.LOW, false);
        workspace.addTask(report);
        workspace.addTask(gym);
        workspace.addEvent(new FixedEvent("EECS 3311 lecture", MONDAY.atTime(10, 0), MONDAY.atTime(11, 30)));
        workspace.getPlan().add(new TimeBlock("b4", report.getId(), MONDAY.plusDays(2).atTime(14, 0),
                MONDAY.plusDays(2).atTime(16, 0)));
        workspace.getPlan().add(new TimeBlock("b5", gym.getId(), MONDAY.plusDays(1).atTime(18, 0),
                MONDAY.plusDays(1).atTime(19, 0)));
    }

    @Test
    void exportWritesAValidCalendar() throws Exception {
        Path file = dir.resolve("week.ics");
        int count = new CalendarExporter(CLOCK).export(workspace, file);

        String ics = Files.readString(file, StandardCharsets.UTF_8);
        assertEquals(3, count);
        assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\nVERSION:2.0\r\n"));
        assertTrue(ics.endsWith("END:VCALENDAR\r\n"));
        assertEquals(3, ics.split("BEGIN:VEVENT", -1).length - 1);
        assertTrue(ics.contains("UID:e3@plannedagent\r\n"));
        assertTrue(ics.contains("DTSTART:20261102T150000Z\r\n"), "10:00 Toronto (EST) is 15:00 UTC");
        assertTrue(ics.contains("SUMMARY:EECS report\\, draft\\; v2\r\n"));
        assertTrue(ics.contains("DTSTAMP:20261102T170000Z"));
        assertFalse(ics.replace("\r\n", "").contains("\n"), "only CRLF line endings");
    }

    @Test
    void longLinesAreFolded() {
        StringBuilder ics = new StringBuilder();
        CalendarExporter.line(ics, "SUMMARY:" + "é".repeat(60));
        for (String line : ics.toString().split("\r\n")) {
            assertTrue(line.getBytes(StandardCharsets.UTF_8).length <= 75, line);
        }
        assertTrue(ics.toString().contains("\r\n "));
    }

    @Test
    void escapeHandlesNewlinesAndBackslashes() {
        assertEquals("a\\\\b\\nc", CalendarExporter.escape("a\\b\nc"));
    }

    @Test
    void reportCountsInJavaAndLetsTheLlmWrite() {
        Task report = workspace.getTask("t1");
        report.complete();
        report.setDoneAt(MONDAY.plusDays(2).atTime(16, 5));
        Task gym = workspace.getTask("t2");
        gym.setMissedAt(MONDAY.plusDays(1).atTime(21, 0));
        List<LlmRequest> sent = new ArrayList<>();

        WeeklyReport weekly = new ReportService(request -> {
            sent.add(request);
            return "  Good week overall.  ";
        }).weeklyReport(workspace, MONDAY);

        assertEquals(180, weekly.stats.get(WeeklyReport.PLANNED_MINUTES));
        assertEquals(120, weekly.stats.get(WeeklyReport.DONE_MINUTES));
        assertEquals(1, weekly.stats.get(WeeklyReport.TASKS_DONE));
        assertEquals(1, weekly.stats.get(WeeklyReport.TASKS_MISSED));
        assertEquals(3, weekly.stats.get(WeeklyReport.BUSIEST_DAY));
        assertEquals("Good week overall.", weekly.summary);
        assertTrue(weekly.writtenByAi);
        assertTrue(sent.get(0).messages.get(0).contains("Minutes planned: 180"));
        assertTrue(sent.get(0).messages.get(0).contains("Busiest day: Wednesday"));
        assertFalse(sent.get(0).wantJson);
    }

    @Test
    void reportFallsBackToTheTemplate() {
        WeeklyReport weekly = new ReportService(request -> {
            throw new LlmException("quota");
        }).weeklyReport(workspace, MONDAY);

        assertFalse(weekly.writtenByAi);
        assertEquals("You planned 3 h and finished 0 tasks (0 h). Your busiest day was Wednesday. "
                + "Nothing was missed. Nice work!", weekly.summary);
    }

    @Test
    void emptyWeekNeedsNoAi() {
        WeeklyReport weekly = new ReportService(request -> fail("no LLM call expected"))
                .weeklyReport(workspace, MONDAY.plusWeeks(3));
        assertEquals("No tasks were planned this week.", weekly.summary);
    }

    @Test
    void otherWeeksAreNotCounted() {
        workspace.getTask("t1").setDoneAt(LocalDateTime.of(2026, 10, 30, 12, 0));
        WeeklyReport weekly = new ReportService(request -> "ok").weeklyReport(workspace, MONDAY);
        assertEquals(0, weekly.stats.get(WeeklyReport.TASKS_DONE));
    }
}
