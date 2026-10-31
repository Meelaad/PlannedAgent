package ca.yorku.plannedagent.agent.tools;

import ca.yorku.plannedagent.agent.Tool;
import ca.yorku.plannedagent.agent.ToolResult;
import ca.yorku.plannedagent.model.Task;
import ca.yorku.plannedagent.model.TimeFormat;
import ca.yorku.plannedagent.model.Workspace;

import java.util.Map;

/** get_tasks: lists the tasks and their status. Read only. */
public class GetTasksTool implements Tool {

    @Override
    public String name() {
        return "get_tasks";
    }

    @Override
    public String description() {
        return "lists every task with its id, length, deadline, priority and status. No args.";
    }

    @Override
    public ToolResult run(Map<String, String> args, Workspace workspace) {
        if (workspace.getTasks().isEmpty()) {
            return ToolResult.ok("There are no tasks.");
        }
        StringBuilder text = new StringBuilder();
        for (Task task : workspace.getTasks()) {
            text.append(task.getId()).append(" | ").append(task.getTitle())
                    .append(" | ").append(task.getMinutes()).append(" min")
                    .append(" | due ").append(TimeFormat.dayTime(task.getDeadline()))
                    .append(" | ").append(task.getPriority())
                    .append(task.isOutdoor() ? " | outdoor" : "")
                    .append(" | ").append(task.getStatus())
                    .append('\n');
        }
        return ToolResult.ok(text.toString().trim());
    }
}
