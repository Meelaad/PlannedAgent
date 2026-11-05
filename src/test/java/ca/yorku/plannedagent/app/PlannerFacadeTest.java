package ca.yorku.plannedagent.app;

import ca.yorku.plannedagent.agent.AgentReply;
import ca.yorku.plannedagent.agent.ScriptedLlm;
import ca.yorku.plannedagent.commands.Proposal;
import ca.yorku.plannedagent.model.Conflict;
import ca.yorku.plannedagent.model.FixedEvent;
import ca.yorku.plannedagent.model.Priority;
import ca.yorku.plannedagent.model.Task;
import ca.yorku.plannedagent.model.Workspace;
import ca.yorku.plannedagent.services.Forecast;
import ca.yorku.plannedagent.services.JsonStorage;
import ca.yorku.plannedagent.services.WeatherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlannerFacadeTest {

    static final ZoneId ZONE = ZoneId.of("America/Toronto");
    static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 9, 0);
    static final Clock CLOCK = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
    static final WeatherService DRY = (from, to) -> Forecast.none();

    @TempDir
    Path dir;

    private JsonStorage storage;
    private List<String> heard;

    @BeforeEach
    void setUp() {
        storage = new JsonStorage(dir.resolve("workspace.json"));
        heard = new ArrayList<>();
    }

    @Test
    void addTaskSavesAndNotifies() {
        PlannerFacade facade = facade(new ScriptedLlm(), DRY);
        facade.addTask(new Task("Report", 120, NOW.plusDays(3), Priority.HIGH, false));

        assertEquals(1, storage.load().getTasks().size());
        assertEquals(List.of("TASKS Added task 'Report'"), heard);
    }

    @Test
    void invalidTasksAreNotSaved() {
        PlannerFacade facade = facade(new ScriptedLlm(), DRY);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> facade.saveTasks(List.of(new Task("Ok", 30, null, null, false),
                        new Task("Old", 30, NOW.minusDays(1), null, false))));
        assertTrue(e.getMessage().contains("'Old'"));
        assertTrue(facade.getWorkspace().getTasks().isEmpty());
    }

    @Test
    void tasksFromTextThenSave() {
        ScriptedLlm llm = new ScriptedLlm("{\"tasks\": [{\"title\": \"Groceries\", \"minutes\": 45}]}");
        PlannerFacade facade = facade(llm, DRY);

        List<Task> tasks = facade.tasksFromText("groceries");
        assertTrue(facade.getWorkspace().getTasks().isEmpty());
        facade.saveTasks(tasks);

        assertEquals("Groceries", facade.getWorkspace().getTask("t1").getTitle());
        assertTrue(llm.requests.get(0).system.contains("Monday 2026-11-02"));
    }

    @Test
    void completeTwiceIsRefusedByTheState() {
        PlannerFacade facade = facade(new ScriptedLlm(), DRY);
        facade.addTask(new Task("Quiz", 30, null, null, false));
        facade.completeTask("t1");
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> facade.completeTask("t1"));
        assertEquals("'Quiz' is already done", e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> facade.completeTask("t9"));
    }

    @Test
    void autoScheduleThenApplyThenUndo() {
        PlannerFacade facade = facade(new ScriptedLlm(), DRY);
        facade.addEvent(new FixedEvent("Lecture", NOW, NOW.plusMinutes(90)));
        facade.addTask(new Task("Report", 120, NOW.plusDays(3), Priority.HIGH, false));

        Proposal proposal = facade.autoSchedule("deadline");
        assertTrue(proposal.getSummary().startsWith("Auto-schedule (deadline first): 1 new block."));
        assertTrue(facade.getWorkspace().getPlan().getBlocks().isEmpty());

        assertEquals("Applied 1 change", facade.applyProposal());
        assertEquals(NOW.plusMinutes(90), facade.getWorkspace().getPlan().getBlocks().get(0).getStart());
        assertEquals("SCHEDULED", facade.getWorkspace().getTask("t2").getStatus());
        assertEquals(1, storage.load().getPlan().getBlocks().size());

        assertTrue(facade.undo());
        assertTrue(facade.getWorkspace().getPlan().getBlocks().isEmpty());
        assertEquals("TODO", storage.load().getTask("t2").getStatus());
        assertTrue(facade.redo());
        assertFalse(facade.redo());
    }

    @Test
    void undoWorksInTheNextRun() {
        PlannerFacade first = facade(new ScriptedLlm(), DRY);
        first.addTask(new Task("Report", 60, null, null, false));
        first.autoSchedule("deadline");
        first.applyProposal();

        PlannerFacade second = new PlannerFacade(storage.load(), new ScriptedLlm(), storage, DRY, CLOCK);
        assertTrue(second.undo());
        assertEquals("TODO", second.getWorkspace().getTask("t1").getStatus());
        assertTrue(storage.load().getPlan().getBlocks().isEmpty());

        PlannerFacade third = new PlannerFacade(storage.load(), new ScriptedLlm(), storage, DRY, CLOCK);
        assertTrue(third.redo());
        assertEquals(1, storage.load().getPlan().getBlocks().size());
    }

    @Test
    void unknownStrategyFallsBackWithAWarning() {
        PlannerFacade facade = facade(new ScriptedLlm(), DRY);
        facade.addTask(new Task("Report", 60, null, null, false));
        Proposal proposal = facade.autoSchedule("random");
        assertTrue(proposal.getSummary().contains("deadline first"));
        assertTrue(proposal.getSummary().contains("unknown strategy 'random'"));
    }

    @Test
    void schedulingWorksWithoutWeather() {
        PlannerFacade facade = facade(new ScriptedLlm(), (from, to) -> {
            throw new IOException("offline");
        });
        facade.addTask(new Task("Run", 45, null, null, true));
        Proposal proposal = facade.autoSchedule("deadline");
        assertEquals(1, proposal.getCommands().size());
        assertTrue(proposal.getSummary().contains("weather service is not available"));

        facade.checkConflicts();
        assertTrue(heard.stream().anyMatch(h -> h.startsWith("WARNING")));
    }

    @Test
    void nothingToApplyOrDiscard() {
        PlannerFacade facade = facade(new ScriptedLlm(), DRY);
        assertThrows(IllegalStateException.class, facade::applyProposal);
        facade.discardProposal();
        assertFalse(facade.undo());
    }

    @Test
    void conflictingProposalCannotBeApplied() {
        ScriptedLlm llm = new ScriptedLlm(
                "{\"tool\": \"move_task\", \"args\": {\"blockId\": \"b3\", \"day\": \"2026-11-02\", \"time\": \"10:00\"}}",
                "{\"answer\": \"That clashes with your lecture.\"}");
        PlannerFacade facade = facade(llm, DRY);
        facade.addEvent(new FixedEvent("Lecture", NOW.plusHours(1), NOW.plusHours(2)));
        facade.addTask(new Task("Gym", 60, null, null, false));
        facade.autoSchedule("deadline");
        facade.applyProposal();
        String blockId = facade.getWorkspace().getPlan().getBlocks().get(0).getId();
        assertEquals("b3", blockId);

        AgentReply reply = facade.chat("move gym to 10");

        assertTrue(reply.proposal.hasConflicts());
        assertThrows(IllegalStateException.class, facade::applyProposal);
    }

    @Test
    void chatSavesNewPreferences() {
        ScriptedLlm llm = new ScriptedLlm(
                "{\"tool\": \"remember_preference\", \"args\": {\"rule\": \"NO_WORK_AFTER\", \"value\": \"22:00\"}}",
                "{\"answer\": \"Saved.\"}");
        PlannerFacade facade = facade(llm, DRY);

        facade.chat("I never study after 10pm");

        assertEquals("22:00", storage.load().getPreferences().get(0).getValue());
        assertTrue(heard.contains("PREFERENCES Preferences: no work after 22:00"));
    }

    @Test
    void chatWithoutAiGivesAHelpfulReply() {
        PlannerFacade facade = facade(new ScriptedLlm(), DRY);
        AgentReply reply = facade.chat("plan my week");
        assertTrue(reply.text.startsWith("The AI is not available right now"));
        assertNull(reply.proposal);
    }

    @Test
    void staleProposalIsRejectedSafely() {
        PlannerFacade facade = facade(new ScriptedLlm(), DRY);
        facade.addTask(new Task("Report", 60, null, null, false));
        facade.autoSchedule("deadline");
        facade.completeTask("t1");

        IllegalStateException e = assertThrows(IllegalStateException.class, facade::applyProposal);
        assertTrue(e.getMessage().startsWith("The plan changed"));
        assertTrue(facade.getWorkspace().getPlan().getBlocks().isEmpty());
        assertNull(facade.getPending());
    }

    @Test
    void newLengthFreesTheSlot() {
        PlannerFacade facade = facade(new ScriptedLlm(), DRY);
        facade.addTask(new Task("Report", 60, null, null, false));
        facade.autoSchedule("deadline");
        facade.applyProposal();

        Task edited = facade.getWorkspace().getTask("t1").copy();
        edited.setMinutes(90);
        facade.updateTask(edited);

        assertEquals("TODO", facade.getWorkspace().getTask("t1").getStatus());
        assertTrue(facade.getWorkspace().getPlan().getBlocks().isEmpty());
    }

    @Test
    void deleteRemovesTaskAndBlocks() {
        PlannerFacade facade = facade(new ScriptedLlm(), DRY);
        facade.addTask(new Task("Report", 60, null, null, false));
        facade.autoSchedule("deadline");
        facade.applyProposal();
        facade.deleteTask("t1");
        Workspace saved = storage.load();
        assertTrue(saved.getTasks().isEmpty());
        assertTrue(saved.getPlan().getBlocks().isEmpty());
    }

    @Test
    void conflictCheckFindsOverlappingEvents() {
        PlannerFacade facade = facade(new ScriptedLlm(), DRY);
        facade.addEvent(new FixedEvent("Shift", NOW, NOW.plusHours(4)));
        facade.addEvent(new FixedEvent("Lab", NOW.plusHours(1), NOW.plusHours(2)));
        List<Conflict> conflicts = facade.checkConflicts();
        assertEquals(Conflict.OVERLAP, conflicts.get(0).getType());
    }

    @Test
    void activityLogWritesOneCleanLine() throws Exception {
        Path log = dir.resolve("logs/activity.log");
        ActivityLog activity = new ActivityLog(log, CLOCK);
        activity.update(EventBus.AGENT, "using get_tasks\nfake line");
        activity.update(EventBus.PLAN, "Applied 2 changes");

        List<String> lines = Files.readAllLines(log);
        assertEquals(List.of("2026-11-02T09:00 AGENT using get_tasks / fake line", "2026-11-02T09:00 PLAN Applied 2 changes"),
                lines);
    }

    private PlannerFacade facade(ScriptedLlm llm, WeatherService weather) {
        PlannerFacade facade = new PlannerFacade(new Workspace(), llm, storage, weather, CLOCK);
        facade.subscribe((type, message) -> heard.add(type + " " + message));
        return facade;
    }
}
