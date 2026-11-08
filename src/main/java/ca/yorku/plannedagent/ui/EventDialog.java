package ca.yorku.plannedagent.ui;

import ca.yorku.plannedagent.model.FixedEvent;

import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.GridBagLayout;
import java.time.LocalDate;

/** The Add event form for fixed commitments like a lecture or a work shift. */
final class EventDialog {

    private EventDialog() {
    }

    static FixedEvent show(Component parent, LocalDate today) {
        JTextField title = new JTextField(22);
        JTextField date = new JTextField(today.plusDays(1).toString(), 10);
        JTextField start = new JTextField("10:00", 6);
        JTextField end = new JTextField("11:30", 6);

        JPanel form = new JPanel(new GridBagLayout());
        TaskDialog.row(form, 0, "Title", title);
        TaskDialog.row(form, 1, "Date (YYYY-MM-DD)", date);
        TaskDialog.row(form, 2, "Start (HH:MM)", start);
        TaskDialog.row(form, 3, "End (HH:MM)", end);

        while (JOptionPane.showConfirmDialog(parent, form, "Add fixed event", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            try {
                LocalDate day = Forms.parseDate(date.getText());
                return new FixedEvent(title.getText(), day.atTime(Forms.parseTime(start.getText())),
                        day.atTime(Forms.parseTime(end.getText())));
            } catch (IllegalArgumentException e) {
                JOptionPane.showMessageDialog(parent, e.getMessage(), "Add fixed event", JOptionPane.WARNING_MESSAGE);
            }
        }
        return null;
    }
}
