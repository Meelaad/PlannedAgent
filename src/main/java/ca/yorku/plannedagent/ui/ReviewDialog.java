package ca.yorku.plannedagent.ui;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import java.awt.Component;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;

/** End-of-day review: Done, Missed or Skip for each of today's blocks. */
final class ReviewDialog {

    record Item(String taskId, String label) {
    }

    record Result(List<String> done, List<String> missed) {
    }

    private ReviewDialog() {
    }

    static Result show(Component parent, List<Item> items) {
        JPanel form = new JPanel(new GridLayout(0, 4, 8, 4));
        form.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        List<JRadioButton[]> choices = new ArrayList<>();
        for (Item item : items) {
            JRadioButton done = new JRadioButton("Done");
            JRadioButton missed = new JRadioButton("Missed");
            JRadioButton skip = new JRadioButton("Skip", true);
            ButtonGroup group = new ButtonGroup();
            group.add(done);
            group.add(missed);
            group.add(skip);
            form.add(new JLabel(item.label()));
            form.add(done);
            form.add(missed);
            form.add(skip);
            choices.add(new JRadioButton[]{done, missed});
        }
        if (JOptionPane.showConfirmDialog(parent, form, "Review today", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return null;
        }
        List<String> done = new ArrayList<>();
        List<String> missed = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (choices.get(i)[0].isSelected()) {
                done.add(items.get(i).taskId());
            } else if (choices.get(i)[1].isSelected()) {
                missed.add(items.get(i).taskId());
            }
        }
        return new Result(done, missed);
    }
}
