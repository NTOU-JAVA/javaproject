import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * TodoPanel 負責代辦的建立、編輯、刪除與列表顯示。
 */
public class TodoPanel extends JPanel {
    private final DefaultTableModel tableModel = new DefaultTableModel(new Object[]{"ID", "日期", "時間", "內容"}, 0);
    private final JTextField contentField = new JTextField();
    private final JTextField dateField = new JTextField(LocalDate.now().toString());
    private final JTextField timeField = new JTextField("09:00");
    private final JButton editButton = new JButton("編輯");
    private final JButton deleteButton = new JButton("刪除");
    private final JTable todoTable;
    private final List<TodoItem> todos;
    private final Timer reminderTimer;
    private final java.util.Set<Integer> remindedTodoIds = new java.util.HashSet<>();
    private final Runnable updateCallback;
    private final Runnable switchToCalendarCallback;

    /**
     * 建構代辦面板，並建立表格與操作表單元件。
     *
     * @param todos 代辦項目列表
     * @param updateCallback 更新 UI 的回呼
     */
    public TodoPanel(List<TodoItem> todos, Runnable updateCallback) {
        this(todos, updateCallback, null);
    }

    public TodoPanel(List<TodoItem> todos, Runnable updateCallback, Runnable switchToCalendarCallback) {
        this.todos = todos;
        this.updateCallback = updateCallback;
        this.switchToCalendarCallback = switchToCalendarCallback;

        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(12, 12, 12, 12));

        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setBorder(BorderFactory.createTitledBorder("代辦清單"));
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
        fieldPanel.add(new JLabel("代辦內容："));
        fieldPanel.add(contentField);
        fieldPanel.add(new JLabel("日期 (YYYY-MM-DD)："));
        fieldPanel.add(dateField);
        fieldPanel.add(new JLabel("時間 (HH:mm)："));
        fieldPanel.add(timeField);
        formPanel.add(fieldPanel);

        JButton addButton = new JButton("新增代辦");
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

    /**
     * 新增一筆代辦項目，並檢查內容與時間格式是否正確。
     */
    private void addTodo() {
        String content = contentField.getText().trim();
        String date = dateField.getText().trim();
        String time = timeField.getText().trim();
        if (content.isEmpty()) {
            JOptionPane.showMessageDialog(this, "請輸入代辦內容。", "輸入錯誤", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!isValidDate(date)) {
            JOptionPane.showMessageDialog(this, "請輸入正確的日期格式，範例：2026-04-20。", "日期格式錯誤", JOptionPane.WARNING_MESSAGE);
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
    }

    /**
     * 編輯目前所選中的代辦項目。
     */
    private void editSelectedTodo() {
        int selectedRow = todoTable.getSelectedRow();
        if (selectedRow >= 0) {
            int id = (int) tableModel.getValueAt(selectedRow, 0);
            TodoItem todo = todos.stream().filter(t -> t.getId() == id).findFirst().orElse(null);
            if (todo != null) {
                JTextField editContentField = new JTextField(todo.getContent());
                JTextField editDateField = new JTextField(todo.getDate());
                JTextField editTimeField = new JTextField(todo.getTime());
                JPanel editPanel = new JPanel(new GridLayout(6, 1, 4, 4));
                editPanel.add(new JLabel("編輯內容："));
                editPanel.add(editContentField);
                editPanel.add(new JLabel("編輯日期 (YYYY-MM-DD)："));
                editPanel.add(editDateField);
                editPanel.add(new JLabel("編輯時間 (HH:mm)："));
                editPanel.add(editTimeField);

                int result = JOptionPane.showConfirmDialog(this, editPanel, "編輯代辦", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
                if (result == JOptionPane.OK_OPTION) {
                    String newContent = editContentField.getText().trim();
                    String newDate = editDateField.getText().trim();
                    String newTime = editTimeField.getText().trim();
                    if (newContent.isEmpty()) {
                        JOptionPane.showMessageDialog(this, "代辦內容不可為空。", "編輯錯誤", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    if (!isValidDate(newDate)) {
                        JOptionPane.showMessageDialog(this, "請輸入正確的日期格式，範例：2026-04-20。", "日期格式錯誤", JOptionPane.WARNING_MESSAGE);
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
                }
            }
        }
    }

    /**
     * 刪除目前所選中的代辦項目。
     */
    private void deleteSelectedTodo() {
        int selectedRow = todoTable.getSelectedRow();
        if (selectedRow >= 0) {
            int id = (int) tableModel.getValueAt(selectedRow, 0);
            todos.removeIf(t -> t.getId() == id);
            remindedTodoIds.remove(id);
            updateTable();
            updateCallback.run();
            if (switchToCalendarCallback != null) {
                switchToCalendarCallback.run();
            }
        }
    }

    /**
     * 排序代辦項目後更新表格顯示。
     */
    public void updateTable() {
        sortTodosByDateTime();
        tableModel.setRowCount(0);
        for (TodoItem todo : todos) {
            tableModel.addRow(new Object[]{todo.getId(), todo.getDate(), todo.getTime(), todo.getContent()});
        }
    }

    /**
     * 依據代辦日期與時間排序代辦項目。
     */
    private void sortTodosByDateTime() {
        todos.sort((a, b) -> {
            LocalDate dateA = parseTodoDate(a.getDate());
            LocalDate dateB = parseTodoDate(b.getDate());
            if (dateA != null && dateB != null) {
                int dateCompare = dateA.compareTo(dateB);
                if (dateCompare != 0) {
                    return dateCompare;
                }
            } else if (dateA != null) {
                return -1;
            } else if (dateB != null) {
                return 1;
            }
            LocalTime timeA = LocalTime.parse(a.getTime(), DateTimeFormatter.ofPattern("HH:mm"));
            LocalTime timeB = LocalTime.parse(b.getTime(), DateTimeFormatter.ofPattern("HH:mm"));
            return timeA.compareTo(timeB);
        });
    }

    private LocalDate parseTodoDate(String dateText) {
        try {
            return LocalDate.parse(dateText, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            DayOfWeek targetDay = parseDayOfWeek(dateText);
            if (targetDay == null) {
                return null;
            }
            LocalDate today = LocalDate.now();
            int daysUntil = (targetDay.getValue() - today.getDayOfWeek().getValue() + 7) % 7;
            return today.plusDays(daysUntil);
        }
    }

    /**
     * 驗證時間格式是否為 HH:mm。
     *
     * @param time 時間字串
     * @return 若格式正確回傳 true
     */
    private boolean isValidDate(String date) {
        try {
            LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private boolean isValidTime(String time) {
        try {
            LocalTime.parse(time, DateTimeFormatter.ofPattern("HH:mm"));
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private int getNextId() {
        return todos.isEmpty() ? 1 : todos.stream().mapToInt(TodoItem::getId).max().orElse(0) + 1;
    }

    /**
     * 若代辦時間距離現在四小時內，則顯示提醒訊息。
     *
     * @param todo 要檢查的代辦項目
     */
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
                    String.format("提醒：代辦「%s」於 %s %s 將在四小時內到達。", todo.getContent(), todo.getDate(), todo.getTime()),
                    "即時提醒", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * 將代辦項目的日期與時間解析為 LocalDateTime。
     *
     * @param todo 代辦項目
     * @return 解析後的日期時間，若格式錯誤回傳 null
     */
    private LocalDateTime parseTodoDateTime(TodoItem todo) {
        try {
            LocalDate date;
            boolean weekdayFormat = false;
            try {
                date = LocalDate.parse(todo.getDate(), DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException e) {
                DayOfWeek targetDay = parseDayOfWeek(todo.getDate());
                if (targetDay == null) {
                    return null;
                }
                LocalDate today = LocalDate.now();
                int daysUntil = (targetDay.getValue() - today.getDayOfWeek().getValue() + 7) % 7;
                date = today.plusDays(daysUntil);
                weekdayFormat = true;
            }
            LocalTime time = LocalTime.parse(todo.getTime(), DateTimeFormatter.ofPattern("HH:mm"));
            LocalDateTime targetDateTime = LocalDateTime.of(date, time);
            if (weekdayFormat && targetDateTime.isBefore(LocalDateTime.now())) {
                targetDateTime = targetDateTime.plusDays(7);
            }
            return targetDateTime;
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * 定期掃描所有代辦項目，若有代辦進入四小時提醒範圍則顯示通知。
     */
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
                        String.format("提醒：代辦「%s」於 %s %s 將在四小時內到達。", todo.getContent(), todo.getDate(), todo.getTime()),
                        "即時提醒", JOptionPane.INFORMATION_MESSAGE);
            }
        }
    }

    /**
     * 將中文星期字串轉為 DayOfWeek 列舉值。
     *
     * @param day 中文星期字串
     * @return 轉換後的 DayOfWeek，若無法轉換回傳 null
     */
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