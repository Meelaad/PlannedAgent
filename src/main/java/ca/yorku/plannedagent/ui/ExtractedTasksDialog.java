package ca.yorku.plannedagent.ui;

import ca.yorku.plannedagent.model.Priority;
import ca.yorku.plannedagent.model.Task;

import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Shows the tasks the AI read from the text in an editable table before anything is saved (UC01 step 6).
 * Rows with a problem are highlighted and explained, so the student can fix them.
 */
final class ExtractedTasksDialog {

    private static final Color PROBLEM = new Color(0xFDE2E1);

    private ExtractedTasksDialog() {
    }

    /** Returns the tasks to save, or null if the student cancels. */
    static List<Task> show(Component parent, List<Task> tasks, LocalDateTime now) {
        Model model = new Model(tasks, now);
        JTable table = new JTable(model);
        table.setRowHeight(22);
        table.getColumnModel().getColumn(0).setMaxWidth(45);
        table.getColumnModel().getColumn(1).setPreferredWidth(170);
        table.getColumnModel().getColumn(2).setMaxWidth(60);
        table.getColumnModel().getColumn(3).setPreferredWidth(120);
        table.getColumnModel().getColumn(6).setPreferredWidth(220);
        table.getColumnModel().getColumn(4).setCellEditor(new DefaultCellEditor(new JComboBox<>(Priority.values())));
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus,
                                                           int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, selected, focus, row, column);
                if (!selected) {
                    c.setBackground(model.problem(row).isEmpty() ? Color.WHITE : PROBLEM);
                }
                return c;
            }
        };
        for (int column = 1; column < model.getColumnCount(); column++) {
            if (column != 5) {
                table.getColumnModel().getColumn(column).setCellRenderer(renderer);
            }
        }

        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.add(new JLabel("Check the tasks, fix anything wrong, then Save. Untick a row to leave it out."),
                BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(820, Math.min(80 + 22 * tasks.size(), 360)));
        panel.add(scroll, BorderLayout.CENTER);

        Object[] buttons = {"Save", "Cancel"};
        while (JOptionPane.showOptionDialog(parent, panel, "Tasks from your text", JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE, null, buttons, buttons[0]) == 0) {
            if (table.isEditing()) {
                table.getCellEditor().stopCellEditing();
            }
            List<Task> chosen = model.chosen();
            String problems = model.problemsOfChosen();
            if (!problems.isEmpty()) {
                JOptionPane.showMessageDialog(parent, "Please fix these first:\n" + problems, "Tasks from your text",
                        JOptionPane.WARNING_MESSAGE);
            } else if (chosen.isEmpty()) {
                JOptionPane.showMessageDialog(parent, "No rows are ticked.", "Tasks from your text",
                        JOptionPane.WARNING_MESSAGE);
            } else {
                return chosen;
            }
        }
        return null;
    }

    private static final class Model extends AbstractTableModel {
        private static final String[] COLUMNS = {"Save", "Title", "Min", "Due", "Priority", "Outdoor", "Problem"};

        private final List<Object[]> rows = new ArrayList<>();
        private final LocalDateTime now;

        Model(List<Task> tasks, LocalDateTime now) {
            this.now = now;
            for (Task t : tasks) {
                rows.add(new Object[]{true, t.getTitle(), t.getMinutes(), Forms.format(t.getDeadline()),
                        t.getPriority(), t.isOutdoor()});
            }
        }

        String problem(int row) {
            try {
                return String.join(", ", toTask(rows.get(row)).problems(now));
            } catch (IllegalArgumentException e) {
                return e.getMessage();
            }
        }

        List<Task> chosen() {
            List<Task> tasks = new ArrayList<>();
            for (Object[] row : rows) {
                if ((Boolean) row[0]) {
                    try {
                        tasks.add(toTask(row));
                    } catch (IllegalArgumentException e) {
                        // reported by problemsOfChosen()
                    }
                }
            }
            return tasks;
        }

        String problemsOfChosen() {
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < rows.size(); i++) {
                String problem = problem(i);
                if ((Boolean) rows.get(i)[0] && !problem.isEmpty()) {
                    text.append("- ").append(rows.get(i)[1]).append(": ").append(problem).append('\n');
                }
            }
            return text.toString();
        }

        private static Task toTask(Object[] row) {
            return new Task((String) row[1], (Integer) row[2], Forms.parseDeadline((String) row[3]),
                    (Priority) row[4], (Boolean) row[5]);
        }

        @Override
        public int getRowCount() {
            return rows.size();
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
        public Class<?> getColumnClass(int column) {
            return switch (column) {
                case 0, 5 -> Boolean.class;
                case 2 -> Integer.class;
                case 4 -> Priority.class;
                default -> String.class;
            };
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column < 6;
        }

        @Override
        public Object getValueAt(int row, int column) {
            return column == 6 ? problem(row) : rows.get(row)[column];
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            if (column == 2 && value == null) {
                return;
            }
            rows.get(row)[column] = value;
            fireTableRowsUpdated(row, row);
        }
    }
}
