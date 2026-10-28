package ca.yorku.plannedagent.scheduling;

import ca.yorku.plannedagent.commands.AddBlockCommand;
import ca.yorku.plannedagent.commands.MoveBlockCommand;
import ca.yorku.plannedagent.model.Conflict;
import ca.yorku.plannedagent.model.FixedEvent;
import ca.yorku.plannedagent.model.Preference;
import ca.yorku.plannedagent.model.Priority;
import ca.yorku.plannedagent.model.Task;
import ca.yorku.plannedagent.model.TimeBlock;
import ca.yorku.plannedagent.model.Workspace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConflictCheckerTest {

    private static final LocalDateTime TUESDAY_10 = LocalDateTime.of(2026, 10, 27, 10, 0);

    private Workspace workspace;
    private ConflictChecker checker;
    private Task gym;

    @BeforeEach
    void setUp() {
        workspace = new Workspace();
        checker = new ConflictChecker();
        gym = new Task("Gym", 60, null, Priority.MEDIUM, false);
        workspace.addTask(gym);
    }

    @Test
    void emptyPlanHasNoConflicts() {
        assertTrue(checker.check(workspace).isEmpty());
    }

    @Test
    void findsBlockOverlappingAnEvent() {
        workspace.addEvent(new FixedEvent("Lecture", TUESDAY_10, TUESDAY_10.plusMinutes(80)));
        workspace.getPlan().add(new TimeBlock("b1", gym.getId(), TUESDAY_10.plusHours(1), TUESDAY_10.plusHours(2)));

        List<Conflict> conflicts = checker.check(workspace);

        assertEquals(1, conflicts.size());
        assertEquals(Conflict.OVERLAP, conflicts.get(0).getType());
        assertEquals(List.of("e2", "b1"), conflicts.get(0).getBlockIds());
        assertTrue(conflicts.get(0).getMessage().contains("'Gym'"));
    }

    @Test
    void touchingBlocksDoNotOverlap() {
        workspace.addEvent(new FixedEvent("Lecture", TUESDAY_10, TUESDAY_10.plusHours(1)));
        workspace.getPlan().add(new TimeBlock("b1", gym.getId(), TUESDAY_10.plusHours(1), TUESDAY_10.plusHours(2)));
        assertTrue(checker.check(workspace).isEmpty());
    }

    @Test
    void overlappingEventsAreReportedToo() {
        workspace.addEvent(new FixedEvent("Shift", TUESDAY_10, TUESDAY_10.plusHours(4)));
        workspace.addEvent(new FixedEvent("Lab", TUESDAY_10.plusHours(1), TUESDAY_10.plusHours(2)));
        workspace.addEvent(new FixedEvent("Call", TUESDAY_10.plusHours(3), TUESDAY_10.plusHours(5)));
        assertEquals(2, checker.check(workspace).size());
    }

    @Test
    void findsBlockAfterDeadline() {
        Task report = new Task("Report", 120, TUESDAY_10.plusHours(1), Priority.HIGH, false);
        workspace.addTask(report);
        workspace.getPlan().add(new TimeBlock("b1", report.getId(), TUESDAY_10, TUESDAY_10.plusHours(2)));

        List<Conflict> conflicts = checker.check(workspace);

        assertEquals(1, conflicts.size());
        assertEquals(Conflict.LATE, conflicts.get(0).getType());
    }

    @Test
    void findsBrokenPreference() {
        workspace.addPreference(new Preference(Preference.NO_WORK_AFTER, "22:00", "no studying after 10pm"));
        workspace.getPlan().add(new TimeBlock("b1", gym.getId(), TUESDAY_10.withHour(22), TUESDAY_10.withHour(23)));

        List<Conflict> conflicts = checker.check(workspace);

        assertEquals(Conflict.PREFERENCE, conflicts.get(0).getType());
        assertTrue(conflicts.get(0).getMessage().endsWith("no work after 22:00"));
    }

    @Test
    void checkChangesOnlyReportsNewConflictsAndLeavesWorkspaceAlone() {
        workspace.addEvent(new FixedEvent("Shift", TUESDAY_10, TUESDAY_10.plusHours(4)));
        workspace.addEvent(new FixedEvent("Lab", TUESDAY_10.plusHours(1), TUESDAY_10.plusHours(2)));
        TimeBlock block = new TimeBlock("b9", gym.getId(), TUESDAY_10.plusDays(1), TUESDAY_10.plusDays(1).plusHours(1));

        assertTrue(checker.checkChanges(workspace, List.of(new AddBlockCommand(block, "Gym"))).isEmpty());

        workspace.getPlan().add(block.copy());
        gym.schedule();
        List<Conflict> added = checker.checkChanges(workspace,
                List.of(new MoveBlockCommand("b9", TUESDAY_10.plusHours(3), "Gym")));
        assertEquals(1, added.size());
        assertEquals(TUESDAY_10.plusDays(1), workspace.getPlan().find("b9").getStart());
    }
}
