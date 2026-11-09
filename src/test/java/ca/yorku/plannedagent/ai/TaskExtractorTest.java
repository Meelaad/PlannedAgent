package ca.yorku.plannedagent.ai;

import ca.yorku.plannedagent.model.Priority;
import ca.yorku.plannedagent.model.Task;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TaskExtractorTest {

    private static final LocalDate THURSDAY = LocalDate.of(2026, 10, 29);

    @Test
    void readsTasksFromTheReply() {
        TaskExtractor extractor = new TaskExtractor(request -> """
                {"tasks": [
                  {"title": "EECS report", "minutes": 180, "deadline": "2026-10-30T23:59", "priority": "HIGH", "outdoor": false},
                  {"title": "Run", "minutes": 40, "deadline": null, "priority": "low", "outdoor": true}
                ]}""");

        List<Task> tasks = extractor.extract("EECS report Friday about 3 hours, and a run", THURSDAY);

        assertEquals(2, tasks.size());
        Task report = tasks.get(0);
        assertEquals("EECS report", report.getTitle());
        assertEquals(180, report.getMinutes());
        assertEquals(LocalDateTime.of(2026, 10, 30, 23, 59), report.getDeadline());
        assertEquals(Priority.HIGH, report.getPriority());
        assertTrue(tasks.get(1).isOutdoor());
        assertEquals(Priority.LOW, tasks.get(1).getPriority());
        assertNull(tasks.get(1).getDeadline());
        assertEquals("TODO", report.getStatus());
        assertNull(report.getId());
    }

    @Test
    void promptGivesTodayAndTheNextDays() {
        AtomicReference<LlmRequest> sent = new AtomicReference<>();
        TaskExtractor extractor = new TaskExtractor(request -> {
            sent.set(request);
            return "{\"tasks\": []}";
        });

        extractor.extract("groceries", THURSDAY);

        assertTrue(sent.get().system.contains("Thursday 2026-10-29"));
        assertTrue(sent.get().system.contains("Friday 2026-10-30"));
        assertTrue(sent.get().system.contains("never copy another task's deadline"));
        assertTrue(sent.get().wantJson);
        assertEquals(List.of("user: groceries"), sent.get().messages);
    }

    @Test
    void acceptsLooseValues() {
        TaskExtractor extractor = new TaskExtractor(request -> """
                [{"title": "Groceries", "minutes": "45", "deadline": "2026-10-31"},
                 {"title": "Laundry", "minutes": 30.4, "deadline": "2026-10-31T10:00:00", "priority": "urgent"},
                 {"title": "Call mom", "deadline": "sometime"}]""");

        List<Task> tasks = extractor.extract("groceries saturday, laundry, call mom", THURSDAY);

        assertEquals(45, tasks.get(0).getMinutes());
        assertEquals(LocalDateTime.of(2026, 10, 31, 23, 59), tasks.get(0).getDeadline());
        assertEquals(30, tasks.get(1).getMinutes());
        assertEquals(LocalDateTime.of(2026, 10, 31, 10, 0), tasks.get(1).getDeadline());
        assertEquals(Priority.MEDIUM, tasks.get(1).getPriority());
        assertEquals(60, tasks.get(2).getMinutes());
        assertNull(tasks.get(2).getDeadline());
    }

    @Test
    void skipsEntriesWithoutTitle() {
        TaskExtractor extractor = new TaskExtractor(request ->
                "{\"tasks\": [{\"minutes\": 30}, {\"title\": \" \"}, \"text\", {\"title\": \"Read\"}]}");
        assertEquals(1, extractor.extract("read", THURSDAY).size());
    }

    @Test
    void emptyTextNeverCallsTheLlm() {
        TaskExtractor extractor = new TaskExtractor(request -> fail("LLM should not be called"));
        assertThrows(IllegalArgumentException.class, () -> extractor.extract("   ", THURSDAY));
        assertThrows(IllegalArgumentException.class, () -> extractor.extract("x".repeat(2001), THURSDAY));
    }

    @Test
    void invalidJsonIsAnLlmException() {
        TaskExtractor extractor = new TaskExtractor(request -> "Sure! Here are your tasks: report, gym");
        assertThrows(LlmException.class, () -> extractor.extract("report, gym", THURSDAY));
    }

    @Test
    void wrongShapeIsAnLlmException() {
        TaskExtractor extractor = new TaskExtractor(request -> "{\"answer\": \"report\"}");
        assertThrows(LlmException.class, () -> extractor.extract("report", THURSDAY));
    }

    @Test
    void llmFailurePassesThrough() {
        TaskExtractor extractor = new TaskExtractor(request -> {
            throw new LlmException("quota");
        });
        assertThrows(LlmException.class, () -> extractor.extract("report", THURSDAY));
    }

    @Test
    void problemsAreFoundInJava() {
        LocalDateTime now = THURSDAY.atTime(12, 0);
        Task fine = new Task("Report", 120, now.plusDays(1), Priority.HIGH, false);
        Task bad = new Task(" ", 900, now.minusDays(1), Priority.HIGH, false);

        assertTrue(fine.problems(now).isEmpty());
        assertEquals(3, bad.problems(now).size());
        assertEquals(1, new Task("Nap", 2, null, null, false).problems(now).size());
    }
}
