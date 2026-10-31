package ca.yorku.plannedagent.agent.tools;

import ca.yorku.plannedagent.agent.Tool;
import ca.yorku.plannedagent.agent.ToolResult;
import ca.yorku.plannedagent.commands.Proposal;
import ca.yorku.plannedagent.model.Workspace;
import ca.yorku.plannedagent.scheduling.ConflictChecker;
import ca.yorku.plannedagent.scheduling.ScheduleResult;
import ca.yorku.plannedagent.scheduling.Scheduler;
import ca.yorku.plannedagent.scheduling.SchedulingStrategies;
import ca.yorku.plannedagent.scheduling.SchedulingStrategy;
import ca.yorku.plannedagent.services.Forecast;
import ca.yorku.plannedagent.services.WeatherService;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** schedule_tasks: places every open task in free time with a strategy, and returns a Proposal. */
public class ScheduleTasksTool implements Tool {

    private final Scheduler scheduler;
    private final WeatherService weather;
    private final ConflictChecker checker;
    private final Clock clock;

    public ScheduleTasksTool(Scheduler scheduler, WeatherService weather, ConflictChecker checker, Clock clock) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.weather = Objects.requireNonNull(weather, "weather");
        this.checker = Objects.requireNonNull(checker, "checker");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public String name() {
        return "schedule_tasks";
    }

    @Override
    public String description() {
        return "places every task that needs a time slot into free time over the next days and returns a "
                + "proposal. Args: strategy (deadline, priority or shortest; default deadline), "
                + "days (1-14, default 7).";
    }

    @Override
    public ToolResult run(Map<String, String> args, Workspace workspace) {
        String name = args.getOrDefault("strategy", "deadline");
        SchedulingStrategy strategy = SchedulingStrategies.byName(name);
        if (strategy == null) {
            return ToolResult.error("Unknown strategy '" + name + "'. Use one of: "
                    + String.join(", ", SchedulingStrategies.NAMES));
        }
        int days;
        try {
            days = (int) Double.parseDouble(args.getOrDefault("days", "7").trim());
        } catch (NumberFormatException e) {
            return ToolResult.error("'days' must be a number from 1 to 14");
        }
        if (days < 1 || days > 14) {
            return ToolResult.error("'days' must be a number from 1 to 14");
        }
        if (workspace.tasksToSchedule().isEmpty()) {
            return ToolResult.ok("Every task is already scheduled or done. Nothing to place.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime until = now.plusDays(days);
        List<String> warnings = new ArrayList<>();
        Forecast forecast;
        try {
            forecast = weather.forecast(now.toLocalDate(), until.toLocalDate());
        } catch (IOException e) {
            forecast = Forecast.none();
            warnings.add("the weather service is not available, so rain was not checked");
        }

        scheduler.setStrategy(strategy);
        ScheduleResult result = scheduler.schedule(workspace, now, until, forecast);
        Proposal proposal = result.toProposal("Schedule (" + strategy.name() + " first)", workspace, checker,
                forecast, warnings);
        if (proposal.isEmpty()) {
            return ToolResult.ok(proposal.getSummary());
        }
        return ToolResult.ok(proposal.getSummary() + "\nThis is a proposal; the student must apply it.", proposal);
    }
}
