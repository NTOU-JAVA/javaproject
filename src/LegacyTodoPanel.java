import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * LegacyTodoPanel 保留上一個版本的任務管理頁面，使用星期格式日期與行事曆切換。
 */
public class LegacyTodoPanel extends JPanel {
    private static final String[] WEEK_DAYS = {
        "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"
    };

    private final DefaultTableModel tableModel = new DefaultTableModel(new Object[]{"ID", "日期", "時間", "內容"}, 0);
    private final JTextField contentField = new JTextField();
    private final JComboBox<String> dayCombo;
    private final JTextField timeField = new JTextField("09:00");
    private final JButton editButton = new JButton("編輯");
    private final JButton deleteButton = new JButton("刪除");
    private final JTable todoTable;
    private final List<TodoItem> todos;
    private final Timer reminderTimer;
    private final java.util.Set<Integer> remindedTodoIds = new java.util.HashSet<>();
    private final Runnable updateCallback;
    private final Runnable switchToCalendarCallback;

    public LegacyTodoPanel(List<TodoItem> todos, Runnable updateCallback, Runnable switchToCalendarCallback) {
        this.todos = todos;
        this.updateCallback = updateCallback;
        this.switchToCalendarCallback = switchToCalendarCallback;
        dayCombo = new JComboBox<>(WEEK_DAYS);

        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(12, 12, 12, 12));

        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setBorder(BorderFactory.createTitledBorder("任務清單"));
        todoTable = new JTable(tableModel);
        todoTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        todoTable.setRowHeight(28);
        todoTable.getColumnModel().getColumn(0).setMaxWidth(50);
        JScrollPane tableScroll = new JScrollPane(todoTable);
        tablePanel.add(tableScroll, BorderLayout.CENTER);
        add(tablePanel, BorderLayout.CENTER);

        JPanel formPanel = new JPanel();
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
        formPanel.setBorder(BorderFactory.createTitledBorder("操作區"));

        JPanel fieldPanel = new JPanel(new GridLayout(6, 1, 8, 8));
        fieldPanel.add(new JLabel("任務內容："));
        fieldPanel.add(contentField);
        fieldPanel.add(new JLabel("日期："));
        fieldPanel.add(dayCombo);
        fieldPanel.add(new JLabel("時間 (HH:mm)："));
        fieldPanel.add(timeField);
        formPanel.add(fieldPanel);

        JButton addButton = new JButton("新增任務");
        addButton.addActionListener(e -> addTodo());
        addButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        formPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        formPanel.add(addButton);

        JPanel actionButtons = new JPanel(new GridLayout(1, 2, 8, 0));
        editButton.addActionListener(e -> editSelectedTodo());
        deleteButton.addActionListener(e -> deleteSelectedTodo());
        editButton.setEnabled(false);
        deleteButton.setEnabled(false);
        actionButtons.add(editButton);
        actionButtons.add(deleteButton);
        formPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        formPanel.add(actionButtons);

        todoTable.getSelectionModel().addListSelectionListener(e -> {
            boolean selected = todoTable.getSelectedRow() >= 0;
            editButton.setEnabled(selected);
            deleteButton.setEnabled(selected);
        });

        reminderTimer = new Timer(60_000, e -> scanReminders());
        reminderTimer.setInitialDelay(0);
        reminderTimer.start();

        add(formPanel, BorderLayout.EAST);
    }

    private void addTodo() {
        String content = contentField.getText().trim();
        String date = (String) dayCombo.getSelectedItem();
        String time = timeField.getText().trim();
        if (content.isEmpty()) {
            JOptionPane.showMessageDialog(this, "請輸入任務內容。", "輸入錯誤", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!isValidTime(time)) {
            JOptionPane.showMessageDialog(this, "請輸入正確的時間格式，範例：09:30。", "時間格式錯誤", JOptionPane.WARNING_MESSAGE);
            return;
        }
        TodoItem todo = new TodoItem(getNextId(), date, time, content);
        todos.add(todo);
        updateTable();
        contentField.setText("");
        timeField.setText("09:00");
        updateCallback.run();
        maybeShowReminder(todo);
        if (switchToCalendarCallback != null) {
            switchToCalendarCallback.run();
        }
    }

    private void editSelectedTodo() {
        int selectedRow = todoTable.getSelectedRow();
        if (selectedRow >= 0) {
            int id = (int) tableModel.getValueAt(selectedRow, 0);
            TodoItem todo = todos.stream().filter(t -> t.getId() == id).findFirst().orElse(null);
            if (todo != null) {
                JTextField editContentField = new JTextField(todo.getContent());
                JComboBox<String> editDayCombo = new JComboBox<>(WEEK_DAYS);
                editDayCombo.setSelectedItem(todo.getDate());
                JTextField editTimeField = new JTextField(todo.getTime());
                JPanel editPanel = new JPanel(new GridLayout(6, 1, 4, 4));
                editPanel.add(new JLabel("編輯內容："));
                editPanel.add(editContentField);
                editPanel.add(new JLabel("編輯日期："));
                editPanel.add(editDayCombo);
                editPanel.add(new JLabel("編輯時間 (HH:mm)："));
                editPanel.add(editTimeField);

                int result = JOptionPane.showConfirmDialog(this, editPanel, "編輯任務", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
                if (result == JOptionPane.OK_OPTION) {
                    String newContent = editContentField.getText().trim();
                    String newDate = (String) editDayCombo.getSelectedItem();
                    String newTime = editTimeField.getText().trim();
                    if (newContent.isEmpty()) {
                        JOptionPane.showMessageDialog(this, "任務內容不可為空。", "編輯錯誤", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    if (!isValidTime(newTime)) {
                        JOptionPane.showMessageDialog(this, "請輸入正確的時間格式，範例：09:30。", "時間格式錯誤", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    todo.setContent(newContent);
                    todo.setDate(newDate);
                    todo.setTime(newTime);
                    remindedTodoIds.remove(todo.getId());
                    updateTable();
                    updateCallback.run();
                    maybeShowReminder(todo);
                    if (switchToCalendarCallback != null) {
                        switchToCalendarCallback.run();
                    }
                }
            }
        }
    }

    private void deleteSelectedTodo() {
        int selectedRow = todoTable.getSelectedRow();
        if (selectedRow >= 0) {
            int id = (int) tableModel.getValueAt(selectedRow, 0);
            todos.removeIf(t -> t.getId() == id);
            remindedTodoIds.remove(id);
            updateTable();
            updateCallback.run();
        }
    }

    public void updateTable() {
        sortTodosByDateTime();
        tableModel.setRowCount(0);
        for (TodoItem todo : todos) {
            tableModel.addRow(new Object[]{todo.getId(), todo.getDate(), todo.getTime(), todo.getContent()});
        }
    }

    private void sortTodosByDateTime() {
        todos.sort((a, b) -> {
            DayOfWeek dayA = parseDayOfWeek(a.getDate());
            DayOfWeek dayB = parseDayOfWeek(b.getDate());
            if (dayA != null && dayB != null) {
                int dayCompare = Integer.compare(dayA.getValue(), dayB.getValue());
                if (dayCompare != 0) {
                    return dayCompare;
                }
            } else if (dayA != null) {
                return -1;
            } else if (dayB != null) {
                return 1;
            }
            LocalTime timeA = LocalTime.parse(a.getTime(), DateTimeFormatter.ofPattern("HH:mm"));
            LocalTime timeB = LocalTime.parse(b.getTime(), DateTimeFormatter.ofPattern("HH:mm"));
            return timeA.compareTo(timeB);
        });
    }

    private int getNextId() {
        return todos.isEmpty() ? 1 : todos.stream().mapToInt(TodoItem::getId).max().orElse(0) + 1;
    }

    private boolean isValidTime(String time) {
        try {
            LocalTime.parse(time, DateTimeFormatter.ofPattern("HH:mm"));
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private void maybeShowReminder(TodoItem todo) {
        LocalDateTime targetDateTime = parseTodoDateTime(todo);
        if (targetDateTime == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        long diffMinutes = Duration.between(now, targetDateTime).toMinutes();
        if (diffMinutes >= 0 && diffMinutes <= 240 && !remindedTodoIds.contains(todo.getId())) {
            remindedTodoIds.add(todo.getId());
            JOptionPane.showMessageDialog(this,
                    String.format("提醒：任務「%s」於 %s %s 將在四小時內到達。", todo.getContent(), todo.getDate(), todo.getTime()),
                    "即時提醒", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private LocalDateTime parseTodoDateTime(TodoItem todo) {
        try {
            DayOfWeek targetDay = parseDayOfWeek(todo.getDate());
            if (targetDay == null) {
                return null;
            }
            LocalDate today = LocalDate.now();
            int daysUntil = (targetDay.getValue() - today.getDayOfWeek().getValue() + 7) % 7;
            LocalDate targetDate = today.plusDays(daysUntil);
            LocalTime time = LocalTime.parse(todo.getTime(), DateTimeFormatter.ofPattern("HH:mm"));
            LocalDateTime targetDateTime = LocalDateTime.of(targetDate, time);
            if (targetDateTime.isBefore(LocalDateTime.now())) {
                targetDateTime = targetDateTime.plusDays(7);
            }
            return targetDateTime;
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private void scanReminders() {
        LocalDateTime now = LocalDateTime.now();
        for (TodoItem todo : todos) {
            LocalDateTime targetDateTime = parseTodoDateTime(todo);
            if (targetDateTime == null) {
                continue;
            }
            long diffMinutes = Duration.between(now, targetDateTime).toMinutes();
            if (diffMinutes >= 0 && diffMinutes <= 240 && !remindedTodoIds.contains(todo.getId())) {
                remindedTodoIds.add(todo.getId());
                JOptionPane.showMessageDialog(this,
                        String.format("提醒：任務「%s」於 %s %s 將在四小時內到達。", todo.getContent(), todo.getDate(), todo.getTime()),
                        "即時提醒", JOptionPane.INFORMATION_MESSAGE);
            }
        }
    }

    private DayOfWeek parseDayOfWeek(String day) {
        switch (day) {
            case "星期一": return DayOfWeek.MONDAY;
            case "星期二": return DayOfWeek.TUESDAY;
            case "星期三": return DayOfWeek.WEDNESDAY;
            case "星期四": return DayOfWeek.THURSDAY;
            case "星期五": return DayOfWeek.FRIDAY;
            case "星期六": return DayOfWeek.SATURDAY;
            case "星期日": return DayOfWeek.SUNDAY;
            default: return null;
        }
    }
}
