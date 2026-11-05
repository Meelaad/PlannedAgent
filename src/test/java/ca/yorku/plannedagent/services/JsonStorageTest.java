package ca.yorku.plannedagent.services;

import ca.yorku.plannedagent.commands.AddBlockCommand;
import ca.yorku.plannedagent.commands.CommandHistory;
import ca.yorku.plannedagent.commands.MoveBlockCommand;
import ca.yorku.plannedagent.model.FixedEvent;
import ca.yorku.plannedagent.model.Preference;
import ca.yorku.plannedagent.model.Priority;
import ca.yorku.plannedagent.model.Task;
import ca.yorku.plannedagent.model.TimeBlock;
import ca.yorku.plannedagent.model.Workspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JsonStorageTest {

    private static final LocalDateTime FRIDAY_17 = LocalDateTime.of(2026, 10, 30, 17, 0);

    @TempDir
    Path dir;

    @Test
    void missingFileGivesEmptyWorkspace() {
        Workspace workspace = new JsonStorage(dir.resolve("none.json")).load();
        assertTrue(workspace.getTasks().isEmpty());
    }

    @Test
    void roundTripKeepsEverything() {
        Workspace workspace = new Workspace();
        Task report = new Task("EECS report", 180, FRIDAY_17, Priority.HIGH, false);
        Task run = new Task("Run", 45, null, Priority.LOW, true);
        workspace.addTask(report);
        workspace.addTask(run);
        workspace.addEvent(new FixedEvent("Lecture", FRIDAY_17.minusHours(8), FRIDAY_17.minusHours(7)));
        workspace.getPlan().add(new TimeBlock("b9", report.getId(), FRIDAY_17.minusDays(1), FRIDAY_17.minusDays(1).plusHours(3)));
        report.schedule();
        run.complete();
        workspace.addPreference(new Preference(Preference.NO_WORK_AFTER, "22:00", "I never study after 10pm"));

        JsonStorage storage = new JsonStorage(dir.resolve("data/workspace.json"));
        storage.save(workspace);
        Workspace loaded = storage.load();

        Task loadedReport = loaded.getTask("t1");
        assertEquals("EECS report", loadedReport.getTitle());
        assertEquals(FRIDAY_17, loadedReport.getDeadline());
        assertEquals(Priority.HIGH, loadedReport.getPriority());
        assertEquals("SCHEDULED", loadedReport.getStatus());
        assertEquals("DONE", loaded.getTask("t2").getStatus());
        assertTrue(loaded.getTask("t2").isOutdoor());
        assertNull(loaded.getTask("t2").getDeadline());
        assertEquals("Lecture", loaded.getEvents().get(0).getTitle());
        assertEquals(180, loaded.getPlan().find("b9").getMinutes());
        assertEquals("22:00", loaded.getPreferences().get(0).getValue());
    }

    @Test
    void idsContinueAfterLoading() {
        Workspace workspace = new Workspace();
        workspace.addTask(new Task("A", 30, null, Priority.LOW, false));
        JsonStorage storage = new JsonStorage(dir.resolve("workspace.json"));
        storage.save(workspace);

        Workspace loaded = storage.load();
        Task next = new Task("B", 30, null, Priority.LOW, false);
        loaded.addTask(next);
        assertEquals("t2", next.getId());
    }

    @Test
    void loadedStateObjectsStillWork() {
        Workspace workspace = new Workspace();
        workspace.addTask(new Task("A", 30, null, Priority.LOW, false));
        JsonStorage storage = new JsonStorage(dir.resolve("workspace.json"));
        storage.save(workspace);

        Task loaded = storage.load().getTask("t1");
        loaded.schedule();
        loaded.markMissed();
        assertTrue(loaded.needsScheduling());
    }

    @Test
    void damagedFileIsReportedAndKept() throws Exception {
        Path file = dir.resolve("workspace.json");
        Files.writeString(file, "{ \"tasks\": [ { \"title\": \"A\", \"state\": \"FLYING\" } ] }");
        JsonStorage storage = new JsonStorage(file);

        IllegalStateException e = assertThrows(IllegalStateException.class, storage::load);
        assertTrue(e.getMessage().contains("damaged"));
        assertTrue(Files.readString(file).contains("FLYING"));
    }

    @Test
    void notJsonAtAllIsReported() throws Exception {
        Path file = dir.resolve("workspace.json");
        Files.writeString(file, "this is not json");
        assertThrows(IllegalStateException.class, () -> new JsonStorage(file).load());
    }

    @Test
    void taskWithoutStateBecomesTodo() throws Exception {
        Path file = dir.resolve("workspace.json");
        Files.writeString(file, "{ \"tasks\": [ { \"id\": \"t1\", \"title\": \"A\", \"minutes\": 30 } ], \"lastId\": 1 }");
        Task task = new JsonStorage(file).load().getTask("t1");
        assertEquals("TODO", task.getStatus());
        assertEquals(Priority.MEDIUM, task.getPriority());
    }

    @Test
    void historyRoundTripKeepsCommandTypesAndOrder() {
        Workspace workspace = new Workspace();
        Task gym = new Task("Gym", 60, null, Priority.LOW, false);
        workspace.addTask(gym);
        CommandHistory history = new CommandHistory();
        TimeBlock block = new TimeBlock("b2", gym.getId(), FRIDAY_17, FRIDAY_17.plusHours(1));
        history.run(List.of(new AddBlockCommand(block, "Gym")), workspace);
        history.run(List.of(new MoveBlockCommand("b2", FRIDAY_17.plusDays(1), "Gym")), workspace);

        JsonStorage storage = new JsonStorage(dir.resolve("workspace.json"));
        storage.save(workspace);
        storage.saveHistory(history);
        Workspace loadedWorkspace = storage.load();
        CommandHistory loaded = storage.loadHistory();

        assertTrue(loaded.undo(loadedWorkspace));
        assertEquals(FRIDAY_17, loadedWorkspace.getPlan().find("b2").getStart());
        assertTrue(loaded.undo(loadedWorkspace));
        assertTrue(loadedWorkspace.getPlan().getBlocks().isEmpty());
        assertEquals("TODO", loadedWorkspace.getTask("t1").getStatus());
        assertFalse(loaded.undo(loadedWorkspace));
        assertTrue(loaded.redo(loadedWorkspace));
        assertEquals("SCHEDULED", loadedWorkspace.getTask("t1").getStatus());
    }

    @Test
    void damagedHistoryStartsEmpty() throws Exception {
        Files.writeString(dir.resolve("history.json"), "{\"undoStack\": [[{\"type\": \"teleport\"}]]}");
        CommandHistory history = new JsonStorage(dir.resolve("workspace.json")).loadHistory();
        assertFalse(history.canUndo());
    }

    @Test
    void saveLeavesNoTempFiles() throws Exception {
        JsonStorage storage = new JsonStorage(dir.resolve("workspace.json"));
        storage.save(new Workspace());
        storage.save(new Workspace());
        try (var files = Files.list(dir)) {
            assertEquals(1, files.count());
        }
    }
}
