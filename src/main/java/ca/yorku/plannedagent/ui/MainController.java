package ca.yorku.plannedagent.ui;

import ca.yorku.plannedagent.agent.AgentReply;
import ca.yorku.plannedagent.ai.LlmException;
import ca.yorku.plannedagent.app.EventBus;
import ca.yorku.plannedagent.app.PlannerFacade;
import ca.yorku.plannedagent.app.PlannerObserver;
import ca.yorku.plannedagent.model.Task;
import ca.yorku.plannedagent.model.TimeFormat;
import ca.yorku.plannedagent.model.Workspace;

import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * MVC controller and Observer: turns clicks into facade calls and refreshes the view when the model changes.
 * Slow calls (the LLM, the weather service) run in a SwingWorker so the window does not freeze.
 */
public class MainController implements PlannerObserver {

    private final PlannerFacade facade;
    private final MainWindow window;

    public MainController(PlannerFacade facade, MainWindow window) {
        this.facade = facade;
        this.window = window;
        window.bind(this);
        refresh();
    }

    public void onAddText(String text) {
        if (text == null || text.isBlank()) {
            window.showError("Type some tasks first, e.g. \"EECS report Friday about 3 hours, gym twice\".");
            return;
        }
        background("Reading your tasks...", () -> facade.tasksFromText(text), tasks -> {
            if (tasks.isEmpty()) {
                window.showError("No tasks were found in that text.");
                return;
            }
            List<Task> valid = new ArrayList<>();
            StringBuilder list = new StringBuilder();
            for (Task task : tasks) {
                List<String> problems = task.problems(facade.now());
                list.append(problems.isEmpty() ? "  " : "! ").append(task.getTitle()).append(", ")
                        .append(task.getMinutes()).append(" min, due ").append(TimeFormat.dayTime(task.getDeadline()))
                        .append(", ").append(task.getPriority())
                        .append(problems.isEmpty() ? "" : "  (" + String.join(", ", problems) + ")").append('\n');
                if (problems.isEmpty()) {
                    valid.add(task);
                }
            }
            if (!valid.isEmpty() && window.confirm("Save these tasks?\n\n" + list
                    + (valid.size() < tasks.size() ? "\nTasks marked ! have a problem and will be skipped." : ""),
                    "Tasks from your text")) {
                onSaveTasks(valid);
            }
        });
    }

    public void onSaveTasks(List<Task> tasks) {
        safely(() -> facade.saveTasks(tasks));
    }

    public void onDeleteTask(String taskId) {
        Task task = facade.getWorkspace().getTask(taskId);
        if (task != null && window.confirm("Delete '" + task.getTitle() + "'?", "Delete task")) {
            safely(() -> facade.deleteTask(taskId));
        }
    }

    public void onComplete(String taskId) {
        safely(() -> facade.completeTask(taskId));
    }

    public void onAutoSchedule(String strategyName) {
        background("Scheduling...", () -> facade.autoSchedule(strategyName), proposal -> {
            if (!proposal.isEmpty()) {
                window.showProposal(proposal);
            } else if (facade.getWorkspace().tasksToSchedule().isEmpty()) {
                window.showStatus("Every task is already scheduled or done.");
            } else {
                window.showError(proposal.getSummary());
            }
        });
    }

    public void onCheckConflicts() {
        background("Checking the plan...", facade::checkConflicts, window::showConflicts);
    }

    public void onSend(String message) {
        window.showChat("You: " + message);
        background("The agent is thinking...", () -> facade.chat(message), (AgentReply reply) -> {
            window.showChat("Agent: " + reply.text);
            if (reply.proposal != null) {
                window.showProposal(reply.proposal);
            }
        });
    }

    public void onApply() {
        safely(() -> {
            String message = facade.applyProposal();
            window.showProposal(null);
            window.showStatus(message);
        });
    }

    public void onDiscard() {
        facade.discardProposal();
        window.showProposal(null);
    }

    public void onUndo() {
        safely(() -> {
            if (!facade.undo()) {
                window.showStatus("Nothing to undo.");
            }
        });
    }

    public void onRedo() {
        safely(() -> {
            if (!facade.redo()) {
                window.showStatus("Nothing to redo.");
            }
        });
    }

    public void onReport() {
        background("Writing your weekly report...", facade::weeklyReport, window::showReport);
    }

    @Override
    public void update(String type, String message) {
        SwingUtilities.invokeLater(() -> {
            if (EventBus.AGENT.equals(type)) {
                window.showAgentStatus("The agent is " + message + "...");
                return;
            }
            if (EventBus.PROPOSAL.equals(type) && "discarded".equals(message)) {
                window.showProposal(null);
            }
            window.showStatus(EventBus.WARNING.equals(type) ? "Warning: " + message : message);
            refresh();
        });
    }

    private void refresh() {
        Workspace workspace = facade.getWorkspace();
        window.showTasks(workspace.getTasks());
        window.showWeek(workspace.getPlan(), workspace.getEvents(), workspace.getTasks());
    }

    private void safely(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException | IllegalStateException | LlmException e) {
            window.showError(e.getMessage());
        } catch (RuntimeException e) {
            window.showError("Something went wrong: " + e.getMessage());
        }
        refresh();
    }

    private <T> void background(String busyMessage, Supplier<T> work, Consumer<T> onDone) {
        window.setBusy(true, busyMessage);
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() {
                return work.get();
            }

            @Override
            protected void done() {
                window.setBusy(false, null);
                try {
                    onDone.accept(get());
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    window.showError(cause instanceof LlmException
                            ? "The AI is not available right now (" + cause.getMessage() + ")."
                            : String.valueOf(cause.getMessage()));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                refresh();
            }
        }.execute();
    }
}
