import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * TodoPanel 負責代辦事項的建立、編輯、刪除、勾選完成與提醒。
 */
public class TodoPanel extends JPanel {
    private static final DateTimeFormatter REMINDER_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[]{"完成", "內容", "提醒時間"}, 0) {
        @Override
        public Class<?> getColumnClass(int col) {
            return col == 0 ? Boolean.class : String.class;
        }
        @Override
        public boolean isCellEditable(int row, int col) {
            return col == 0;
        }
    };

    private final JTable todoTable;
    private final JTextField contentField = new JTextField();

    // 提醒時間相關元件
    private final JCheckBox reminderCheckBox = new JCheckBox("設定提醒時間");
    private final JSpinner yearSpinner;
    private final JSpinner monthSpinner;
    private final JSpinner daySpinner;
    private final JSpinner hourSpinner;
    private final JSpinner minuteSpinner;
    private final JPanel reminderPanel;

    private final JButton editButton = new JButton("編輯");
    private final JButton deleteButton = new JButton("刪除");
    private final List<TodoItem> todos;
    private final Runnable updateCallback;
    private final Timer reminderTimer;
    private final java.util.Set<Integer> remindedIds = new java.util.HashSet<>();

    public TodoPanel(List<TodoItem> todos, Runnable updateCallback) {
        this.todos = todos;
        this.updateCallback = updateCallback;

        // 初始化 Spinner
        LocalDateTime now = LocalDateTime.now();
        yearSpinner = new JSpinner(new SpinnerNumberModel(now.getYear(), 2020, 2099, 1));
        monthSpinner = new JSpinner(new SpinnerNumberModel(now.getMonthValue(), 1, 12, 1));
        daySpinner = new JSpinner(new SpinnerNumberModel(now.getDayOfMonth(), 1, 31, 1));
        hourSpinner = new JSpinner(new SpinnerNumberModel(now.getHour(), 0, 23, 1));
        minuteSpinner = new JSpinner(new SpinnerNumberModel(now.getMinute(), 0, 59, 1));

        monthSpinner.setEditor(new JSpinner.NumberEditor(monthSpinner, "00"));
        daySpinner.setEditor(new JSpinner.NumberEditor(daySpinner, "00"));
        hourSpinner.setEditor(new JSpinner.NumberEditor(hourSpinner, "00"));
        minuteSpinner.setEditor(new JSpinner.NumberEditor(minuteSpinner, "00"));

        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(12, 12, 12, 12));

        // 表格
        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setBorder(BorderFactory.createTitledBorder("代辦清單"));
        todoTable = new JTable(tableModel);
        todoTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        todoTable.setRowHeight(28);
        todoTable.getColumnModel().getColumn(0).setMaxWidth(45);
        todoTable.getColumnModel().getColumn(2).setPreferredWidth(150);

        // 勾選時更新完成狀態
        tableModel.addTableModelListener(e -> {
            if (e.getColumn() == 0) {
                int row = e.getFirstRow();
                if (row >= 0 && row < todos.size()) {
                    todos.get(row).setCompleted((boolean) tableModel.getValueAt(row, 0));
                    updateCallback.run();
                    todoTable.repaint();
                }
            }
        });

        // 完成的項目顯示刪除線
        todoTable.setDefaultRenderer(String.class, (table, value, isSelected, hasFocus, row, col) -> {
            JLabel label = new JLabel(value != null ? value.toString() : "");
            label.setOpaque(true);
            label.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            label.setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
            if (row < todos.size() && todos.get(row).isCompleted()) {
                label.setText("<html><strike>" + label.getText() + "</strike></html>");
                label.setForeground(Color.GRAY);
            }
            return label;
        });

        tablePanel.add(new JScrollPane(todoTable), BorderLayout.CENTER);
        add(tablePanel, BorderLayout.CENTER);

        // 右側表單
        JPanel formPanel = new JPanel();
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
        formPanel.setBorder(BorderFactory.createTitledBorder("操作區"));
        formPanel.setPreferredSize(new Dimension(220, 0));

        // 內容欄位
        JPanel contentPanel = new JPanel(new GridLayout(2, 1, 4, 4));
        contentPanel.add(new JLabel("代辦內容："));
        contentPanel.add(contentField);
        formPanel.add(contentPanel);
        formPanel.add(Box.createRigidArea(new Dimension(0, 8)));

        // 提醒時間選擇（預設隱藏）
        reminderPanel = new JPanel();
        reminderPanel.setLayout(new BoxLayout(reminderPanel, BoxLayout.Y_AXIS));
        reminderPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        reminderPanel.setVisible(false);

        // 提醒時間勾選
        reminderCheckBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        reminderCheckBox.addActionListener(e -> {
            reminderPanel.setVisible(reminderCheckBox.isSelected());
        });
        formPanel.add(reminderCheckBox);

        JLabel dateLabel = new JLabel("日期：");
        dateLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel dateRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        dateRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        yearSpinner.setPreferredSize(new Dimension(65, 25));
        monthSpinner.setPreferredSize(new Dimension(45, 25));
        daySpinner.setPreferredSize(new Dimension(45, 25));
        dateRow.add(yearSpinner);
        dateRow.add(new JLabel("/"));
        dateRow.add(monthSpinner);
        dateRow.add(new JLabel("/"));
        dateRow.add(daySpinner);

        JLabel timeLabel = new JLabel("時間：");
        timeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel timeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        timeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        hourSpinner.setPreferredSize(new Dimension(45, 25));
        minuteSpinner.setPreferredSize(new Dimension(45, 25));
        timeRow.add(hourSpinner);
        timeRow.add(new JLabel(":"));
        timeRow.add(minuteSpinner);

        reminderPanel.add(dateLabel);
        reminderPanel.add(dateRow);
        reminderPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        reminderPanel.add(timeLabel);
        reminderPanel.add(timeRow);
        formPanel.add(reminderPanel);
        formPanel.add(Box.createRigidArea(new Dimension(0, 8)));

        // 新增按鈕
        JButton addButton = new JButton("新增代辦");
        addButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        addButton.addActionListener(e -> addTodo());
        formPanel.add(addButton);
        formPanel.add(Box.createRigidArea(new Dimension(0, 4)));

        // 編輯/刪除按鈕
        JPanel actionButtons = new JPanel(new GridLayout(1, 2, 8, 0));
        editButton.addActionListener(e -> editSelectedTodo());
        deleteButton.addActionListener(e -> deleteSelectedTodo());
        editButton.setEnabled(false);
        deleteButton.setEnabled(false);
        actionButtons.add(editButton);
        actionButtons.add(deleteButton);
        formPanel.add(actionButtons);

        todoTable.getSelectionModel().addListSelectionListener(e -> {
            boolean selected = todoTable.getSelectedRow() >= 0;
            editButton.setEnabled(selected);
            deleteButton.setEnabled(selected);
        });

        add(formPanel, BorderLayout.EAST);

        // 提醒計時器
        reminderTimer = new Timer(60_000, e -> scanReminders());
        reminderTimer.setInitialDelay(0);
        reminderTimer.start();

        updateTable();
    }

    private void addTodo() {
        String content = contentField.getText().trim();
        if (content.isEmpty()) {
            JOptionPane.showMessageDialog(this, "請輸入代辦內容。", "輸入錯誤", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String reminderTime = null;
        if (reminderCheckBox.isSelected()) {
            int year = (int) yearSpinner.getValue();
            int month = (int) monthSpinner.getValue();
            int day = (int) daySpinner.getValue();
            int hour = (int) hourSpinner.getValue();
            int minute = (int) minuteSpinner.getValue();
            try {
                LocalDateTime.of(year, month, day, hour, minute);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "日期不合法，請重新確認。", "日期錯誤", JOptionPane.WARNING_MESSAGE);
                return;
            }
            reminderTime = String.format("%04d-%02d-%02d %02d:%02d", year, month, day, hour, minute);
        }

        todos.add(new TodoItem(getNextId(), content, reminderTime));
        contentField.setText("");
        reminderCheckBox.setSelected(false);
        reminderPanel.setVisible(false);
        updateTable();
        updateCallback.run();
    }

    private void editSelectedTodo() {
        int row = todoTable.getSelectedRow();
        if (row < 0) return;
        TodoItem todo = todos.get(row);

        // 編輯內容
        JTextField editContent = new JTextField(todo.getContent());

        // 提醒時間
        JCheckBox editReminderCheck = new JCheckBox("設定提醒時間");
        editReminderCheck.setPreferredSize(new Dimension(250, 60));
        LocalDateTime reminderDT = null;
        if (todo.getReminderTime() != null) {
            try {
                reminderDT = LocalDateTime.parse(todo.getReminderTime(), REMINDER_FMT);
                editReminderCheck.setSelected(true);
            } catch (DateTimeParseException ignored) {}
        }

        LocalDateTime base = reminderDT != null ? reminderDT : LocalDateTime.now();
        JSpinner eYear = new JSpinner(new SpinnerNumberModel(base.getYear(), 2020, 2099, 1));
        JSpinner eMonth = new JSpinner(new SpinnerNumberModel(base.getMonthValue(), 1, 12, 1));
        JSpinner eDay = new JSpinner(new SpinnerNumberModel(base.getDayOfMonth(), 1, 31, 1));
        JSpinner eHour = new JSpinner(new SpinnerNumberModel(base.getHour(), 0, 23, 1));
        JSpinner eMinute = new JSpinner(new SpinnerNumberModel(base.getMinute(), 0, 59, 1));

        eMonth.setEditor(new JSpinner.NumberEditor(eMonth, "00"));
        eDay.setEditor(new JSpinner.NumberEditor(eDay, "00"));
        eHour.setEditor(new JSpinner.NumberEditor(eHour, "00"));
        eMinute.setEditor(new JSpinner.NumberEditor(eMinute, "00"));

        JPanel dateRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        eYear.setPreferredSize(new Dimension(80, 22));
        eMonth.setPreferredSize(new Dimension(80, 22));
        eDay.setPreferredSize(new Dimension(80, 22));
        dateRow.add(eYear); dateRow.add(new JLabel("/")); 
        dateRow.add(eMonth); dateRow.add(new JLabel("/"));
        dateRow.add(eDay);

        JPanel timeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        timeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        eHour.setPreferredSize(new Dimension(80, 22));
        eMinute.setPreferredSize(new Dimension(80, 22));
        timeRow.add(eHour); timeRow.add(new JLabel(":"));
        timeRow.add(eMinute);

        JLabel editDateLabel = new JLabel("日期：");
        editDateLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel editTimeLabel = new JLabel("時間：");
        editTimeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel editReminderPanel = new JPanel();
        editReminderPanel.setLayout(new BoxLayout(editReminderPanel, BoxLayout.Y_AXIS));
        editReminderPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        editReminderPanel.add(editDateLabel);
        editReminderPanel.add(dateRow);
        editReminderPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        editReminderPanel.add(editTimeLabel);
        editReminderPanel.add(timeRow);
        editReminderPanel.setVisible(editReminderCheck.isSelected());
        editReminderCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        editReminderCheck.addActionListener(e -> editReminderPanel.setVisible(editReminderCheck.isSelected()));

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(new JLabel("編輯內容："));
        panel.add(editContent);
        panel.add(Box.createRigidArea(new Dimension(0, 8)));
        panel.add(editReminderCheck);
        panel.add(editReminderPanel);

        int result = JOptionPane.showConfirmDialog(this, panel, "編輯代辦", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            String newContent = editContent.getText().trim();
            if (newContent.isEmpty()) {
                JOptionPane.showMessageDialog(this, "內容不可為空。", "編輯錯誤", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String newReminder = null;
            if (editReminderCheck.isSelected()) {
                int y = (int) eYear.getValue();
                int mo = (int) eMonth.getValue();
                int d = (int) eDay.getValue();
                int h = (int) eHour.getValue();
                int mi = (int) eMinute.getValue();
                try {
                    LocalDateTime.of(y, mo, d, h, mi);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(this, "日期不合法。", "日期錯誤", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                newReminder = String.format("%04d-%02d-%02d %02d:%02d", y, mo, d, h, mi);
            }
            todo.setContent(newContent);
            todo.setReminderTime(newReminder);
            remindedIds.remove(todo.getId());
            updateTable();
            updateCallback.run();
            maybeShowReminder(todo);
        }
    }

    private void deleteSelectedTodo() {
        int row = todoTable.getSelectedRow();
        if (row < 0) return;
        remindedIds.remove(todos.get(row).getId());
        todos.remove(row);
        updateTable();
        updateCallback.run();
    }

    public void updateTable() {
        todos.sort((a, b) -> {
            String ta = a.getReminderTime();
            String tb = b.getReminderTime();
            if (ta == null && tb == null) return 0;
            if (ta == null) return 1;
            if (tb == null) return -1;
            return ta.compareTo(tb);
        });
        tableModel.setRowCount(0);
        for (TodoItem todo : todos) {
            tableModel.addRow(new Object[]{
                    todo.isCompleted(),
                    todo.getContent(),
                    todo.getReminderTime() != null ? todo.getReminderTime() : ""
            });
        }
    }

    private void maybeShowReminder(TodoItem todo) {
        if (todo.getReminderTime() == null) return;
        try {
            LocalDateTime target = LocalDateTime.parse(todo.getReminderTime(), REMINDER_FMT);
            long diff = java.time.Duration.between(LocalDateTime.now(), target).toMinutes();
            if (diff >= 0 && diff <= 240 && !remindedIds.contains(todo.getId())) {
                remindedIds.add(todo.getId());
                JOptionPane.showMessageDialog(this,
                        String.format("提醒：「%s」將在四小時內到期。", todo.getContent()),
                        "代辦提醒", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (DateTimeParseException ignored) {}
    }

    private void scanReminders() {
        for (TodoItem todo : todos) maybeShowReminder(todo);
    }

    private int getNextId() {
        return todos.isEmpty() ? 1 : todos.stream().mapToInt(TodoItem::getId).max().orElse(0) + 1;
    }
}