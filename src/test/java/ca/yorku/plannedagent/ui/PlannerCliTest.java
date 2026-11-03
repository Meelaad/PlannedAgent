package ca.yorku.plannedagent.ui;

import ca.yorku.plannedagent.agent.ScriptedLlm;
import ca.yorku.plannedagent.app.PlannerFacade;
import ca.yorku.plannedagent.model.Workspace;
import ca.yorku.plannedagent.services.Forecast;
import ca.yorku.plannedagent.services.JsonStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class PlannerCliTest {

    private static final ZoneId ZONE = ZoneId.of("America/Toronto");
    private static final Clock CLOCK = Clock.fixed(LocalDateTime.of(2026, 11, 3, 9, 0).atZone(ZONE).toInstant(), ZONE);

    @TempDir
    Path dir;

    private PlannerFacade facade;

    @Test
    void addFromTextAsksBeforeSaving() {
        facade = facade(new ScriptedLlm("{\"tasks\": [{\"title\": \"EECS report\", \"minutes\": 180, "
                + "\"deadline\": \"2026-11-06T23:59\", \"priority\": \"HIGH\"}]}"));

        String output = run("y\n", "add", "EECS report Friday about 3 hours");

        assertTrue(output.contains("1. EECS report | 180 min | due Fri Nov 6 23:59 | HIGH"));
        assertTrue(output.contains("Saved."));
        assertEquals(1, facade.getWorkspace().getTasks().size());
    }

    @Test
    void answeringNoSavesNothing() {
        facade = facade(new ScriptedLlm("{\"tasks\": [{\"title\": \"Gym\"}]}"));
        run("n\n", "add", "gym");
        assertTrue(facade.getWorkspace().getTasks().isEmpty());
    }

    @Test
    void aiDownPointsToTheManualCommand() {
        facade = facade(new ScriptedLlm());
        String output = run("", "add", "gym");
        assertTrue(output.contains("The AI is not available"));
        assertTrue(output.contains("new \"<title>\""));
    }

    @Test
    void manualTaskThenScheduleWithYes() {
        facade = facade(new ScriptedLlm());
        run("", "new", "Read", "chapter", "4", "--minutes", "45", "--priority", "low");
        String output = run("", "schedule", "--strategy", "shortest", "--yes");

        assertTrue(output.contains("Add 'Read chapter 4' Tue Nov 3 09:00-09:45"));
        assertTrue(output.contains("Applied 1 change."));
        assertEquals("SCHEDULED", facade.getWorkspace().getTask("t1").getStatus());
    }

    @Test
    void scheduleWithoutAnswerIsDiscarded() {
        facade = facade(new ScriptedLlm());
        run("", "new", "Read", "--minutes", "45");
        String output = run("", "schedule");
        assertTrue(output.contains("Discarded."));
        assertNull(facade.getPending());
    }

    @Test
    void eventsAndWeek() {
        facade = facade(new ScriptedLlm());
        run("", "event", "add", "EECS 3311 lecture", "2026-11-04T10:00", "2026-11-04T11:30");
        String week = run("", "week");
        assertTrue(week.contains("Wed Nov 4"));
        assertTrue(week.contains("10:00-11:30  EECS 3311 lecture (event)"));
        assertTrue(run("", "event", "add", "Bad", "tomorrow", "later").contains("Error: start must look like"));
    }

    @Test
    void askShowsToolUseAndTheProposal() {
        facade = facade(new ScriptedLlm(
                "{\"tool\": \"schedule_tasks\", \"args\": {}}",
                "{\"answer\": \"Your week is ready.\"}"));
        run("", "new", "Gym", "--minutes", "60");

        String output = run("y\n", "ask", "plan", "my", "week");

        assertTrue(output.contains("... using schedule_tasks"));
        assertTrue(output.contains("Your week is ready."));
        assertTrue(output.contains("Applied 1 change."));
    }

    @Test
    void doneTwiceShowsTheStateError() {
        facade = facade(new ScriptedLlm());
        run("", "new", "Quiz", "--minutes", "30");
        run("", "done", "t1");
        assertTrue(run("", "done", "t1").contains("Error: 'Quiz' is already done"));
    }

    @Test
    void conflictsAndUndo() {
        facade = facade(new ScriptedLlm());
        assertTrue(run("", "conflicts").contains("No conflicts found."));
        assertTrue(run("", "undo").contains("Nothing to undo."));
    }

    @Test
    void unknownCommandShowsHelp() {
        facade = facade(new ScriptedLlm());
        String output = run("", "fly");
        assertTrue(output.contains("Unknown command 'fly'"));
        assertTrue(output.contains("Usage: plannedagent"));
    }

    @Test
    void optionParsing() {
        PlannerCli.Args args = PlannerCli.Args.parse(new String[]{"Gym", "--minutes", "60", "--outdoor", "now", "--yes"});
        assertEquals("Gym now", args.text("x"));
        assertEquals("60", args.option("minutes", null));
        assertTrue(args.flag("outdoor"));
        assertTrue(args.flag("yes"));
    }

    private String run(String input, String... args) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new PlannerCli(facade, new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(bytes, true, StandardCharsets.UTF_8)).run(args);
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private PlannerFacade facade(ScriptedLlm llm) {
        return new PlannerFacade(new Workspace(), llm, new JsonStorage(dir.resolve("workspace.json")),
                (from, to) -> Forecast.none(), CLOCK);
    }
}
