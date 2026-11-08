package ca.yorku.plannedagent.ui;

import ca.yorku.plannedagent.model.Priority;
import ca.yorku.plannedagent.model.Task;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/** The New / Edit task form. Returns the filled-in task, or null if the student cancels. */
final class TaskDialog {

    private TaskDialog() {
    }

    /** For an edit, pass the task: a copy with the same id comes back, the original is not touched. */
    static Task show(Component parent, Task existing) {
        JTextField title = new JTextField(existing == null ? "" : existing.getTitle(), 24);
        JSpinner minutes = new JSpinner(new SpinnerNumberModel(
                existing == null ? 60 : Math.max(Task.MIN_MINUTES, Math.min(Task.MAX_MINUTES, existing.getMinutes())),
                Task.MIN_MINUTES, Task.MAX_MINUTES, 5));
        JTextField due = new JTextField(existing == null ? "" : Forms.format(existing.getDeadline()), 16);
        JComboBox<Priority> priority = new JComboBox<>(Priority.values());
        priority.setSelectedItem(existing == null ? Priority.MEDIUM : existing.getPriority());
        JCheckBox outdoor = new JCheckBox("Done outside (keep it out of the rain)",
                existing != null && existing.isOutdoor());

        JPanel form = new JPanel(new GridBagLayout());
        row(form, 0, "Title", title);
        row(form, 1, "Minutes", minutes);
        row(form, 2, "Due (YYYY-MM-DD HH:MM)", due);
        row(form, 3, "Priority", priority);
        row(form, 4, "", outdoor);

        String heading = existing == null ? "New task" : "Edit task";
        while (JOptionPane.showConfirmDialog(parent, form, heading, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            try {
                Task task = existing == null
                        ? new Task(title.getText(), (Integer) minutes.getValue(), Forms.parseDeadline(due.getText()),
                        (Priority) priority.getSelectedItem(), outdoor.isSelected())
                        : existing.copy();
                if (existing != null) {
                    task.setTitle(title.getText());
                    task.setMinutes((Integer) minutes.getValue());
                    task.setDeadline(Forms.parseDeadline(due.getText()));
                    task.setPriority((Priority) priority.getSelectedItem());
                    task.setOutdoor(outdoor.isSelected());
                }
                if (task.getTitle().isBlank()) {
                    throw new IllegalArgumentException("The title is empty");
                }
                return task;
            } catch (IllegalArgumentException e) {
                JOptionPane.showMessageDialog(parent, e.getMessage(), heading, JOptionPane.WARNING_MESSAGE);
            }
        }
        return null;
    }

    static void row(JPanel form, int y, String label, Component field) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = y;
        c.insets = new Insets(3, 3, 3, 3);
        c.anchor = GridBagConstraints.WEST;
        form.add(new JLabel(label), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        form.add(field, c);
    }
}
