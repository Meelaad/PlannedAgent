package ca.yorku.plannedagent.app;

import ca.yorku.plannedagent.agent.Agent;
import ca.yorku.plannedagent.agent.AgentReply;
import ca.yorku.plannedagent.agent.ConversationMemory;
import ca.yorku.plannedagent.agent.tools.GetScheduleTool;
import ca.yorku.plannedagent.agent.tools.GetTasksTool;
import ca.yorku.plannedagent.agent.tools.MoveTaskTool;
import ca.yorku.plannedagent.agent.tools.RememberPreferenceTool;
import ca.yorku.plannedagent.agent.tools.ScheduleTasksTool;
import ca.yorku.plannedagent.ai.LlmClient;
import ca.yorku.plannedagent.ai.LlmException;
import ca.yorku.plannedagent.ai.TaskExtractor;
import ca.yorku.plannedagent.commands.CommandHistory;
import ca.yorku.plannedagent.commands.Proposal;
import ca.yorku.plannedagent.model.Conflict;
import ca.yorku.plannedagent.model.FixedEvent;
import ca.yorku.plannedagent.model.Preference;
import ca.yorku.plannedagent.model.Task;
import ca.yorku.plannedagent.model.TimeBlock;
import ca.yorku.plannedagent.model.Workspace;
import ca.yorku.plannedagent.scheduling.ConflictChecker;
import ca.yorku.plannedagent.scheduling.DeadlineFirstStrategy;
import ca.yorku.plannedagent.scheduling.Scheduler;
import ca.yorku.plannedagent.scheduling.SchedulingStrategies;
import ca.yorku.plannedagent.scheduling.SchedulingStrategy;
import ca.yorku.plannedagent.services.Forecast;
import ca.yorku.plannedagent.services.JsonStorage;
import ca.yorku.plannedagent.services.OpenMeteoWeather;
import ca.yorku.plannedagent.services.WeatherService;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Facade pattern: the one entry point used by both the GUI and the CLI.
 * Every operation that changes data saves the workspace and tells the observers.
 */
public class PlannerFacade {

    static final int PLAN_DAYS = 7;
    static final double YORK_LATITUDE = 43.7735;
    static final double YORK_LONGITUDE = -79.5019;

    private final Workspace workspace;
    private final Agent agent;
    private final Scheduler scheduler;
    private final ConflictChecker checker;
    private final CommandHistory history;
    private final TaskExtractor extractor;
    private final WeatherService weather;
    private final JsonStorage storage;
    private final EventBus events;
    private final Clock clock;
    private Proposal pending;

    public PlannerFacade(Workspace workspace, LlmClient llm, JsonStorage storage) {
        this(workspace, llm, storage, new OpenMeteoWeather(YORK_LATITUDE, YORK_LONGITUDE), Clock.systemDefaultZone());
    }

    public PlannerFacade(Workspace workspace, LlmClient llm, JsonStorage storage, WeatherService weather, Clock clock) {
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.weather = Objects.requireNonNull(weather, "weather");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.events = new EventBus();
        this.scheduler = new Scheduler();
        this.checker = new ConflictChecker();
        this.history = new CommandHistory();
        this.extractor = new TaskExtractor(llm);
        this.agent = new Agent(llm, List.of(
                new GetTasksTool(),
                new GetScheduleTool(clock),
                new ScheduleTasksTool(scheduler, weather, checker, clock),
                new MoveTaskTool(checker, weather, clock),
                new RememberPreferenceTool()),
                new ConversationMemory(), events, clock);
    }

    /** Asks the LLM to read the text. Nothing is saved until saveTasks(). Throws LlmException if the AI is down. */
    public List<Task> tasksFromText(String text) {
        return extractor.extract(text, now().toLocalDate());
    }

    public void saveTasks(List<Task> tasks) {
        if (tasks.isEmpty()) {
            throw new IllegalArgumentException("There are no tasks to save");
        }
        for (Task task : tasks) {
            requireValid(task);
        }
        tasks.forEach(workspace::addTask);
        changed(EventBus.TASKS, "Added " + tasks.size() + (tasks.size() == 1 ? " task" : " tasks"));
    }

    public void addTask(Task task) {
        requireValid(task);
        workspace.addTask(task);
        changed(EventBus.TASKS, "Added task '" + task.getTitle() + "'");
    }

    /** Copies the edited fields onto the stored task. A new length frees the task's slot so it can be planned again. */
    public void updateTask(Task edited) {
        Task task = findTask(edited.getId());
        List<String> problems = new ArrayList<>(edited.problems(now()));
        if (Objects.equals(edited.getDeadline(), task.getDeadline())) {
            problems.remove("The deadline is in the past");
        }
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(String.join(". ", problems));
        }
        boolean lengthChanged = edited.getMinutes() != task.getMinutes();
        task.setTitle(edited.getTitle());
        task.setMinutes(edited.getMinutes());
        task.setDeadline(edited.getDeadline());
        task.setPriority(edited.getPriority());
        task.setOutdoor(edited.isOutdoor());
        if (lengthChanged && "SCHEDULED".equals(task.getStatus())) {
            for (TimeBlock block : workspace.getPlan().blocksFor(task.getId())) {
                workspace.getPlan().remove(block.getId());
            }
            task.unschedule();
        }
        changed(EventBus.TASKS, "Updated task '" + task.getTitle() + "'");
    }

    public void deleteTask(String taskId) {
        Task task = findTask(taskId);
        workspace.removeTask(taskId);
        changed(EventBus.TASKS, "Deleted task '" + task.getTitle() + "'");
    }

    /** The task's state decides: completing a done task throws IllegalStateException. */
    public void completeTask(String taskId) {
        Task task = findTask(taskId);
        task.complete();
        changed(EventBus.TASKS, "Completed '" + task.getTitle() + "'");
    }

    public void addEvent(FixedEvent event) {
        workspace.addEvent(event);
        changed(EventBus.EVENTS, "Added event '" + event.getTitle() + "'");
    }

    public Proposal autoSchedule(String strategyName) {
        List<String> warnings = new ArrayList<>();
        SchedulingStrategy strategy = strategyFor(strategyName, warnings);
        LocalDateTime now = now();
        LocalDateTime until = now.plusDays(PLAN_DAYS);
        Forecast forecast = forecast(now, until, warnings);
        scheduler.setStrategy(strategy);
        Proposal proposal = scheduler.schedule(workspace, now, until, forecast)
                .toProposal("Auto-schedule (" + strategy.name() + " first)", workspace, checker, forecast, warnings);
        setPending(proposal.isEmpty() ? null : proposal);
        return proposal;
    }

    /** Never throws LlmException: if the AI is down the reply says so and points to the non-AI features. */
    public AgentReply chat(String message) {
        List<Preference> before = List.copyOf(workspace.getPreferences());
        AgentReply reply;
        try {
            reply = agent.handle(message, workspace);
        } catch (LlmException e) {
            reply = new AgentReply("The AI is not available right now (" + e.getMessage()
                    + "). You can still use Auto-schedule and edit tasks by hand.", null);
        }
        if (!before.equals(workspace.getPreferences())) {
            changed(EventBus.PREFERENCES, "Preferences: " + describe(workspace.getPreferences()));
        }
        if (reply.proposal != null) {
            setPending(reply.proposal);
        }
        return reply;
    }

    public String applyProposal() {
        if (pending == null) {
            throw new IllegalStateException("There is no proposal to apply");
        }
        if (pending.hasConflicts()) {
            throw new IllegalStateException("This proposal has conflicts and cannot be applied. "
                    + "Discard it or ask for another time.");
        }
        Proposal proposal = pending;
        pending = null;
        try {
            history.run(proposal.getCommands(), workspace);
        } catch (IllegalStateException e) {
            events.notifyObservers(EventBus.PROPOSAL, "discarded");
            throw new IllegalStateException("The plan changed since this proposal was made (" + e.getMessage()
                    + "). Please ask again.", e);
        }
        int count = proposal.getCommands().size();
        String message = "Applied " + count + (count == 1 ? " change" : " changes");
        changed(EventBus.PLAN, message);
        return message;
    }

    public void discardProposal() {
        if (pending != null) {
            pending = null;
            events.notifyObservers(EventBus.PROPOSAL, "discarded");
        }
    }

    public List<Conflict> checkConflicts() {
        List<String> warnings = new ArrayList<>();
        LocalDateTime now = now();
        Forecast forecast = forecast(now, now.plusDays(14), warnings);
        warnings.forEach(w -> events.notifyObservers(EventBus.WARNING, w));
        return checker.check(workspace, forecast);
    }

    public boolean undo() {
        boolean done = history.undo(workspace);
        if (done) {
            changed(EventBus.PLAN, "Undo");
        }
        return done;
    }

    public boolean redo() {
        boolean done = history.redo(workspace);
        if (done) {
            changed(EventBus.PLAN, "Redo");
        }
        return done;
    }

    public void subscribe(PlannerObserver observer) {
        events.attach(observer);
    }

    public Workspace getWorkspace() {
        return workspace;
    }

    public Proposal getPending() {
        return pending;
    }

    private SchedulingStrategy strategyFor(String name, List<String> warnings) {
        SchedulingStrategy strategy = SchedulingStrategies.byName(name);
        if (strategy == null) {
            warnings.add("unknown strategy '" + name + "', so deadline first was used");
            return new DeadlineFirstStrategy();
        }
        return strategy;
    }

    private Forecast forecast(LocalDateTime from, LocalDateTime to, List<String> warnings) {
        try {
            return weather.forecast(from.toLocalDate(), to.toLocalDate());
        } catch (IOException e) {
            warnings.add("the weather service is not available, so rain was not checked");
            return Forecast.none();
        }
    }

    private void setPending(Proposal proposal) {
        pending = proposal;
        if (proposal != null) {
            events.notifyObservers(EventBus.PROPOSAL, proposal.getCommands().size() + " changes waiting");
        }
    }

    private Task findTask(String taskId) {
        Task task = taskId == null ? null : workspace.getTask(taskId.trim());
        if (task == null) {
            throw new IllegalArgumentException("There is no task " + taskId);
        }
        return task;
    }

    private void requireValid(Task task) {
        List<String> problems = task.problems(now());
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("'" + task.getTitle() + "': " + String.join(". ", problems));
        }
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private void changed(String type, String message) {
        storage.save(workspace);
        events.notifyObservers(type, message);
    }

    private static String describe(List<Preference> preferences) {
        return preferences.isEmpty() ? "none"
                : String.join("; ", preferences.stream().map(Preference::describe).toList());
    }
}
