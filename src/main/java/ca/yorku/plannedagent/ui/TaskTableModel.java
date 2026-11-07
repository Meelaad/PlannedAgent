package ca.yorku.plannedagent.ui;

import ca.yorku.plannedagent.model.Task;
import ca.yorku.plannedagent.model.TimeFormat;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/** Read-only table of tasks for the Tasks panel. */
class TaskTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {"Task", "Min", "Due", "Priority", "Status"};

    private List<Task> tasks = new ArrayList<>();

    void setTasks(List<Task> tasks) {
        this.tasks = new ArrayList<>(tasks);
        fireTableDataChanged();
    }

    Task taskAt(int row) {
        return row < 0 || row >= tasks.size() ? null : tasks.get(row);
    }

    int rowOf(String taskId) {
        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).getId().equals(taskId)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int getRowCount() {
        return tasks.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Object getValueAt(int row, int column) {
        Task task = tasks.get(row);
        return switch (column) {
            case 0 -> task.getTitle() + (task.isOutdoor() ? " (outdoor)" : "");
            case 1 -> task.getMinutes();
            case 2 -> task.getDeadline() == null ? "" : TimeFormat.dayTime(task.getDeadline());
            case 3 -> task.getPriority();
            default -> switch (task.getStatus()) {
                case "TODO" -> "To-do";
                case "SCHEDULED" -> "Scheduled";
                case "MISSED" -> "Missed";
                case "DONE" -> "Done";
                default -> task.getStatus();
            };
        };
    }
}
