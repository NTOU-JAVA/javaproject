import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

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

            JTextArea taskArea = new JTextArea();
            taskArea.setEditable(false);
            taskArea.setOpaque(false);
            taskArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            taskArea.setLineWrap(true);
            taskArea.setWrapStyleWord(true);
            JScrollPane scroll = new JScrollPane(taskArea);
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
        formPanel.setPreferredSize(new Dimension(200, 0));

        // 日期選擇
        JPanel datePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        yearSpinner.setPreferredSize(new Dimension(65, 25));
        monthSpinner.setPreferredSize(new Dimension(45, 25));
        daySpinner.setPreferredSize(new Dimension(45, 25));
        datePanel.add(yearSpinner);
        datePanel.add(new JLabel("/"));
        datePanel.add(monthSpinner);
        datePanel.add(new JLabel("/"));
        datePanel.add(daySpinner);

        // 時間選擇
        JPanel timePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        hourSpinner.setPreferredSize(new Dimension(45, 25));
        minuteSpinner.setPreferredSize(new Dimension(45, 25));
        timePanel.add(hourSpinner);
        timePanel.add(new JLabel(":"));
        timePanel.add(minuteSpinner);

        JPanel fieldPanel = new JPanel(new GridLayout(6, 1, 4, 4));
        fieldPanel.add(new JLabel("任務內容："));
        fieldPanel.add(contentField);
        fieldPanel.add(new JLabel("日期："));
        fieldPanel.add(datePanel);
        fieldPanel.add(new JLabel("時間："));
        fieldPanel.add(timePanel);
        formPanel.add(fieldPanel);

        JButton addButton = new JButton("新增任務");
        addButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        addButton.addActionListener(e -> addTask());
        formPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        formPanel.add(addButton);

        // 刪除按鈕
        JButton deleteButton = new JButton("刪除選取任務");
        deleteButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        deleteButton.addActionListener(e -> {
            // 找到目前週內被點選的任務（簡易實作：刪除內容相符的第一筆）
            String content = contentField.getText().trim();
            if (content.isEmpty()) {
                JOptionPane.showMessageDialog(this, "請在內容欄輸入要刪除的任務名稱。", "提示", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            boolean removed = tasks.removeIf(t -> t.getContent().equals(content));
            if (removed) {
                contentField.setText("");
                updateCalendar();
            } else {
                JOptionPane.showMessageDialog(this, "找不到該任務。", "提示", JOptionPane.WARNING_MESSAGE);
            }
        });
        formPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        formPanel.add(deleteButton);

        add(formPanel, BorderLayout.EAST);

        updateCalendar();
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

        tasks.add(new Task(nextId, date, time, content));
        contentField.setText("");
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

            JTextArea taskArea = (JTextArea) ((JScrollPane) dayPanels[i].getComponent(1))
                    .getViewport().getView();
            StringBuilder sb = new StringBuilder();
            for (Task task : tasks) {
                if (dateStr.equals(task.getDate())) {
                    sb.append("• ").append(task.getTime())
                      .append(" ").append(task.getContent()).append("\n");
                }
            }
            taskArea.setText(sb.length() > 0 ? sb.toString() : "（無任務）");
        }
    }
}