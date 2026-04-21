import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * CalendarPanel 顯示一週七天的任務行事曆，支援週切換與新增任務。
 */
public class CalendarPanel extends JPanel {
    private static final String[] WEEK_DAY_NAMES = {"日", "一", "二", "三", "四", "五", "六"};
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MM/dd");
    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final JPanel[] dayPanels = new JPanel[7];
    private final JLabel[] dayLabels = new JLabel[7];
    private final JLabel weekLabel = new JLabel("", SwingConstants.CENTER);
    private LocalDate weekStart;
    private final List<Task> tasks;

    // 表單元件
    private final JSpinner yearSpinner;
    private final JSpinner monthSpinner;
    private final JSpinner daySpinner;
    private final JSpinner hourSpinner;
    private final JSpinner minuteSpinner;
    private final JTextField contentField = new JTextField();
    private final JCheckBox importantCheckBox = new JCheckBox("重要任務");
    private final List<JList<Task>> dayTaskLists = new ArrayList<>();
    private Task selectedTask = null;
    private int selectedDayIndex = -1;
    private final Timer reminderTimer;
    private final Set<Integer> remindedIds = new HashSet<>();

    public CalendarPanel(List<Task> tasks) {
        this.tasks = tasks;

        // 計算本週星期日
        LocalDate today = LocalDate.now();
        int dayOfWeek = today.getDayOfWeek().getValue() % 7; // 星期日=0, 一=1, ..., 六=6
        this.weekStart = today.minusDays(dayOfWeek);

        // 初始化 Spinner
        LocalDate now = LocalDate.now();
        yearSpinner = new JSpinner(new SpinnerNumberModel(now.getYear(), 2020, 2099, 1));
        monthSpinner = new JSpinner(new SpinnerNumberModel(now.getMonthValue(), 1, 12, 1));
        daySpinner = new JSpinner(new SpinnerNumberModel(now.getDayOfMonth(), 1, 31, 1));
        hourSpinner = new JSpinner(new SpinnerNumberModel(9, 0, 23, 1));
        minuteSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 59, 1));

        // 格式化 Spinner 顯示兩位數
        monthSpinner.setEditor(new JSpinner.NumberEditor(monthSpinner, "00"));
        daySpinner.setEditor(new JSpinner.NumberEditor(daySpinner, "00"));
        hourSpinner.setEditor(new JSpinner.NumberEditor(hourSpinner, "00"));
        minuteSpinner.setEditor(new JSpinner.NumberEditor(minuteSpinner, "00"));

        setLayout(new BorderLayout(8, 8));
        setBorder(new EmptyBorder(8, 8, 8, 8));

        // 上方週切換列
        JPanel navPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton prevButton = new JButton("← 上一週");
        JButton nextButton = new JButton("下一週 →");
        weekLabel.setPreferredSize(new Dimension(200, 30));
        weekLabel.setFont(weekLabel.getFont().deriveFont(Font.BOLD, 14f));
        prevButton.addActionListener(e -> {
            weekStart = weekStart.minusWeeks(1);
            updateCalendar();
        });
        nextButton.addActionListener(e -> {
            weekStart = weekStart.plusWeeks(1);
            updateCalendar();
        });
        navPanel.add(prevButton);
        navPanel.add(weekLabel);
        navPanel.add(nextButton);
        add(navPanel, BorderLayout.NORTH);

        // 七天格子
        JPanel gridPanel = new JPanel(new GridLayout(1, 7, 6, 6));
        for (int i = 0; i < 7; i++) {
            JPanel dayPanel = new JPanel(new BorderLayout());
            dayPanel.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
            dayPanel.setBackground(new Color(250, 250, 250));

            dayLabels[i] = new JLabel("", SwingConstants.CENTER);
            dayLabels[i].setFont(dayLabels[i].getFont().deriveFont(Font.BOLD, 12f));
            dayLabels[i].setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
            dayPanel.add(dayLabels[i], BorderLayout.NORTH);

            JList<Task> taskList = new JList<>(new DefaultListModel<>());
            taskList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            taskList.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            taskList.setOpaque(true);
            taskList.setBackground(new Color(250, 250, 250));
            taskList.setCellRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                              boolean isSelected, boolean cellHasFocus) {
                    JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    if (value instanceof Task) {
                        Task task = (Task) value;
                        String prefix = task.isImportant() ? "★ " : "• ";
                        label.setText(prefix + task.getTime() + " " + task.getContent());
                        label.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
                    }
                    return label;
                }
            });
            final int dayIndex = i;
            taskList.addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting()) {
                    Task selected = taskList.getSelectedValue();
                    if (selected != null) {
                        selectedTask = selected;
                        selectedDayIndex = dayIndex;
                        for (int j = 0; j < dayTaskLists.size(); j++) {
                            if (j != dayIndex) {
                                dayTaskLists.get(j).clearSelection();
                            }
                        }
                    } else if (selectedDayIndex == dayIndex) {
                        selectedTask = null;
                        selectedDayIndex = -1;
                    }
                }
            });
            dayTaskLists.add(taskList);
            JScrollPane scroll = new JScrollPane(taskList);
            scroll.setBorder(BorderFactory.createEmptyBorder());
            dayPanel.add(scroll, BorderLayout.CENTER);

            dayPanels[i] = dayPanel;
            gridPanel.add(dayPanel);
        }
        add(gridPanel, BorderLayout.CENTER);

        // 右側新增任務表單
        JPanel formPanel = new JPanel();
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
        formPanel.setBorder(BorderFactory.createTitledBorder("新增任務"));
        formPanel.setPreferredSize(new Dimension(220, 0));

        // 任務內容
        JLabel contentLabel = new JLabel("任務內容：");
        contentLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        formPanel.add(contentLabel);
        formPanel.add(contentField);
        formPanel.add(Box.createRigidArea(new Dimension(0, 8)));

        // 日期選擇
        JLabel dateLabel = new JLabel("日期：");
        dateLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel datePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        datePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        yearSpinner.setPreferredSize(new Dimension(65, 25));
        monthSpinner.setPreferredSize(new Dimension(45, 25));
        daySpinner.setPreferredSize(new Dimension(45, 25));
        datePanel.add(yearSpinner);
        datePanel.add(new JLabel("/"));
        datePanel.add(monthSpinner);
        datePanel.add(new JLabel("/"));
        datePanel.add(daySpinner);
        formPanel.add(dateLabel);
        formPanel.add(datePanel);
        formPanel.add(Box.createRigidArea(new Dimension(0, 8)));

        // 時間選擇
        JLabel timeLabel = new JLabel("時間：");
        timeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel timePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        timePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        hourSpinner.setPreferredSize(new Dimension(45, 25));
        minuteSpinner.setPreferredSize(new Dimension(45, 25));
        timePanel.add(hourSpinner);
        timePanel.add(new JLabel(":"));
        timePanel.add(minuteSpinner);
        formPanel.add(timeLabel);
        formPanel.add(timePanel);
        formPanel.add(Box.createRigidArea(new Dimension(0, 8)));

        // 重要性設定
        importantCheckBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        formPanel.add(importantCheckBox);
        formPanel.add(Box.createRigidArea(new Dimension(0, 8)));

        JButton addButton = new JButton("新增任務");
        addButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        addButton.addActionListener(e -> addTask());
        formPanel.add(addButton);
        formPanel.add(Box.createRigidArea(new Dimension(0, 4)));

        // 編輯按鈕
        JButton editButton = new JButton("編輯");
        editButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        editButton.addActionListener(e -> editSelectedTask());
        formPanel.add(editButton);
        formPanel.add(Box.createRigidArea(new Dimension(0, 4)));

        // 刪除按鈕
        JButton deleteButton = new JButton("刪除");
        deleteButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        deleteButton.addActionListener(e -> deleteSelectedTask());
        formPanel.add(deleteButton);

        add(formPanel, BorderLayout.EAST);

        updateCalendar();


        // 提醒計時器
        reminderTimer = new Timer(60_000, e -> scanReminders());
        reminderTimer.setInitialDelay(0);
        reminderTimer.start();
    }

    private void addTask() {
        String content = contentField.getText().trim();
        if (content.isEmpty()) {
            JOptionPane.showMessageDialog(this, "請輸入任務內容。", "輸入錯誤", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int year = (int) yearSpinner.getValue();
        int month = (int) monthSpinner.getValue();
        int day = (int) daySpinner.getValue();
        int hour = (int) hourSpinner.getValue();
        int minute = (int) minuteSpinner.getValue();

        // 驗證日期是否合法
        try {
            LocalDate.of(year, month, day);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "日期不合法，請重新確認。", "日期錯誤", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String date = String.format("%04d-%02d-%02d", year, month, day);
        String time = String.format("%02d:%02d", hour, minute);

        int nextId = tasks.isEmpty() ? 1 : tasks.stream().mapToInt(Task::getId).max().orElse(0) + 1;
        Task newTask = new Task(nextId, date, time, content);
        newTask.setImportant(importantCheckBox.isSelected());
        tasks.add(newTask);

        contentField.setText("");
        importantCheckBox.setSelected(false);
        updateCalendar();
    }

    private void editSelectedTask() {
        if (selectedTask == null) {
            JOptionPane.showMessageDialog(this, "請先點選要編輯的任務。", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // 編輯內容
        JTextField editContent = new JTextField(selectedTask.getContent());

        // 日期
        LocalDate reminderDT = LocalDate.parse(selectedTask.getDate());
        JSpinner eYear = new JSpinner(new SpinnerNumberModel(reminderDT.getYear(), 2020, 2099, 1));
        JSpinner eMonth = new JSpinner(new SpinnerNumberModel(reminderDT.getMonthValue(), 1, 12, 1));
        JSpinner eDay = new JSpinner(new SpinnerNumberModel(reminderDT.getDayOfMonth(), 1, 31, 1));
        JSpinner eHour = new JSpinner(new SpinnerNumberModel(Integer.parseInt(selectedTask.getTime().split(":")[0]), 0, 23, 1));
        JSpinner eMinute = new JSpinner(new SpinnerNumberModel(Integer.parseInt(selectedTask.getTime().split(":")[1]), 0, 59, 1));

        eMonth.setEditor(new JSpinner.NumberEditor(eMonth, "00"));
        eDay.setEditor(new JSpinner.NumberEditor(eDay, "00"));
        eHour.setEditor(new JSpinner.NumberEditor(eHour, "00"));
        eMinute.setEditor(new JSpinner.NumberEditor(eMinute, "00"));

        JPanel dateRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        eYear.setPreferredSize(new Dimension(80, 30));
        eMonth.setPreferredSize(new Dimension(80, 30));
        eDay.setPreferredSize(new Dimension(80, 30));
        dateRow.add(eYear); dateRow.add(new JLabel("/"));
        dateRow.add(eMonth); dateRow.add(new JLabel("/"));
        dateRow.add(eDay);

        JPanel timeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        eHour.setPreferredSize(new Dimension(80, 30));
        eMinute.setPreferredSize(new Dimension(80, 30));
        timeRow.add(eHour); timeRow.add(new JLabel(":"));
        timeRow.add(eMinute);

        // 重要性複選框
        JCheckBox eImportant = new JCheckBox("重要任務", selectedTask.isImportant());

        JPanel editPanel = new JPanel();
        editPanel.setLayout(new BoxLayout(editPanel, BoxLayout.Y_AXIS));
        editPanel.add(new JLabel("編輯內容："));
        editPanel.add(editContent);
        editPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        editPanel.add(new JLabel("編輯日期："));
        editPanel.add(dateRow);
        editPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        editPanel.add(new JLabel("編輯時間："));
        editPanel.add(timeRow);
        editPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        editPanel.add(eImportant);

        int result = JOptionPane.showConfirmDialog(this, editPanel, "編輯任務", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            String newContent = editContent.getText().trim();
            if (newContent.isEmpty()) {
                JOptionPane.showMessageDialog(this, "內容不可為空。", "編輯錯誤", JOptionPane.WARNING_MESSAGE);
                return;
            }
            int y = (int) eYear.getValue();
            int mo = (int) eMonth.getValue();
            int d = (int) eDay.getValue();
            int h = (int) eHour.getValue();
            int mi = (int) eMinute.getValue();
            try {
                LocalDate.of(y, mo, d);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "日期不合法。", "日期錯誤", JOptionPane.WARNING_MESSAGE);
                return;
            }
            selectedTask.setContent(newContent);
            selectedTask.setDate(String.format("%04d-%02d-%02d", y, mo, d));
            selectedTask.setTime(String.format("%02d:%02d", h, mi));
            selectedTask.setImportant(eImportant.isSelected());
            remindedIds.remove(selectedTask.getId());
            selectedTask = null;
            updateCalendar();
        }
    }

    private void deleteSelectedTask() {
        if (selectedTask == null) {
            JOptionPane.showMessageDialog(this, "請先點選要刪除的任務。", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        remindedIds.remove(selectedTask.getId());
        tasks.remove(selectedTask);
        selectedTask = null;
        updateCalendar();
    }

    /**
     * 更新行事曆顯示內容。
     */
    public void updateCalendar() {
        LocalDate today = LocalDate.now();
        LocalDate weekEnd = weekStart.plusDays(6);
        weekLabel.setText(weekStart.format(DATE_FMT) + " ~ " + weekEnd.format(DATE_FMT));

        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStart.plusDays(i);
            String dateStr = date.format(ISO_FMT);

            dayLabels[i].setText("<html><center>" + date.format(DATE_FMT)
                    + "<br>（" + WEEK_DAY_NAMES[i] + "）</center></html>");

            dayPanels[i].setBackground(date.equals(today)
                    ? new Color(220, 235, 255)
                    : new Color(250, 250, 250));

            JList<Task> taskList = dayTaskLists.get(i);
            DefaultListModel<Task> model = (DefaultListModel<Task>) taskList.getModel();
            model.clear();
            tasks.stream()
                    .filter(task -> dateStr.equals(task.getDate()))
                    .sorted((a, b) -> a.getTime().compareTo(b.getTime()))
                    .forEach(model::addElement);
            if (selectedTask != null && selectedTask.getDate().equals(dateStr)) {
                taskList.setSelectedValue(selectedTask, true);
            } else {
                taskList.clearSelection();
            }
        }
    }

    private void scanReminders() {
        for (Task task : tasks) {
            maybeShowReminder(task);
        }
    }

    private void maybeShowReminder(Task task) {
        if (!task.isImportant()) return;
        try {
            String dateTimeStr = task.getDate() + " " + task.getTime();
            LocalDateTime target = LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            long diff = java.time.Duration.between(LocalDateTime.now(), target).toMinutes();
            if (diff >= 0 && diff <= 240 && !remindedIds.contains(task.getId())) {
                remindedIds.add(task.getId());
                JOptionPane.showMessageDialog(this,
                        String.format("提醒：重要任務「%s」將在四小時內到期。", task.getContent()),
                        "任務提醒", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (DateTimeParseException ignored) {}
    }
}