package ca.yorku.plannedagent.ui;

import ca.yorku.plannedagent.commands.Proposal;
import ca.yorku.plannedagent.model.Conflict;
import ca.yorku.plannedagent.model.FixedEvent;
import ca.yorku.plannedagent.model.Plan;
import ca.yorku.plannedagent.model.Task;
import ca.yorku.plannedagent.scheduling.SchedulingStrategies;
import ca.yorku.plannedagent.services.ReportService;
import ca.yorku.plannedagent.services.WeeklyReport;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.File;
import java.nio.file.Path;
import java.awt.Font;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * MVC view: one window with the Tasks, Week and Chat panels. It only shows data and forwards
 * clicks to the MainController; it never calls the facade or changes the model.
 */
public class MainWindow extends JFrame {

    private final JPanel tasksPanel = new JPanel(new BorderLayout(6, 6));
    private final JPanel weekPanel = new JPanel(new BorderLayout(6, 6));
    private final JPanel chatPanel = new JPanel(new BorderLayout(6, 6));

    private final TaskTableModel taskModel = new TaskTableModel();
    private final JTable taskTable = new JTable(taskModel);
    private final JTextArea taskText = new JTextArea(3, 20);
    private final WeekView weekView;
    private final JTextArea conflictArea = new JTextArea(4, 20);
    private final JComboBox<String> strategyBox = new JComboBox<>(SchedulingStrategies.NAMES.toArray(String[]::new));
    private final JTextArea chatArea = new JTextArea();
    private final JTextField chatInput = new JTextField();
    private final JLabel agentStatus = new JLabel(" ");
    private final JPanel proposalCard = new JPanel(new BorderLayout(4, 4));
    private final JTextArea proposalText = new JTextArea(6, 20);
    private final JButton applyButton = new JButton("Apply");
    private final JLabel statusBar = new JLabel(" ");
    private final List<JComponent> actions = new ArrayList<>();

    private final Clock clock;
    private MainController controller;
    private boolean proposalCanApply;

    public MainWindow() {
        this(Clock.systemDefaultZone());
    }

    public MainWindow(Clock clock) {
        super("PlannedAgent");
        this.clock = clock;
        weekView = new WeekView(clock);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        buildTasksPanel();
        buildWeekPanel();
        buildChatPanel();
        Font text = UIManager.getFont("Label.font");
        if (text != null) {
            for (JTextArea area : List.of(taskText, conflictArea, chatArea, proposalText)) {
                area.setFont(text.deriveFont(13f));
            }
        }

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        root.add(tasksPanel, BorderLayout.WEST);
        root.add(weekPanel, BorderLayout.CENTER);
        root.add(chatPanel, BorderLayout.EAST);
        statusBar.setForeground(Color.DARK_GRAY);
        root.add(statusBar, BorderLayout.SOUTH);
        setContentPane(root);
        setMinimumSize(new Dimension(1100, 650));
        setSize(1400, 820);
        setLocationRelativeTo(null);
    }

    void bind(MainController controller) {
        this.controller = controller;
    }

    public void showWindow() {
        setVisible(true);
    }

    public void showTasks(List<Task> tasks) {
        String selected = selectedTaskId();
        taskModel.setTasks(tasks);
        int row = selected == null ? -1 : taskModel.rowOf(selected);
        if (row >= 0) {
            taskTable.setRowSelectionInterval(row, row);
        }
    }

    public void showWeek(Plan plan, List<FixedEvent> events, List<Task> tasks) {
        weekView.show(plan, events, tasks);
    }

    public void showChat(String text) {
        chatArea.append(text + "\n\n");
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    public void showProposal(Proposal proposal) {
        if (proposal == null || proposal.isEmpty()) {
            proposalCanApply = false;
            proposalCard.setVisible(false);
            weekView.highlight(Set.of());
            return;
        }
        proposalText.setText(proposal.getSummary());
        proposalText.setCaretPosition(0);
        proposalCanApply = !proposal.hasConflicts();
        applyButton.setEnabled(proposalCanApply);
        applyButton.setToolTipText(proposal.hasConflicts() ? "This proposal has conflicts and cannot be applied" : null);
        Set<String> ids = new HashSet<>();
        proposal.getConflicts().forEach(c -> ids.addAll(c.getBlockIds()));
        weekView.highlight(ids);
        proposalCard.setVisible(true);
        chatPanel.revalidate();
    }

    public void showConflicts(List<Conflict> conflicts) {
        Set<String> ids = new HashSet<>();
        StringBuilder text = new StringBuilder();
        for (Conflict conflict : conflicts) {
            text.append(conflict.getType()).append(": ").append(conflict.getMessage()).append('\n');
            ids.addAll(conflict.getBlockIds());
        }
        conflictArea.setText(conflicts.isEmpty() ? "No conflicts found." : text.toString().trim());
        conflictArea.setCaretPosition(0);
        weekView.highlight(ids);
    }

    public void showReport(WeeklyReport report) {
        String text = "Planned: " + report.stats.get(WeeklyReport.PLANNED_MINUTES) + " min\n"
                + "Done: " + report.stats.get(WeeklyReport.TASKS_DONE) + " tasks ("
                + report.stats.get(WeeklyReport.DONE_MINUTES) + " min)\n"
                + "Missed: " + report.stats.get(WeeklyReport.TASKS_MISSED) + " tasks\n"
                + "Busiest day: " + ReportService.dayName(report.stats.get(WeeklyReport.BUSIEST_DAY)) + "\n\n"
                + report.summary + (report.writtenByAi ? "" : "\n\n(template summary: the AI was not available)");
        JTextArea area = readOnlyArea(text);
        area.setColumns(42);
        area.setFont(chatArea.getFont());
        JOptionPane.showMessageDialog(this, new JScrollPane(area), "Weekly report, week of " + report.weekStart,
                JOptionPane.INFORMATION_MESSAGE);
    }

    public void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "PlannedAgent", JOptionPane.WARNING_MESSAGE);
    }

    void showStatus(String text) {
        statusBar.setText(text);
    }

    void showAgentStatus(String text) {
        agentStatus.setText(text == null || text.isBlank() ? " " : text);
    }

    /** While the AI or the weather service is working, buttons are disabled so nothing changes underneath it. */
    void setBusy(boolean busy, String message) {
        actions.forEach(a -> a.setEnabled(!busy));
        applyButton.setEnabled(!busy && proposalCanApply);
        showAgentStatus(busy ? message : " ");
        setCursor(busy ? java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR) : null);
    }

    /** Lets the student check and fix the tasks the AI read; returns the ones to save, or null. */
    List<Task> reviewExtractedTasks(List<Task> tasks, LocalDateTime now) {
        return ExtractedTasksDialog.show(this, tasks, now);
    }

    ReviewDialog.Result askReview(List<ReviewDialog.Item> items) {
        return ReviewDialog.show(this, items);
    }

    boolean confirm(String message, String title) {
        return JOptionPane.showConfirmDialog(this, message, title, JOptionPane.YES_NO_OPTION)
                == JOptionPane.YES_OPTION;
    }

    String selectedTaskId() {
        Task task = taskModel.taskAt(taskTable.getSelectedRow());
        return task == null ? null : task.getId();
    }

    private void buildTasksPanel() {
        tasksPanel.setPreferredSize(new Dimension(390, 0));
        tasksPanel.setBorder(BorderFactory.createTitledBorder("Tasks"));

        taskText.setLineWrap(true);
        taskText.setWrapStyleWord(true);
        taskText.setToolTipText("e.g. EECS report Friday about 3 hours, gym 3 times, groceries");
        JButton addText = button("Add from text", () -> controller.onAddText(taskText.getText()));
        JPanel top = new JPanel(new BorderLayout(4, 4));
        top.add(new JLabel("Type your tasks in one sentence:"), BorderLayout.NORTH);
        top.add(new JScrollPane(taskText), BorderLayout.CENTER);
        top.add(addText, BorderLayout.EAST);
        tasksPanel.add(top, BorderLayout.NORTH);

        taskTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        taskTable.setRowHeight(22);
        taskTable.getColumnModel().getColumn(0).setPreferredWidth(140);
        taskTable.getColumnModel().getColumn(1).setPreferredWidth(32);
        taskTable.getColumnModel().getColumn(2).setPreferredWidth(110);
        taskTable.getColumnModel().getColumn(3).setPreferredWidth(60);
        taskTable.getColumnModel().getColumn(4).setPreferredWidth(70);
        tasksPanel.add(new JScrollPane(taskTable), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        buttons.add(button("New", () -> {
            Task task = TaskDialog.show(this, null);
            if (task != null) {
                controller.onAddTask(task);
            }
        }));
        buttons.add(button("Edit", () -> {
            Task selected = taskModel.taskAt(taskTable.getSelectedRow());
            if (selected == null) {
                showError("Select a task first.");
                return;
            }
            Task edited = TaskDialog.show(this, selected);
            if (edited != null) {
                controller.onEditTask(edited);
            }
        }));
        buttons.add(button("Mark done", () -> withSelectedTask(id -> controller.onComplete(id))));
        buttons.add(button("Delete", () -> withSelectedTask(id -> controller.onDeleteTask(id))));
        tasksPanel.add(buttons, BorderLayout.SOUTH);
    }

    private void buildWeekPanel() {
        weekPanel.setBorder(BorderFactory.createTitledBorder("Week"));
        JPanel planRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        planRow.add(new JLabel("Strategy:"));
        planRow.add(strategyBox);
        actions.add(strategyBox);
        planRow.add(button("Auto-schedule", () -> controller.onAutoSchedule((String) strategyBox.getSelectedItem())));
        planRow.add(button("Check conflicts", () -> controller.onCheckConflicts()));
        planRow.add(Box.createHorizontalStrut(12));
        planRow.add(button("Undo", () -> controller.onUndo()));
        planRow.add(button("Redo", () -> controller.onRedo()));
        JPanel moreRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        moreRow.add(button("Add event", () -> {
            FixedEvent event = EventDialog.show(this, LocalDate.now(clock));
            if (event != null) {
                controller.onAddEvent(event);
            }
        }));
        moreRow.add(button("Review today", () -> controller.onReviewToday()));
        moreRow.add(button("Weekly report", () -> controller.onReport()));
        moreRow.add(button("Export .ics", () -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new FileNameExtensionFilter("Calendar files (*.ics)", "ics"));
            chooser.setSelectedFile(new File("plannedagent-week.ics"));
            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                Path file = chooser.getSelectedFile().toPath();
                if (!file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ics")) {
                    file = file.resolveSibling(file.getFileName() + ".ics");
                }
                controller.onExport(file);
            }
        }));
        JPanel toolbar = new JPanel(new GridLayout(2, 1));
        toolbar.add(planRow);
        toolbar.add(moreRow);
        weekPanel.add(toolbar, BorderLayout.NORTH);

        JScrollPane weekScroll = new JScrollPane(weekView);
        weekScroll.getVerticalScrollBar().setUnitIncrement(16);
        weekPanel.add(weekScroll, BorderLayout.CENTER);

        conflictArea.setEditable(false);
        conflictArea.setLineWrap(true);
        conflictArea.setWrapStyleWord(true);
        conflictArea.setText("Click Check conflicts to look for problems.");
        weekPanel.add(new JScrollPane(conflictArea), BorderLayout.SOUTH);
    }

    private void buildChatPanel() {
        chatPanel.setPreferredSize(new Dimension(380, 0));
        chatPanel.setBorder(BorderFactory.createTitledBorder("Chat with the agent"));
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setText("Agent: Hi! Ask me to plan your week, move something, or tell me a rule "
                + "like \"I never study after 10pm\".\n\n");
        chatPanel.add(new JScrollPane(chatArea), BorderLayout.CENTER);

        proposalText.setEditable(false);
        proposalText.setLineWrap(true);
        proposalText.setWrapStyleWord(true);
        proposalCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x4A7FC1)), BorderFactory.createEmptyBorder(4, 4, 4, 4)));
        JLabel proposalTitle = new JLabel("Proposal");
        proposalTitle.setFont(proposalTitle.getFont().deriveFont(Font.BOLD));
        proposalCard.add(proposalTitle, BorderLayout.NORTH);
        proposalCard.add(new JScrollPane(proposalText), BorderLayout.CENTER);
        JPanel proposalButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        applyButton.addActionListener(e -> controller.onApply());
        actions.add(applyButton);
        proposalButtons.add(applyButton);
        proposalButtons.add(button("Discard", () -> controller.onDiscard()));
        proposalCard.add(proposalButtons, BorderLayout.SOUTH);
        proposalCard.setVisible(false);

        JPanel bottom = new JPanel();
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.add(proposalCard);
        agentStatus.setForeground(new Color(0x4A7FC1));
        agentStatus.setAlignmentX(LEFT_ALIGNMENT);
        proposalCard.setAlignmentX(LEFT_ALIGNMENT);
        bottom.add(agentStatus);
        JPanel input = new JPanel(new BorderLayout(4, 4));
        input.setAlignmentX(LEFT_ALIGNMENT);
        input.add(chatInput, BorderLayout.CENTER);
        Runnable send = () -> {
            String message = chatInput.getText();
            if (!message.isBlank()) {
                chatInput.setText("");
                controller.onSend(message);
            }
        };
        chatInput.addActionListener(e -> send.run());
        actions.add(chatInput);
        input.add(button("Send", send), BorderLayout.EAST);
        bottom.add(input);
        chatPanel.add(bottom, BorderLayout.SOUTH);
    }

    private JButton button(String text, Runnable action) {
        JButton button = new JButton(text);
        button.addActionListener(e -> {
            if (controller != null) {
                action.run();
            }
        });
        actions.add(button);
        return button;
    }

    private void withSelectedTask(java.util.function.Consumer<String> action) {
        String id = selectedTaskId();
        if (id == null) {
            showError("Select a task first.");
        } else {
            action.accept(id);
        }
    }

    private static JTextArea readOnlyArea(String text) {
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        return area;
    }
}
