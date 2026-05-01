import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.List;

public class CalendarPanel extends JPanel {

    private static final String[] WEEK_DAY_NAMES = {"日","一","二","三","四","五","六"};
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MM/dd");
    private static final DateTimeFormatter ISO_FMT  = DateTimeFormatter.ISO_LOCAL_DATE;

    private final JPanel[]  dayPanels     = new JPanel[7];   // 外層格子
    private final JLabel[]  dayLabels     = new JLabel[7];   // 日期標頭
    private final JPanel[]  taskContainers = new JPanel[7];  // 任務列表容器
    private final JLabel    weekLabel     = new JLabel("", SwingConstants.CENTER);
    private LocalDate        weekStart;
    private final List<Task> tasks;

    private final javax.swing.Timer reminderTimer;
    private final Set<Integer>      remindedIds = new HashSet<>();

    private TaskPopover currentPopover = null;

    public CalendarPanel(List<Task> tasks) {
        this.tasks = tasks;

        LocalDate today = LocalDate.now();
        int dow = today.getDayOfWeek().getValue() % 7;
        this.weekStart = today.minusDays(dow);

        setLayout(new BorderLayout(0, 0));
        setBackground(AppColors.BG_SECONDARY);

        add(buildTopNav(),  BorderLayout.NORTH);
        add(buildGrid(),    BorderLayout.CENTER);
        add(buildHintBar(), BorderLayout.SOUTH);

        updateCalendar();

        reminderTimer = new javax.swing.Timer(60_000, e -> scanReminders());
        reminderTimer.setInitialDelay(0);
        reminderTimer.start();
    }

    // ── 頂部週切換列 ──────────────────────────────────────────────────────────
    private JPanel buildTopNav() {
        JPanel nav = new JPanel(new BorderLayout());
        nav.setBackground(AppColors.BG_SECONDARY);
        nav.setBorder(new EmptyBorder(12, 16, 8, 16));

        weekLabel.setFont(AppFonts.TITLE_MEDIUM);
        weekLabel.setForeground(AppColors.TEXT_PRIMARY);

        JButton prevBtn  = navBtn("<");
        JButton nextBtn  = navBtn(">");
        JButton todayBtn = new JButton("今天");
        todayBtn.setFont(AppFonts.BODY_SMALL);
        todayBtn.setForeground(AppColors.ACCENT);
        todayBtn.setBackground(AppColors.ACCENT_LIGHT);
        todayBtn.setBorder(new EmptyBorder(4, 12, 4, 12));
        todayBtn.setFocusPainted(false);
        todayBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        prevBtn.addActionListener(e  -> { closePopover(); weekStart = weekStart.minusWeeks(1); updateCalendar(); });
        nextBtn.addActionListener(e  -> { closePopover(); weekStart = weekStart.plusWeeks(1);  updateCalendar(); });
        todayBtn.addActionListener(e -> {
            closePopover();
            LocalDate t = LocalDate.now();
            weekStart = t.minusDays(t.getDayOfWeek().getValue() % 7);
            updateCalendar();
        });

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.setOpaque(false);
        left.add(prevBtn); left.add(nextBtn); left.add(todayBtn); left.add(weekLabel);

        JButton addBtn = new JButton("+ 新增任務");
        addBtn.setFont(AppFonts.BODY_SMALL);
        addBtn.setBackground(AppColors.ACCENT);
        addBtn.setForeground(Color.WHITE);
        addBtn.setBorder(new EmptyBorder(7, 16, 7, 16));
        addBtn.setFocusPainted(false);
        addBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addBtn.addActionListener(e -> { closePopover(); openTaskDialog(null, LocalDate.now()); });

        nav.add(left,   BorderLayout.WEST);
        nav.add(addBtn, BorderLayout.EAST);
        return nav;
    }

    private JButton navBtn(String text) {
        JButton b = new JButton(text);
        b.setFont(AppFonts.TITLE_MEDIUM);
        b.setForeground(AppColors.TEXT_SECONDARY);
        b.setBorder(new EmptyBorder(2, 10, 2, 10));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setContentAreaFilled(false);
        return b;
    }

    // ── 底部提示列 ────────────────────────────────────────────────────────────
    private JPanel buildHintBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 6));
        bar.setBackground(AppColors.BG_SECONDARY);
        bar.setBorder(new MatteBorder(1, 0, 0, 0, AppColors.BORDER_DEFAULT));
        JLabel hint = new JLabel("點擊任務查看詳情・編輯・刪除　|　點空白處新增任務");
        hint.setFont(AppFonts.CAPTION);
        hint.setForeground(AppColors.TEXT_TERTIARY);
        bar.add(hint);
        return bar;
    }

    // ── 七天格子 ──────────────────────────────────────────────────────────────
    private JPanel buildGrid() {
        JPanel grid = new JPanel(new GridLayout(1, 7, 1, 0));
        grid.setBackground(AppColors.BORDER_DEFAULT);
        grid.setBorder(new MatteBorder(1, 0, 1, 0, AppColors.BORDER_DEFAULT));

        for (int i = 0; i < 7; i++) {
            final int idx = i;

            // 外層格子
            JPanel dayPanel = new JPanel(new BorderLayout());
            dayPanel.setBackground(AppColors.BG_PRIMARY);

            // 日期標頭
            dayLabels[i] = new JLabel("", SwingConstants.CENTER);
            dayLabels[i].setFont(AppFonts.BODY_SMALL);
            dayLabels[i].setOpaque(true);
            dayLabels[i].setBackground(AppColors.BG_PRIMARY);
            dayLabels[i].setBorder(new MatteBorder(0, 0, 1, 0, AppColors.BORDER_DEFAULT));
            dayLabels[i].setPreferredSize(new Dimension(0, 44));
            dayPanel.add(dayLabels[i], BorderLayout.NORTH);

            // 任務容器：BoxLayout 垂直堆疊，靠頂對齊
            JPanel taskContainer = new JPanel();
            taskContainer.setLayout(new BoxLayout(taskContainer, BoxLayout.Y_AXIS));
            taskContainer.setBackground(AppColors.BG_PRIMARY);
            taskContainers[i] = taskContainer;

            // wrapper 讓任務靠頂，剩餘空白可點擊新增
            JPanel wrapper = new JPanel(new BorderLayout());
            wrapper.setBackground(AppColors.BG_PRIMARY);
            wrapper.add(taskContainer, BorderLayout.NORTH);

            // 點擊空白處新增任務
            wrapper.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    closePopover();
                    openTaskDialog(null, weekStart.plusDays(idx));
                }
            });
            // taskContainer 空白處也新增
            taskContainer.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    // 只有點到 taskContainer 本身（非子元件）才新增
                    if (e.getSource() == taskContainer) {
                        closePopover();
                        openTaskDialog(null, weekStart.plusDays(idx));
                    }
                }
            });

            // 不需要 ScrollPane，讓格子自然撐高即可
            dayPanel.add(wrapper, BorderLayout.CENTER);
            dayPanels[i] = dayPanel;
            grid.add(dayPanel);
        }
        return grid;
    }

    // ── 建立單一任務卡片 ──────────────────────────────────────────────────────
    private JPanel buildTaskCard(Task task, int dayIdx) {
        JPanel card = new JPanel(new BorderLayout(4, 0));
        card.setOpaque(true);
        card.setBorder(new EmptyBorder(4, 6, 4, 6));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // 左側 dot
        JLabel dot = new JLabel(task.isImportant() ? "!" : "-", SwingConstants.CENTER);
        dot.setFont(new Font("Dialog", Font.BOLD, 11));
        dot.setForeground(task.isImportant() ? AppColors.DANGER : AppColors.TEXT_TERTIARY);
        dot.setPreferredSize(new Dimension(14, 14));
        dot.setVerticalAlignment(SwingConstants.TOP);

        // 右側：標題 + 時間
        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.setOpaque(false);

        // 標題 — 自動換行
        JLabel titleLbl = new JLabel("<html>" + escHtml(task.getTitle()) + "</html>");
        titleLbl.setFont(task.isImportant()
                ? AppFonts.BODY_SMALL.deriveFont(Font.BOLD)
                : AppFonts.BODY_SMALL);
        titleLbl.setForeground(task.isImportant() ? AppColors.DANGER : AppColors.TEXT_PRIMARY);
        titleLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        right.add(titleLbl);

        // 時間（若有）
        if (task.hasDeadline() && !task.getTime().isEmpty()) {
            JLabel timeLbl = new JLabel(task.getTime());
            timeLbl.setFont(AppFonts.CAPTION);
            timeLbl.setForeground(AppColors.TEXT_TERTIARY);
            timeLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
            right.add(timeLbl);
        }

        card.add(dot,   BorderLayout.WEST);
        card.add(right, BorderLayout.CENTER);

        // 底部分隔線
        card.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 1, 0, AppColors.BORDER_DEFAULT),
                new EmptyBorder(4, 6, 4, 6)));

        // hover 效果
        card.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                card.setBackground(AppColors.ACCENT_LIGHT);
                right.setBackground(AppColors.ACCENT_LIGHT);
            }
            @Override public void mouseExited(MouseEvent e) {
                card.setBackground(AppColors.BG_PRIMARY);
                right.setBackground(AppColors.BG_PRIMARY);
            }
            @Override public void mouseClicked(MouseEvent e) {
                showPopover(task, card, dayIdx);
            }
        });

        card.setBackground(AppColors.BG_PRIMARY);
        right.setBackground(AppColors.BG_PRIMARY);

        return card;
    }

    // ── Popover 顯示 / 關閉 ──────────────────────────────────────────────────
    private void showPopover(Task task, JPanel sourceCard, int dayIdx) {
        closePopover();

        JRootPane root = SwingUtilities.getRootPane(this);
        if (root == null) return;
        JLayeredPane layered = root.getLayeredPane();

        currentPopover = new TaskPopover(task,
            () -> { closePopover(); openTaskDialog(task, null); },
            () -> {
                int confirm = JOptionPane.showConfirmDialog(CalendarPanel.this,
                        "確定要刪除「" + task.getTitle() + "」？",
                        "確認刪除", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    remindedIds.remove(task.getId());
                    tasks.remove(task);
                    closePopover();
                    updateCalendar();
                }
            },
            this::closePopover
        );

        Point cardLoc = SwingUtilities.convertPoint(sourceCard, 0, 0, layered);
        int popW = 280;
        int popH = currentPopover.getPreferredSize().height;
        int x    = cardLoc.x + sourceCard.getWidth() + 4;
        int y    = cardLoc.y;

        if (x + popW > layered.getWidth())  x = cardLoc.x - popW - 4;
        if (y + popH > layered.getHeight()) y = Math.max(0, layered.getHeight() - popH - 8);
        x = Math.max(0, x);

        currentPopover.setBounds(x, y, popW, popH);
        layered.add(currentPopover, JLayeredPane.POPUP_LAYER);
        layered.revalidate();
        layered.repaint();

        AWTEventListener closer = new AWTEventListener() {
            @Override public void eventDispatched(AWTEvent event) {
                if (event instanceof MouseEvent) {
                    MouseEvent me = (MouseEvent) event;
                    if (me.getID() == MouseEvent.MOUSE_PRESSED) {
                        if (currentPopover != null) {
                            Point p = SwingUtilities.convertPoint(
                                    me.getComponent(), me.getPoint(), currentPopover);
                            if (!currentPopover.contains(p)) {
                                closePopover();
                                Toolkit.getDefaultToolkit().removeAWTEventListener(this);
                            }
                        } else {
                            Toolkit.getDefaultToolkit().removeAWTEventListener(this);
                        }
                    }
                }
            }
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(closer, AWTEvent.MOUSE_EVENT_MASK);
    }

    private void closePopover() {
        if (currentPopover == null) return;
        JRootPane root = SwingUtilities.getRootPane(this);
        if (root != null) {
            root.getLayeredPane().remove(currentPopover);
            root.getLayeredPane().repaint();
        }
        currentPopover = null;
    }

    // ── 任務 Popover 元件 ────────────────────────────────────────────────────
    private static class TaskPopover extends JPanel {
        TaskPopover(Task task, Runnable onEdit, Runnable onDelete, Runnable onClose) {
            setLayout(new BorderLayout(0, 0));
            setBackground(Color.WHITE);
            setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColors.BORDER_DEFAULT, 1, true),
                new EmptyBorder(0, 0, 0, 0)
            ));
            setOpaque(true);

            JPanel header = new JPanel(new BorderLayout());
            header.setBackground(task.isImportant() ? AppColors.DANGER_LIGHT : AppColors.ACCENT_LIGHT);
            header.setBorder(new EmptyBorder(10, 14, 10, 10));

            JLabel titleLbl = new JLabel("<html><b>" + escHtml(task.getTitle()) + "</b></html>");
            titleLbl.setFont(AppFonts.BODY_MEDIUM);
            titleLbl.setForeground(task.isImportant() ? AppColors.DANGER : AppColors.ACCENT);

            JButton closeBtn = new JButton("x");
            closeBtn.setFont(new Font(AppFonts.CAPTION.getFamily(), Font.PLAIN, 11));
            closeBtn.setForeground(AppColors.TEXT_TERTIARY);
            closeBtn.setBorder(new EmptyBorder(2, 6, 2, 6));
            closeBtn.setFocusPainted(false);
            closeBtn.setContentAreaFilled(false);
            closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            closeBtn.addActionListener(e -> onClose.run());

            header.add(titleLbl, BorderLayout.CENTER);
            header.add(closeBtn, BorderLayout.EAST);

            JPanel body = new JPanel();
            body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
            body.setBackground(Color.WHITE);
            body.setBorder(new EmptyBorder(12, 14, 8, 14));

            if (task.isImportant()) {
                JLabel imp = new JLabel("[ ! ] 重要任務");
                imp.setFont(AppFonts.CAPTION);
                imp.setForeground(AppColors.DANGER);
                imp.setAlignmentX(Component.LEFT_ALIGNMENT);
                body.add(imp);
                body.add(Box.createRigidArea(new Dimension(0, 6)));
            }

            if (task.hasDeadline() && !task.getDate().isEmpty()) {
                String timeStr = task.getTime().isEmpty()
                        ? task.getDate()
                        : task.getDate() + "  " + task.getTime();
                JLabel timeLbl = rowLabel("[時間]  " + timeStr);
                timeLbl.setForeground(AppColors.TEXT_SECONDARY);
                body.add(timeLbl);
                body.add(Box.createRigidArea(new Dimension(0, 6)));
            } else {
                JLabel timeLbl = rowLabel("[時間]  無截止日期");
                timeLbl.setForeground(AppColors.TEXT_TERTIARY);
                body.add(timeLbl);
                body.add(Box.createRigidArea(new Dimension(0, 6)));
            }

            String desc = task.getDescription();
            if (desc != null && !desc.isEmpty()) {
                JLabel descHead = rowLabel("[說明]");
                descHead.setForeground(AppColors.TEXT_TERTIARY);
                body.add(descHead);
                body.add(Box.createRigidArea(new Dimension(0, 3)));

                JTextArea descArea = new JTextArea(desc);
                descArea.setFont(AppFonts.BODY_SMALL);
                descArea.setForeground(AppColors.TEXT_PRIMARY);
                descArea.setBackground(AppColors.BG_SECONDARY);
                descArea.setEditable(false);
                descArea.setLineWrap(true);
                descArea.setWrapStyleWord(true);
                descArea.setBorder(new EmptyBorder(6, 8, 6, 8));
                descArea.setAlignmentX(Component.LEFT_ALIGNMENT);
                descArea.setMaximumSize(new Dimension(252, 100));
                descArea.setPreferredSize(new Dimension(252, Math.min(80, desc.length() * 2 + 30)));
                body.add(descArea);
                body.add(Box.createRigidArea(new Dimension(0, 8)));
            }

            if (task.isCompleted()) {
                JLabel done = rowLabel("[v] 已完成");
                done.setForeground(AppColors.SUCCESS);
                body.add(done);
                body.add(Box.createRigidArea(new Dimension(0, 6)));
            }

            JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 8));
            btnRow.setBackground(new Color(0xFAF9F7));
            btnRow.setBorder(new MatteBorder(1, 0, 0, 0, AppColors.BORDER_DEFAULT));

            JButton editBtn = popBtn("編輯", AppColors.ACCENT, Color.WHITE);
            JButton delBtn  = popBtn("刪除", AppColors.BG_TERTIARY, AppColors.DANGER);
            editBtn.addActionListener(e -> onEdit.run());
            delBtn .addActionListener(e -> onDelete.run());

            btnRow.add(delBtn);
            btnRow.add(editBtn);

            add(header, BorderLayout.NORTH);
            add(body,   BorderLayout.CENTER);
            add(btnRow, BorderLayout.SOUTH);
        }

        private static JLabel rowLabel(String text) {
            JLabel l = new JLabel(text);
            l.setFont(AppFonts.BODY_SMALL);
            l.setAlignmentX(Component.LEFT_ALIGNMENT);
            return l;
        }

        private static JButton popBtn(String text, Color bg, Color fg) {
            JButton b = new JButton(text);
            b.setFont(AppFonts.BODY_SMALL);
            b.setBackground(bg);
            b.setForeground(fg);
            b.setBorder(new EmptyBorder(5, 14, 5, 14));
            b.setFocusPainted(false);
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            b.setOpaque(true);
            return b;
        }

        private static String escHtml(String s) {
            return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(0, 0, 0, 18));
            g2.fillRoundRect(3, 5, getWidth()-3, getHeight()-3, 10, 10);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ── 新增 / 編輯 Dialog ────────────────────────────────────────────────────
    private void openTaskDialog(Task editTask, LocalDate defaultDate) {
        boolean isEdit = (editTask != null);
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dlg = new JDialog(owner, isEdit ? "編輯任務" : "新增任務",
                Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setLayout(new BorderLayout());
        dlg.setResizable(false);

        LocalDate initDate = isEdit
                ? (editTask.hasDeadline() && !editTask.getDate().isEmpty()
                   ? LocalDate.parse(editTask.getDate()) : LocalDate.now())
                : (defaultDate != null ? defaultDate : LocalDate.now());

        JTextField titleField = new JTextField(isEdit ? editTask.getTitle() : "", 24);
        titleField.setFont(AppFonts.BODY_MEDIUM);

        JTextArea descArea = new JTextArea(isEdit ? editTask.getDescription() : "", 3, 24);
        descArea.setFont(AppFonts.BODY_SMALL);
        descArea.setLineWrap(true);
        descArea.setWrapStyleWord(true);
        JScrollPane descScroll = new JScrollPane(descArea);
        descScroll.setPreferredSize(new Dimension(300, 62));

        JCheckBox importantCheck = new JCheckBox("[ ! ] 標記為重要任務",
                isEdit && editTask.isImportant());
        importantCheck.setFont(AppFonts.BODY_SMALL);

        int initH = 9, initM = 0;
        if (isEdit && !editTask.getTime().isEmpty()) {
            String[] tp = editTask.getTime().split(":");
            try { initH = Integer.parseInt(tp[0]); initM = Integer.parseInt(tp[1]); }
            catch (NumberFormatException ignored) {}
        }

        JSpinner yearSp  = makeSpinner(initDate.getYear(),       2020, 2099, 1);
        JSpinner monthSp = makeSpinner(initDate.getMonthValue(), 1,    12,   1);
        JSpinner daySp   = makeSpinner(initDate.getDayOfMonth(), 1,    31,   1);
        JSpinner hourSp  = makeSpinner(initH,                    0,    23,   1);
        JSpinner minSp   = makeSpinner(initM,                    0,    59,   1);
        for (JSpinner s : new JSpinner[]{monthSp, daySp, hourSp, minSp})
            s.setEditor(new JSpinner.NumberEditor(s, "00"));
        yearSp .setPreferredSize(new Dimension(68, 26));
        monthSp.setPreferredSize(new Dimension(50, 26));
        daySp  .setPreferredSize(new Dimension(50, 26));
        hourSp .setPreferredSize(new Dimension(50, 26));
        minSp  .setPreferredSize(new Dimension(50, 26));

        JLabel deadlineLabel = new JLabel("截止日期與時間");
        deadlineLabel.setFont(AppFonts.BODY_SMALL);
        deadlineLabel.setForeground(AppColors.TEXT_SECONDARY);

        JPanel dateRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        dateRow.setOpaque(false);
        dateRow.add(new JLabel("日期："));
        dateRow.add(yearSp); dateRow.add(new JLabel("/")); dateRow.add(monthSp);
        dateRow.add(new JLabel("/")); dateRow.add(daySp);

        JPanel timeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        timeRow.setOpaque(false);
        timeRow.add(new JLabel("時間："));
        timeRow.add(hourSp); timeRow.add(new JLabel(":")); timeRow.add(minSp);

        JPanel dtPanel = new JPanel();
        dtPanel.setLayout(new BoxLayout(dtPanel, BoxLayout.Y_AXIS));
        dtPanel.setOpaque(false);
        dtPanel.add(dateRow);
        dtPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        dtPanel.add(timeRow);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(16, 20, 8, 20));

        content.add(fieldRow("標題", titleField));
        content.add(Box.createRigidArea(new Dimension(0, 10)));
        content.add(fieldRow("說明", descScroll));
        content.add(Box.createRigidArea(new Dimension(0, 12)));
        content.add(leftAlign(deadlineLabel));
        content.add(Box.createRigidArea(new Dimension(0, 6)));
        content.add(leftAlign(dtPanel));
        content.add(Box.createRigidArea(new Dimension(0, 10)));
        content.add(leftAlign(importantCheck));

        JButton okBtn     = new JButton(isEdit ? "儲存" : "新增");
        JButton cancelBtn = new JButton("取消");
        okBtn.setFont(AppFonts.BODY_SMALL);
        cancelBtn.setFont(AppFonts.BODY_SMALL);
        okBtn.setBackground(AppColors.ACCENT);
        okBtn.setForeground(Color.WHITE);
        okBtn.setFocusPainted(false);
        okBtn.setBorder(new EmptyBorder(6, 20, 6, 20));
        cancelBtn.setBorder(new EmptyBorder(6, 16, 6, 16));
        cancelBtn.setFocusPainted(false);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10));
        btnRow.setBorder(new MatteBorder(1, 0, 0, 0, AppColors.BORDER_DEFAULT));
        btnRow.add(cancelBtn);
        btnRow.add(okBtn);

        dlg.add(content, BorderLayout.CENTER);
        dlg.add(btnRow,  BorderLayout.SOUTH);
        dlg.pack();
        dlg.setLocationRelativeTo(this);

        cancelBtn.addActionListener(e -> dlg.dispose());
        dlg.getRootPane().setDefaultButton(okBtn);

        okBtn.addActionListener(e -> {
            String titleVal = titleField.getText().trim();
            if (titleVal.isEmpty()) {
                JOptionPane.showMessageDialog(dlg, "標題不可為空。", "輸入錯誤", JOptionPane.WARNING_MESSAGE);
                return;
            }
            int y=(int)yearSp.getValue(), mo=(int)monthSp.getValue(),
                d=(int)daySp.getValue(),  h=(int)hourSp.getValue(),
                mi=(int)minSp.getValue();
            try { LocalDate.of(y, mo, d); }
            catch (Exception ex) {
                JOptionPane.showMessageDialog(dlg,"日期不合法。","日期錯誤",JOptionPane.WARNING_MESSAGE);
                return;
            }
            String dateVal = String.format("%04d-%02d-%02d", y, mo, d);
            String timeVal = String.format("%02d:%02d", h, mi);
            if (isEdit) {
                editTask.setTitle(titleVal);
                editTask.setDescription(descArea.getText().trim());
                editTask.setHasDeadline(true);
                editTask.setDate(dateVal);
                editTask.setTime(timeVal);
                editTask.setImportant(importantCheck.isSelected());
                remindedIds.remove(editTask.getId());
            } else {
                int nextId = tasks.isEmpty() ? 1
                        : tasks.stream().mapToInt(Task::getId).max().orElse(0) + 1;
                Task t = new Task(nextId, titleVal, descArea.getText().trim(),
                                  dateVal, timeVal, true);
                t.setImportant(importantCheck.isSelected());
                tasks.add(t);
            }
            dlg.dispose();
            updateCalendar();
        });

        dlg.setVisible(true);
    }

    private JPanel fieldRow(String labelText, JComponent comp) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        JLabel lbl = new JLabel(labelText);
        lbl.setFont(AppFonts.BODY_SMALL);
        lbl.setForeground(AppColors.TEXT_SECONDARY);
        lbl.setPreferredSize(new Dimension(36, 0));
        row.add(lbl,  BorderLayout.WEST);
        row.add(comp, BorderLayout.CENTER);
        return row;
    }

    private JPanel leftAlign(JComponent comp) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.setOpaque(false);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        p.add(comp);
        return p;
    }

    private JSpinner makeSpinner(int val, int min, int max, int step) {
        return new JSpinner(new SpinnerNumberModel(val, min, max, step));
    }

    // ── 更新顯示 ──────────────────────────────────────────────────────────────
    public void updateCalendar() {
        LocalDate today   = LocalDate.now();
        LocalDate weekEnd = weekStart.plusDays(6);
        weekLabel.setText(weekStart.format(DATE_FMT) + " - " + weekEnd.format(DATE_FMT));

        for (int i = 0; i < 7; i++) {
            final int idx = i;
            LocalDate date    = weekStart.plusDays(i);
            String    dateStr = date.format(ISO_FMT);
            boolean   isToday   = date.equals(today);
            boolean   isWeekend = (i == 0 || i == 6);

            String dayNum  = String.valueOf(date.getDayOfMonth());
            String dayName = WEEK_DAY_NAMES[i];
            Color  bgColor = isToday ? AppColors.TODAY_BG : AppColors.BG_PRIMARY;
            Color  fgColor = isWeekend ? AppColors.DANGER : AppColors.TEXT_SECONDARY;

            if (isToday) {
                dayLabels[i].setText(
                    "<html><center>"
                    + "<span style='background:#EEF2FF;color:#3B5BDB;"
                    + "padding:1px 5px;border-radius:3px'><b>" + dayNum + "</b></span>"
                    + "<br><small style='color:#3B5BDB'>(" + dayName + ")</small>"
                    + "</center></html>");
            } else {
                dayLabels[i].setText(
                    "<html><center><b style='color:"
                    + toHex(fgColor) + "'>" + dayNum + "</b>"
                    + "<br><small style='color:" + toHex(AppColors.TEXT_TERTIARY)
                    + "'>(" + dayName + ")</small></center></html>");
            }
            dayLabels[i].setBackground(bgColor);
            dayPanels[i].setBackground(bgColor);

            // 清空並重建任務卡片
            JPanel container = taskContainers[i];
            container.removeAll();
            container.setBackground(bgColor);

            // 無截止日任務只顯示在今天
            if (isToday) {
                tasks.stream()
                     .filter(t -> !t.hasDeadline())
                     .sorted(Comparator.comparing(Task::getTitle))
                     .forEach(t -> container.add(buildTaskCard(t, idx)));
            }
            // 有截止日任務
            tasks.stream()
                 .filter(t -> t.hasDeadline() && dateStr.equals(t.getDate()))
                 .sorted(Comparator.comparing(Task::getTime))
                 .forEach(t -> container.add(buildTaskCard(t, idx)));

            container.revalidate();
            container.repaint();
        }
    }

    private static String toHex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static String escHtml(String s) {
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }

    // ── Reminder ──────────────────────────────────────────────────────────────
    private void scanReminders() {
        for (Task t : tasks) maybeRemind(t);
    }

    private void maybeRemind(Task task) {
        if (!task.isImportant() || !task.hasDeadline()) return;
        try {
            LocalDateTime target = LocalDateTime.parse(
                    task.getDate() + " " + task.getTime(),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            long diff = java.time.Duration.between(LocalDateTime.now(), target).toMinutes();
            if (diff >= 0 && diff <= 240 && !remindedIds.contains(task.getId())) {
                remindedIds.add(task.getId());
                JOptionPane.showMessageDialog(this,
                        "提醒：重要任務「" + task.getTitle() + "」將在四小時內到期。",
                        "任務提醒", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (DateTimeParseException ignored) {}
    }
}