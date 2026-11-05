package ca.yorku.plannedagent.services;

import ca.yorku.plannedagent.model.FixedEvent;
import ca.yorku.plannedagent.model.Task;
import ca.yorku.plannedagent.model.TimeBlock;
import ca.yorku.plannedagent.model.Workspace;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Writes the fixed events and planned blocks to an iCalendar (.ics) file that Google Calendar,
 * Outlook and Apple Calendar can import. Times are written in UTC so no time zone table is needed.
 */
public class CalendarExporter {

    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final String CRLF = "\r\n";

    private final Clock clock;

    public CalendarExporter() {
        this(Clock.systemDefaultZone());
    }

    public CalendarExporter(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Returns how many calendar entries were written. */
    public int export(Workspace workspace, Path file) {
        ZoneId zone = clock.getZone();
        String stamp = UTC.format(Instant.now(clock).atOffset(ZoneOffset.UTC));
        StringBuilder ics = new StringBuilder();
        line(ics, "BEGIN:VCALENDAR");
        line(ics, "VERSION:2.0");
        line(ics, "PRODID:-//PlannedAgent//EECS 3311//EN");
        line(ics, "CALSCALE:GREGORIAN");
        int count = 0;
        for (FixedEvent event : workspace.getEvents()) {
            event(ics, event.getId(), event.getTitle(), "Fixed event", event.getStart(), event.getEnd(), stamp, zone);
            count++;
        }
        for (TimeBlock block : workspace.getPlan().getBlocks()) {
            Task task = workspace.getTask(block.getTaskId());
            String title = task == null ? "Planned block" : task.getTitle();
            event(ics, block.getId(), title, "PlannedAgent task", block.getStart(), block.getEnd(), stamp, zone);
            count++;
        }
        line(ics, "END:VCALENDAR");
        try {
            Path dir = file.toAbsolutePath().getParent();
            if (dir != null) {
                Files.createDirectories(dir);
            }
            Files.writeString(file, ics.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + file, e);
        }
        return count;
    }

    private static void event(StringBuilder ics, String id, String title, String category,
                              LocalDateTime start, LocalDateTime end, String stamp, ZoneId zone) {
        line(ics, "BEGIN:VEVENT");
        line(ics, "UID:" + escape(id) + "@plannedagent");
        line(ics, "DTSTAMP:" + stamp);
        line(ics, "DTSTART:" + UTC.format(start.atZone(zone).withZoneSameInstant(ZoneOffset.UTC)));
        line(ics, "DTEND:" + UTC.format(end.atZone(zone).withZoneSameInstant(ZoneOffset.UTC)));
        line(ics, "SUMMARY:" + escape(title));
        line(ics, "CATEGORIES:" + escape(category));
        line(ics, "END:VEVENT");
    }

    static String escape(String text) {
        return text.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n")
                .replace("\r", "\\n");
    }

    /** Lines longer than 75 bytes are folded: the rest continues on the next line after one space. */
    static void line(StringBuilder ics, String content) {
        int bytes = 0;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < content.length(); ) {
            int codePoint = content.codePointAt(i);
            int size = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8).length;
            if (bytes + size > 75) {
                ics.append(current).append(CRLF).append(' ');
                current.setLength(0);
                bytes = 1;
            }
            current.appendCodePoint(codePoint);
            bytes += size;
            i += Character.charCount(codePoint);
        }
        ics.append(current).append(CRLF);
    }
}
