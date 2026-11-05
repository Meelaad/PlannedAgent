package ca.yorku.plannedagent.ui;

import ca.yorku.plannedagent.agent.AgentReply;
import ca.yorku.plannedagent.ai.LlmException;
import ca.yorku.plannedagent.app.EventBus;
import ca.yorku.plannedagent.app.PlannerFacade;
import ca.yorku.plannedagent.commands.Proposal;
import ca.yorku.plannedagent.model.Conflict;
import ca.yorku.plannedagent.model.FixedEvent;
import ca.yorku.plannedagent.model.Priority;
import ca.yorku.plannedagent.model.Task;
import ca.yorku.plannedagent.model.TimeBlock;
import ca.yorku.plannedagent.model.TimeFormat;
import ca.yorku.plannedagent.model.Workspace;
import ca.yorku.plannedagent.services.ReportService;
import ca.yorku.plannedagent.services.WeeklyReport;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Command-line interface. Uses the same PlannerFacade as the GUI, so both behave the same. */
public class PlannerCli {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE MMM d", Locale.ENGLISH);

    private final PlannerFacade facade;
    private final BufferedReader in;
    private final PrintStream out;

    public PlannerCli(PlannerFacade facade) {
        this(facade, System.in, System.out);
    }

    public PlannerCli(PlannerFacade facade, InputStream in, PrintStream out) {
        this.facade = facade;
        this.in = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        this.out = out;
    }

    public void run(String[] args) {
        if (args.length == 0) {
            printHelp();
            return;
        }
        facade.subscribe((type, message) -> {
            if (EventBus.AGENT.equals(type)) {
                print("  ... " + message);
            } else if (EventBus.WARNING.equals(type)) {
                print("Warning: " + message);
            }
        });
        Args a = Args.parse(Arrays.copyOfRange(args, 1, args.length));
        try {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "add" -> addFromText(a);
                case "new" -> newTask(a);
                case "tasks" -> printTasks();
                case "done" -> {
                    facade.completeTask(a.first("a task id"));
                    print("Marked done.");
                }
                case "delete" -> {
                    facade.deleteTask(a.first("a task id"));
                    print("Deleted.");
                }
                case "event" -> event(a);
                case "events" -> printEvents();
                case "week" -> printWeek();
                case "schedule" -> {
                    Proposal proposal = facade.autoSchedule(a.option("strategy", "deadline"));
                    print(proposal.getSummary());
                    confirm(proposal, a.flag("yes"));
                }
                case "conflicts" -> printConflicts();
                case "ask" -> ask(a);
                case "export" -> {
                    Path file = Path.of(a.first("a file name, e.g. export week.ics"));
                    print("Exported " + facade.exportCalendar(file) + " entries to " + file.toAbsolutePath());
                }
                case "report" -> printReport();
                case "undo" -> print(facade.undo() ? "Undone." : "Nothing to undo.");
                case "redo" -> print(facade.redo() ? "Redone." : "Nothing to redo.");
                case "help", "-h", "--help" -> printHelp();
                default -> {
                    print("Unknown command '" + args[0] + "'.");
                    printHelp();
                }
            }
        } catch (IllegalArgumentException | IllegalStateException | LlmException | UncheckedIOException e) {
            print("Error: " + e.getMessage());
        }
    }

    private void addFromText(Args a) {
        String text = a.text("the tasks, e.g. add \"EECS report Friday about 3 hours, gym 3 times\"");
        List<Task> tasks;
        try {
            tasks = facade.tasksFromText(text);
        } catch (LlmException e) {
            print("The AI is not available (" + e.getMessage() + ").");
            print("Add tasks by hand: new \"<title>\" --minutes 60 [--due 2026-11-06T17:00] [--priority high]");
            return;
        }
        if (tasks.isEmpty()) {
            print("No tasks found in that text.");
            return;
        }
        List<Task> valid = new ArrayList<>();
        for (int i = 0; i < tasks.size(); i++) {
            Task task = tasks.get(i);
            List<String> problems = task.problems(facade.now());
            print((i + 1) + ". " + describe(task) + (problems.isEmpty() ? "" : "   <- " + String.join(", ", problems)));
            if (problems.isEmpty()) {
                valid.add(task);
            }
        }
        if (valid.size() < tasks.size()) {
            print("Tasks with a problem will be skipped; add them with 'new'.");
        }
        if (!valid.isEmpty() && (a.flag("yes") || yes("Save " + valid.size() + " tasks? [y/N] "))) {
            facade.saveTasks(valid);
            print("Saved.");
        }
    }

    private void newTask(Args a) {
        String title = a.text("a title");
        int minutes;
        try {
            minutes = Integer.parseInt(a.option("minutes", "60"));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--minutes must be a whole number");
        }
        String due = a.option("due", null);
        Task task = new Task(title, minutes, due == null ? null : dateTime(due, "--due"),
                Priority.parseOrMedium(a.option("priority", "medium")), a.flag("outdoor"));
        facade.addTask(task);
        print("Added " + describe(task));
    }

    private void event(Args a) {
        List<String> words = a.positional;
        if (words.size() != 4 || !words.get(0).equalsIgnoreCase("add")) {
            throw new IllegalArgumentException("Use: event add \"<title>\" <start> <end>, e.g. "
                    + "event add \"EECS 3311 lecture\" 2026-11-04T10:00 2026-11-04T11:30");
        }
        FixedEvent event = new FixedEvent(words.get(1), dateTime(words.get(2), "start"), dateTime(words.get(3), "end"));
        facade.addEvent(event);
        print("Added event " + event.getId() + " " + event.getTitle() + " " + TimeFormat.range(event.getStart(), event.getEnd()));
    }

    private void ask(Args a) {
        AgentReply reply = facade.chat(a.text("a message, e.g. ask \"plan my week\""));
        print(reply.text);
        if (reply.proposal != null) {
            print("");
            print(reply.proposal.getSummary());
            confirm(reply.proposal, a.flag("yes"));
        }
    }

    private void confirm(Proposal proposal, boolean yes) {
        if (proposal == null || proposal.isEmpty()) {
            return;
        }
        if (proposal.hasConflicts()) {
            print("This proposal has conflicts, so it cannot be applied.");
            facade.discardProposal();
            return;
        }
        if (yes || yes("Apply this? [y/N] ")) {
            print(facade.applyProposal() + ".");
        } else {
            facade.discardProposal();
            print("Discarded.");
        }
    }

    private void printTasks() {
        Workspace workspace = facade.getWorkspace();
        if (workspace.getTasks().isEmpty()) {
            print("No tasks yet. Try: add \"EECS report Friday about 3 hours\"");
            return;
        }
        workspace.getTasks().forEach(task -> print(task.getId() + "  " + describe(task) + "  [" + task.getStatus() + "]"));
    }

    private void printEvents() {
        List<FixedEvent> events = facade.getWorkspace().getEvents();
        if (events.isEmpty()) {
            print("No fixed events.");
        }
        events.forEach(e -> print(e.getId() + "  " + e.getTitle() + "  " + TimeFormat.range(e.getStart(), e.getEnd())));
    }

    private void printWeek() {
        Workspace workspace = facade.getWorkspace();
        LocalDate today = facade.now().toLocalDate();
        for (LocalDate day = today; day.isBefore(today.plusDays(7)); day = day.plusDays(1)) {
            print(DAY.format(day));
            List<String> lines = new ArrayList<>();
            for (FixedEvent event : workspace.getEvents()) {
                if (event.getStart().toLocalDate().equals(day)) {
                    lines.add(TimeFormat.time(event.getStart()) + "-" + TimeFormat.time(event.getEnd()) + "  "
                            + event.getTitle() + " (event)");
                }
            }
            for (TimeBlock block : workspace.getPlan().blocksOn(day)) {
                Task task = workspace.getTask(block.getTaskId());
                lines.add(TimeFormat.time(block.getStart()) + "-" + TimeFormat.time(block.getEnd()) + "  "
                        + (task == null ? "?" : task.getTitle()) + " (" + block.getId() + ")");
            }
            lines.sort(String::compareTo);
            if (lines.isEmpty()) {
                print("  free");
            }
            lines.forEach(line -> print("  " + line));
        }
    }

    private void printConflicts() {
        List<Conflict> conflicts = facade.checkConflicts();
        if (conflicts.isEmpty()) {
            print("No conflicts found.");
        }
        conflicts.forEach(c -> print(c.getType() + ": " + c.getMessage()));
    }

    private void printReport() {
        WeeklyReport report = facade.weeklyReport();
        Map<String, Integer> stats = report.stats;
        print("Week of " + DAY.format(report.weekStart));
        print("  Planned:      " + stats.get(WeeklyReport.PLANNED_MINUTES) + " min");
        print("  Done:         " + stats.get(WeeklyReport.TASKS_DONE) + " tasks, "
                + stats.get(WeeklyReport.DONE_MINUTES) + " min");
        print("  Missed:       " + stats.get(WeeklyReport.TASKS_MISSED) + " tasks");
        print("  Busiest day:  " + ReportService.dayName(stats.get(WeeklyReport.BUSIEST_DAY)));
        print("");
        print(report.summary + (report.writtenByAi ? "" : "   (template summary)"));
    }

    private void printHelp() {
        print("""
                PlannedAgent - turns your to-do list into a week plan.
                Usage: plannedagent <command> [options]

                  add "<text>" [--yes]              add tasks from one sentence (AI)
                  new "<title>" --minutes N [--due YYYY-MM-DDTHH:MM] [--priority low|medium|high] [--outdoor]
                  tasks | done <id> | delete <id>   list, complete or delete tasks
                  event add "<title>" <start> <end> add a fixed event (times as YYYY-MM-DDTHH:MM)
                  events | week                     list events / show the next 7 days
                  schedule [--strategy deadline|priority|shortest] [--yes]
                  conflicts                         check the plan for problems
                  export <file.ics>                 export events and planned blocks to a calendar file
                  report                            this week's numbers and a short summary (AI)
                  ask "<message>" [--yes]           talk to the agent (AI)
                  undo | redo                       undo or redo the last applied change""");
    }

    private boolean yes(String question) {
        out.print(question);
        out.flush();
        try {
            String line = in.readLine();
            return line != null && line.trim().toLowerCase(Locale.ROOT).startsWith("y");
        } catch (IOException e) {
            return false;
        }
    }

    private static String describe(Task task) {
        return task.getTitle() + " | " + task.getMinutes() + " min | due " + TimeFormat.dayTime(task.getDeadline())
                + " | " + task.getPriority() + (task.isOutdoor() ? " | outdoor" : "");
    }

    private static LocalDateTime dateTime(String text, String what) {
        try {
            return LocalDateTime.parse(text.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(what + " must look like 2026-11-04T10:00");
        }
    }

    private void print(String text) {
        out.println(text);
    }

    /** Positional words plus --name value options and --flag switches. */
    static final class Args {
        final List<String> positional = new ArrayList<>();
        final Map<String, String> options = new HashMap<>();

        static Args parse(String[] words) {
            Args args = new Args();
            for (int i = 0; i < words.length; i++) {
                String word = words[i];
                if (word.startsWith("--") && word.length() > 2) {
                    String name = word.substring(2).toLowerCase(Locale.ROOT);
                    boolean hasValue = i + 1 < words.length && !words[i + 1].startsWith("--");
                    if (hasValue && !name.equals("yes") && !name.equals("outdoor")) {
                        args.options.put(name, words[++i]);
                    } else {
                        args.options.put(name, "true");
                    }
                } else {
                    args.positional.add(word);
                }
            }
            return args;
        }

        String first(String what) {
            if (positional.isEmpty()) {
                throw new IllegalArgumentException("Give " + what);
            }
            return positional.get(0);
        }

        String text(String what) {
            if (positional.isEmpty()) {
                throw new IllegalArgumentException("Give " + what);
            }
            return String.join(" ", positional);
        }

        String option(String name, String fallback) {
            return options.getOrDefault(name, fallback);
        }

        boolean flag(String name) {
            return options.containsKey(name);
        }
    }
}
