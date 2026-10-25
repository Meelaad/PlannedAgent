package ca.yorku.plannedagent.commands;

import ca.yorku.plannedagent.model.Priority;
import ca.yorku.plannedagent.model.Task;
import ca.yorku.plannedagent.model.TimeBlock;
import ca.yorku.plannedagent.model.Workspace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommandHistoryTest {

    private static final LocalDateTime WEDNESDAY_7 = LocalDateTime.of(2026, 10, 28, 7, 0);

    private Workspace workspace;
    private CommandHistory history;
    private Task gym;
    private Task report;

    @BeforeEach
    void setUp() {
        workspace = new Workspace();
        history = new CommandHistory();
        gym = new Task("Gym", 60, null, Priority.MEDIUM, false);
        report = new Task("Report", 120, null, Priority.HIGH, false);
        workspace.addTask(gym);
        workspace.addTask(report);
    }

    @Test
    void addBlockSchedulesTheTask() {
        history.run(List.of(add("b1", gym, WEDNESDAY_7)), workspace);
        assertEquals("SCHEDULED", gym.getStatus());
        assertNotNull(workspace.getPlan().find("b1"));
    }

    @Test
    void undoAndRedoAGroup() {
        history.run(List.of(add("b1", gym, WEDNESDAY_7), add("b2", report, WEDNESDAY_7.plusHours(2))), workspace);

        assertTrue(history.undo(workspace));
        assertTrue(workspace.getPlan().getBlocks().isEmpty());
        assertEquals("TODO", gym.getStatus());
        assertEquals("TODO", report.getStatus());

        assertTrue(history.redo(workspace));
        assertEquals(2, workspace.getPlan().getBlocks().size());
        assertEquals("SCHEDULED", report.getStatus());
    }

    @Test
    void nothingToUndoOrRedo() {
        assertFalse(history.undo(workspace));
        assertFalse(history.redo(workspace));
    }

    @Test
    void newChangeClearsRedo() {
        history.run(List.of(add("b1", gym, WEDNESDAY_7)), workspace);
        history.undo(workspace);
        history.run(List.of(add("b2", report, WEDNESDAY_7)), workspace);
        assertFalse(history.canRedo());
    }

    @Test
    void moveAndUndoMove() {
        history.run(List.of(add("b1", gym, WEDNESDAY_7)), workspace);
        history.run(List.of(new MoveBlockCommand("b1", WEDNESDAY_7.plusDays(1), "Gym")), workspace);
        assertEquals(WEDNESDAY_7.plusDays(1), workspace.getPlan().find("b1").getStart());
        assertEquals(WEDNESDAY_7.plusDays(1).plusHours(1), workspace.getPlan().find("b1").getEnd());

        history.undo(workspace);
        assertEquals(WEDNESDAY_7, workspace.getPlan().find("b1").getStart());
    }

    @Test
    void undoRestoresMissedState() {
        gym.schedule();
        gym.markMissed();
        history.run(List.of(add("b1", gym, WEDNESDAY_7)), workspace);
        assertEquals("SCHEDULED", gym.getStatus());
        history.undo(workspace);
        assertEquals("MISSED", gym.getStatus());
    }

    @Test
    void failingGroupIsRolledBack() {
        report.complete();
        List<PlanCommand> group = List.of(add("b1", gym, WEDNESDAY_7), add("b2", report, WEDNESDAY_7.plusHours(2)));

        assertThrows(IllegalStateException.class, () -> history.run(group, workspace));

        assertTrue(workspace.getPlan().getBlocks().isEmpty());
        assertEquals("TODO", gym.getStatus());
        assertFalse(history.canUndo());
    }

    @Test
    void undoIsRefusedWhenTheTaskChangedSince() {
        history.run(List.of(add("b1", gym, WEDNESDAY_7)), workspace);
        gym.complete();

        assertThrows(IllegalStateException.class, () -> history.undo(workspace));
        assertEquals("DONE", gym.getStatus());
        assertNotNull(workspace.getPlan().find("b1"));
        assertTrue(history.canUndo());
    }

    @Test
    void movingAMissingBlockFails() {
        PlanCommand move = new MoveBlockCommand("b9", WEDNESDAY_7, "Ghost");
        assertThrows(IllegalStateException.class, () -> history.run(List.of(move), workspace));
    }

    @Test
    void historyKeepsAtMostTwentySteps() {
        history.run(List.of(add("b0", gym, WEDNESDAY_7)), workspace);
        for (int i = 1; i <= CommandHistory.LIMIT + 5; i++) {
            history.run(List.of(new MoveBlockCommand("b0", WEDNESDAY_7.plusDays(i), "Gym")), workspace);
        }
        int undone = 0;
        while (history.undo(workspace)) {
            undone++;
        }
        assertEquals(CommandHistory.LIMIT, undone);
    }

    @Test
    void describeIsReadable() {
        assertEquals("Add 'Gym' Wed Oct 28 07:00-08:00", add("b1", gym, WEDNESDAY_7).describe());
        assertEquals("Move 'Gym' to Thu Oct 29 07:00",
                new MoveBlockCommand("b1", WEDNESDAY_7.plusDays(1), "Gym").describe());
    }

    private AddBlockCommand add(String blockId, Task task, LocalDateTime start) {
        TimeBlock block = new TimeBlock(blockId, task.getId(), start, start.plusMinutes(task.getMinutes()));
        return new AddBlockCommand(block, task.getTitle());
    }
}
