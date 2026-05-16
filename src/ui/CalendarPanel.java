package ui;

import java.awt.*;
import java.awt.event.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.*;
import model.Task;
import service.CategoryManager;

public class CalendarPanel extends JPanel {

    private static final String[] WEEK_DAY_NAMES = {"日","一","二","三","四","五","六"};
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MM/dd");
    private static final DateTimeFormatter ISO_FMT  = DateTimeFormatter.ISO_LOCAL_DATE;

    private final JPanel[]  dayPanels     = new JPanel[7];
    private final JLabel[]  dayLabels     = new JLabel[7];
    private final JPanel[]  taskContainers = new JPanel[7];
    private final JLabel    weekLabel     = new JLabel("", SwingConstants.CENTER);
    private LocalDate        weekStart;
    private final List<Task> tasks;
    private final CategoryManager categoryManager;
    private final Runnable saveCallback;

    // 目前選取的分類篩選
    private String currentFilter = CategoryManager.ALL;

    private final javax.swing.Timer reminderTimer;
    private final Set<Integer>      remindedIds = new HashSet<>();

    private TaskPopover currentPopover = null;
    private ComponentListener windowResizeListener = null;

    public CalendarPanel(List<Task> tasks, CategoryManager categoryManager, Runnable saveCallback) {
        this.tasks           = tasks;
        this.categoryManager = categoryManager;
        this.saveCallback    = saveCallback;

        LocalDate today = LocalDate.now();
        int dow = today.getDayOfWeek().getValue() % 7;
        this.weekStart = today.minusDays(dow);

        setLayout(new BorderLayout(0, 0));
        setBackground(AppColors.BG_SECONDARY);

        // 頂部：導覽列 + Tag 列
        JPanel topArea = new JPanel(new BorderLayout());
        topArea.setOpaque(false);
        topArea.add(buildTopNav(), BorderLayout.NORTH);
        topArea.add(buildTagBar(), BorderLayout.SOUTH);
        add(topArea,       BorderLayout.NORTH);
        add(buildGrid(),   BorderLayout.CENTER);
        add(buildHintBar(), BorderLayout.SOUTH);

        updateCalendar();

        // 分類重新命名時，同步 currentFilter 並刷新
        categoryManager.addRenameListener((oldName, newName) -> {
            if (oldName.equals(currentFilter)) {
                currentFilter = newName;
            }
            updateCalendar();
        });

        // 分類清單變動（新增/刪除）時刷新
        categoryManager.addListener(this::updateCalendar);

        reminderTimer = new javax.swing.Timer(60_000, e -> scanReminders());
        reminderTimer.setInitialDelay(0);
        reminderTimer.start();
    }

    @Override public void addNotify() {
        super.addNotify();
        Window w = SwingUtilities.getWindowAncestor(this);
        if (w != null) {
            windowResizeListener = new ComponentAdapter() {
                @Override public void componentResized(ComponentEvent e) { closePopover(); }
            };
            w.addComponentListener(windowResizeListener);
        }
    }

    @Override public void removeNotify() {
        Window w = SwingUtilities.getWindowAncestor(this);
        if (w != null && windowResizeListener != null) {
            w.removeComponentListener(windowResizeListener);
            windowResizeListener = null;
        }
        super.removeNotify();
    }

    // ── Tag 篩選列 ───────────────────────────────────────────────────────────
    private CategoryTagBar buildTagBar() {
        return new CategoryTagBar(categoryManager, filter -> {
            currentFilter = filter;
            updateCalendar();
        });
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

            JPanel dayPanel = new JPanel(new BorderLayout());
            dayPanel.setBackground(AppColors.BG_PRIMARY);

            dayLabels[i] = new JLabel("", SwingConstants.CENTER);
            dayLabels[i].setFont(AppFonts.BODY_SMALL);
            dayLabels[i].setOpaque(true);
            dayLabels[i].setBackground(AppColors.BG_PRIMARY);
            dayLabels[i].setBorder(new MatteBorder(0, 0, 1, 0, AppColors.BORDER_DEFAULT));
            dayLabels[i].setPreferredSize(new Dimension(0, 44));
            dayPanel.add(dayLabels[i], BorderLayout.NORTH);

            JPanel taskContainer = new JPanel();
            taskContainer.setLayout(new BoxLayout(taskContainer, BoxLayout.Y_AXIS));
            taskContainer.setBackground(AppColors.BG_PRIMARY);
            taskContainers[i] = taskContainer;

            JPanel wrapper = new JPanel(new BorderLayout());
            wrapper.setBackground(AppColors.BG_PRIMARY);
            wrapper.add(taskContainer, BorderLayout.NORTH);

            wrapper.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    closePopover();
                    openTaskDialog(null, weekStart.plusDays(idx));
                }
            });
            taskContainer.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (e.getSource() == taskContainer) {
                        closePopover();
                        openTaskDialog(null, weekStart.plusDays(idx));
                    }
                }
            });

            JScrollPane dayScroll = new JScrollPane(wrapper,
                    JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                    JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            dayScroll.setBorder(null);
            dayScroll.getViewport().setBackground(AppColors.BG_PRIMARY);
            dayScroll.getVerticalScrollBar().setUnitIncrement(16);
            AppUIManager.applySlimScrollBar(dayScroll);
            dayPanel.add(dayScroll, BorderLayout.CENTER);
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

        JLabel dot = new JLabel(task.isImportant() ? "!" : "-", SwingConstants.CENTER);
        dot.setFont(new Font("Dialog", Font.BOLD, 11));
        dot.setForeground(task.isImportant() ? AppColors.DANGER : AppColors.TEXT_TERTIARY);
        dot.setPreferredSize(new Dimension(14, 14));
        dot.setVerticalAlignment(SwingConstants.TOP);

        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.setOpaque(false);

        Font titleFont = task.isImportant()
                ? AppFonts.BODY_SMALL.deriveFont(Font.BOLD)
                : AppFonts.BODY_SMALL;
        Color titleColor = task.isImportant() ? AppColors.DANGER : AppColors.TEXT_PRIMARY;

        JTextArea titleLbl = new JTextArea(task.getTitle());
        titleLbl.setFont(titleFont);
        titleLbl.setForeground(titleColor);
        titleLbl.setEditable(false);
        titleLbl.setFocusable(false);
        titleLbl.setLineWrap(true);
        titleLbl.setWrapStyleWord(true);
        titleLbl.setOpaque(false);
        titleLbl.setBorder(null);
        titleLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleLbl.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        titleLbl.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e)  { card.dispatchEvent(SwingUtilities.convertMouseEvent(titleLbl, e, card)); }
            @Override public void mouseEntered(MouseEvent e)  { card.dispatchEvent(SwingUtilities.convertMouseEvent(titleLbl, e, card)); }
            @Override public void mouseExited(MouseEvent e)   { card.dispatchEvent(SwingUtilities.convertMouseEvent(titleLbl, e, card)); }
            @Override public void mousePressed(MouseEvent e)  { card.dispatchEvent(SwingUtilities.convertMouseEvent(titleLbl, e, card)); }
            @Override public void mouseReleased(MouseEvent e) { card.dispatchEvent(SwingUtilities.convertMouseEvent(titleLbl, e, card)); }
        });

        right.add(titleLbl);

        // 時間（若有）
        if (task.hasDeadline() && !task.getTime().isEmpty()) {
            JLabel timeLbl = new JLabel(task.getTime());
            timeLbl.setFont(AppFonts.CAPTION);
            timeLbl.setForeground(AppColors.TEXT_TERTIARY);
            timeLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
            right.add(timeLbl);
        }

        // 分類標籤（若有）
        if (!task.getCategory().isEmpty()) {
            JLabel catTag = new JLabel(task.getCategory()) {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(AppColors.ACCENT_LIGHT);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            catTag.setFont(AppFonts.CAPTION);
            catTag.setForeground(AppColors.ACCENT);
            catTag.setBorder(new EmptyBorder(1, 5, 1, 5));
            catTag.setOpaque(false);
            catTag.setAlignmentX(Component.LEFT_ALIGNMENT);
            catTag.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e)  { card.dispatchEvent(SwingUtilities.convertMouseEvent(catTag, e, card)); }
                @Override public void mouseEntered(MouseEvent e)  { card.dispatchEvent(SwingUtilities.convertMouseEvent(catTag, e, card)); }
                @Override public void mouseExited(MouseEvent e)   { card.dispatchEvent(SwingUtilities.convertMouseEvent(catTag, e, card)); }
                @Override public void mousePressed(MouseEvent e)  { card.dispatchEvent(SwingUtilities.convertMouseEvent(catTag, e, card)); }
                @Override public void mouseReleased(MouseEvent e) { card.dispatchEvent(SwingUtilities.convertMouseEvent(catTag, e, card)); }
            });
            right.add(catTag);
        }

        card.add(dot,   BorderLayout.WEST);
        card.add(right, BorderLayout.CENTER);

        card.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 1, 0, AppColors.BORDER_DEFAULT),
                new EmptyBorder(4, 6, 4, 6)));

        card.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                card.setBackground(AppColors.ACCENT_LIGHT);
                right.setBackground(AppColors.ACCENT_LIGHT);
                titleLbl.setBackground(AppColors.ACCENT_LIGHT);
            }
            @Override public void mouseExited(MouseEvent e) {
                card.setBackground(AppColors.BG_PRIMARY);
                right.setBackground(AppColors.BG_PRIMARY);
                titleLbl.setBackground(AppColors.BG_PRIMARY);
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
                remindedIds.remove(task.getId());
                tasks.remove(task);
                closePopover();
                updateCalendar();
                saveCallback.run();
            },
            this::closePopover
        );

        Point cardLoc = SwingUtilities.convertPoint(sourceCard, 0, 0, layered);
        int popW = 280;

        // 先給定寬度，讓 JTextArea wrap 後能正確回報 preferredSize
        currentPopover.setSize(popW, 9999);
        currentPopover.validate();
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

            // ── Header：title 換行自適應，closeBtn 固定右上角 ──
            JPanel header = new JPanel(new BorderLayout(0, 0));
            header.setBackground(task.isImportant() ? AppColors.DANGER_LIGHT : AppColors.ACCENT_LIGHT);
            header.setBorder(new EmptyBorder(10, 14, 10, 10));

            JTextArea titleLbl = new JTextArea(task.getTitle());
            titleLbl.setFont(AppFonts.BODY_MEDIUM.deriveFont(Font.BOLD));
            titleLbl.setForeground(task.isImportant() ? AppColors.DANGER : AppColors.ACCENT);
            titleLbl.setBackground(task.isImportant() ? AppColors.DANGER_LIGHT : AppColors.ACCENT_LIGHT);
            titleLbl.setEditable(false);
            titleLbl.setFocusable(false);
            titleLbl.setLineWrap(true);
            titleLbl.setWrapStyleWord(true);
            titleLbl.setOpaque(false);
            titleLbl.setBorder(null);

            JButton closeBtn = new JButton("×");
            closeBtn.setFont(new Font(AppFonts.CAPTION.getFamily(), Font.PLAIN, 13));
            closeBtn.setForeground(AppColors.TEXT_TERTIARY);
            closeBtn.setBorder(new EmptyBorder(0, 6, 0, 0));
            closeBtn.setFocusPainted(false);
            closeBtn.setContentAreaFilled(false);
            closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            closeBtn.addActionListener(e -> onClose.run());

            // closeWrapper 讓 X 固定在右上角，不隨 title 增高而垂直置中
            JPanel closeWrapper = new JPanel(new BorderLayout());
            closeWrapper.setOpaque(false);
            closeWrapper.add(closeBtn, BorderLayout.NORTH);

            header.add(titleLbl,     BorderLayout.CENTER);
            header.add(closeWrapper, BorderLayout.EAST);

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

            // 分類
            if (!task.getCategory().isEmpty()) {
                JLabel catLbl = rowLabel("[分類]  " + task.getCategory());
                catLbl.setForeground(AppColors.ACCENT);
                body.add(catLbl);
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

                JScrollPane descSp = new JScrollPane(descArea,
                        JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                        JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
                descSp.setBorder(null);
                descSp.setAlignmentX(Component.LEFT_ALIGNMENT);
                descSp.setPreferredSize(new Dimension(252, 90));
                descSp.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
                AppUIManager.applySlimScrollBar(descSp);
                body.add(descSp);
                body.add(Box.createRigidArea(new Dimension(0, 8)));
            }

            if (task.isCompleted()) {
                JLabel done = rowLabel("[v] 已完成");
                done.setForeground(AppColors.SUCCESS);
                body.add(done);
                body.add(Box.createRigidArea(new Dimension(0, 6)));
            }

            // 先測量 body 實際高度，決定 scroll 高度（最小 80，最大 180）
            body.setSize(280 - 2, 9999);
            body.validate();
            int bodyH = Math.max(80, Math.min(body.getPreferredSize().height + 8, 180));

            JScrollPane bodyScroll = new JScrollPane(body,
                    JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                    JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            bodyScroll.setBorder(null);
            bodyScroll.setPreferredSize(new Dimension(0, bodyH));
            bodyScroll.getVerticalScrollBar().setUnitIncrement(16);
            AppUIManager.applySlimScrollBar(bodyScroll);

            CardLayout btnCard = new CardLayout();
            JPanel btnRow = new JPanel(btnCard);
            btnRow.setBackground(new Color(0xFAF9F7));
            btnRow.setBorder(new MatteBorder(1, 0, 0, 0, AppColors.BORDER_DEFAULT));

            JButton editBtn = popBtn("編輯", AppColors.ACCENT, Color.WHITE);
            JButton delBtn  = popBtn("刪除", AppColors.BG_TERTIARY, AppColors.DANGER);
            JPanel normalPane = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 8));
            normalPane.setOpaque(false);
            normalPane.add(delBtn);
            normalPane.add(editBtn);

            JButton cancelDelBtn  = popBtn("取消",    AppColors.BG_TERTIARY, AppColors.TEXT_SECONDARY);
            JButton confirmDelBtn = popBtn("確認刪除", AppColors.DANGER,      Color.WHITE);
            JPanel confirmPane = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 8));
            confirmPane.setOpaque(false);
            confirmPane.add(cancelDelBtn);
            confirmPane.add(confirmDelBtn);

            btnRow.add(normalPane,  "normal");
            btnRow.add(confirmPane, "confirm");
            btnCard.show(btnRow, "normal");

            editBtn.addActionListener(e -> onEdit.run());
            delBtn.addActionListener(e -> btnCard.show(btnRow, "confirm"));
            cancelDelBtn.addActionListener(e -> btnCard.show(btnRow, "normal"));
            confirmDelBtn.addActionListener(e -> onDelete.run());

            add(header,     BorderLayout.NORTH);
            add(bodyScroll, BorderLayout.CENTER);
            add(btnRow,     BorderLayout.SOUTH);
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
        JDialog dlg = new JDialog(owner, "", Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setUndecorated(true);
        dlg.setLayout(new BorderLayout());
        dlg.setBackground(new Color(0xF0F0F0));

        Color headerBg = AppColors.ACCENT_LIGHT;
        JPanel root = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int shadow = 9, W = getWidth() - shadow, H = getHeight() - shadow, R = 14;
                for (int i = shadow; i >= 1; i--) {
                    g2.setColor(new Color(0, 0, 0, 2 + i));
                    g2.fillRoundRect(i / 2, i / 2 + 1, getWidth() - i - 1, getHeight() - i - 1, R, R);
                }
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, W, H, R, R);
                Component comp = getComponentCount() > 0 ? getComponent(0) : null;
                if (comp != null) {
                    g2.setColor(headerBg);
                    g2.fillRoundRect(0, 0, W, R + comp.getHeight(), R, R);
                    g2.fillRect(0, R, W, comp.getHeight() - R);
                }
                g2.setColor(AppColors.BORDER_HOVER);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(0, 0, W-1, H-1, R, R);
                g2.dispose();
            }
        };
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(0, 0, 9, 9));
        dlg.add(root);

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(headerBg);
        header.setBorder(new EmptyBorder(12, 16, 12, 10));
        header.setOpaque(false);

        JLabel headerTitle = new JLabel(isEdit ? "編輯任務" : "新增任務");
        headerTitle.setFont(AppFonts.TITLE_SMALL);
        headerTitle.setForeground(AppColors.ACCENT);

        JButton closeBtn = new JButton("×");
        closeBtn.setFont(new Font(AppFonts.BODY_MEDIUM.getFamily(), Font.PLAIN, 16));
        closeBtn.setForeground(AppColors.TEXT_TERTIARY);
        closeBtn.setBorder(new EmptyBorder(0, 8, 0, 4));
        closeBtn.setFocusPainted(false);
        closeBtn.setContentAreaFilled(false);
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> dlg.dispose());
        header.add(headerTitle, BorderLayout.CENTER);
        header.add(closeBtn,    BorderLayout.EAST);

        LocalDate initDate = isEdit
                ? (editTask.hasDeadline() && !editTask.getDate().isEmpty()
                   ? LocalDate.parse(editTask.getDate()) : LocalDate.now())
                : (defaultDate != null ? defaultDate : LocalDate.now());

        JTextField titleField = new JTextField(isEdit ? editTask.getTitle() : "");
        titleField.setFont(AppFonts.BODY_MEDIUM);
        titleField.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(AppColors.BORDER_DEFAULT, 1, true),
            new EmptyBorder(5, 8, 5, 8)));

        JTextArea descArea = new JTextArea(isEdit ? editTask.getDescription() : "", 4, 0);
        descArea.setFont(AppFonts.BODY_SMALL);
        descArea.setLineWrap(true);
        descArea.setWrapStyleWord(true);
        descArea.setBorder(new EmptyBorder(6, 8, 6, 8));
        JScrollPane descScroll = new JScrollPane(descArea,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        descScroll.setBorder(new LineBorder(AppColors.BORDER_DEFAULT, 1, true));
        descScroll.setPreferredSize(new Dimension(0, 86));
        descScroll.setMinimumSize(new Dimension(0, 86));
        AppUIManager.applySlimScrollBar(descScroll);

        // ── 分類下拉 ──
        java.util.List<String> catOptions = new java.util.ArrayList<>();
        catOptions.add("（未分類）");
        catOptions.addAll(categoryManager.getCategories());
        JComboBox<String> catCombo = new JComboBox<>(catOptions.toArray(new String[0]));
        SchedulePanel.applyComboStyle(catCombo);
        if (isEdit && !editTask.getCategory().isEmpty()) {
            catCombo.setSelectedItem(editTask.getCategory());
        }

        int initH = 9, initM = 0;
        if (isEdit && !editTask.getTime().isEmpty()) {
            String[] tp = editTask.getTime().split(":");
            try { initH = Integer.parseInt(tp[0]); initM = Integer.parseInt(tp[1]); }
            catch (NumberFormatException ignored) {}
        }

        final LocalDate[] selectedDate = { initDate };
        final int[]       selectedTime = { initH, initM };

        DateTimeFormatter btnDateFmt = DateTimeFormatter.ofPattern("yyyy/MM/dd");
        JButton dateBtn = pickerBtn(initDate.format(btnDateFmt));
        JButton timeBtn = pickerBtn(String.format("%02d:%02d", initH, initM));

        dateBtn.addActionListener(e ->
            AppUIManager.showDatePicker(dateBtn, selectedDate[0], date -> {
                selectedDate[0] = date;
                dateBtn.setText(date.format(btnDateFmt));
            })
        );
        timeBtn.addActionListener(e ->
            AppUIManager.showTimePicker(timeBtn, selectedTime[0], selectedTime[1], (h, m) -> {
                selectedTime[0] = h; selectedTime[1] = m;
                timeBtn.setText(String.format("%02d:%02d", h, m));
            })
        );

        JPanel dateRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        dateRow.setOpaque(false);
        dateRow.add(dlgLabel("日期")); dateRow.add(dateBtn);

        JPanel timeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        timeRow.setOpaque(false);
        timeRow.add(dlgLabel("時間")); timeRow.add(timeBtn);

        JPanel dtPanel = new JPanel();
        dtPanel.setLayout(new BoxLayout(dtPanel, BoxLayout.Y_AXIS));
        dtPanel.setOpaque(false);
        dtPanel.add(dateRow);
        dtPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        dtPanel.add(timeRow);

        JCheckBox importantCheck = new JCheckBox("標記為重要任務", isEdit && editTask.isImportant());
        importantCheck.setFont(AppFonts.BODY_SMALL);
        importantCheck.setForeground(AppColors.DANGER);
        importantCheck.setOpaque(false);

        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(14, 16, 10, 16));

        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0; gc.weightx = 1.0;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.anchor = GridBagConstraints.WEST;

        gc.gridy = 0; gc.insets = new Insets(0, 0, 4, 0);
        content.add(dlgFieldLabel("標題"), gc);
        gc.gridy = 1; gc.insets = new Insets(0, 0, 12, 0);
        content.add(titleField, gc);
        gc.gridy = 2; gc.insets = new Insets(0, 0, 4, 0);
        content.add(dlgFieldLabel("說明"), gc);
        gc.gridy = 3; gc.insets = new Insets(0, 0, 12, 0);
        content.add(descScroll, gc);
        gc.gridy = 4; gc.insets = new Insets(0, 0, 4, 0);
        content.add(dlgFieldLabel("分類"), gc);
        gc.gridy = 5; gc.insets = new Insets(0, 0, 12, 0);
        content.add(catCombo, gc);
        gc.gridy = 6; gc.insets = new Insets(0, 0, 4, 0);
        content.add(dlgFieldLabel("截止日期與時間"), gc);
        gc.gridy = 7; gc.insets = new Insets(0, 0, 12, 0);
        content.add(dtPanel, gc);
        gc.gridy = 8; gc.insets = new Insets(0, 0, 0, 0);
        content.add(importantCheck, gc);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10));
        btnRow.setBackground(new Color(0xFAF9F7));
        btnRow.setOpaque(true);
        btnRow.setBorder(new MatteBorder(1, 0, 0, 0, AppColors.BORDER_DEFAULT));

        JButton cancelBtn = new JButton("取消");
        cancelBtn.setFont(AppFonts.BODY_SMALL);
        cancelBtn.setForeground(AppColors.TEXT_SECONDARY);
        cancelBtn.setBackground(AppColors.BG_TERTIARY);
        cancelBtn.setOpaque(true);
        cancelBtn.setBorder(new EmptyBorder(6, 16, 6, 16));
        cancelBtn.setFocusPainted(false);
        cancelBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JButton okBtn = new JButton(isEdit ? "儲存變更" : "新增任務");
        okBtn.setFont(AppFonts.BODY_SMALL);
        okBtn.setBackground(AppColors.ACCENT);
        okBtn.setForeground(Color.WHITE);
        okBtn.setBorder(new EmptyBorder(6, 18, 6, 18));
        okBtn.setFocusPainted(false);
        okBtn.setOpaque(true);
        okBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        btnRow.add(cancelBtn);
        btnRow.add(okBtn);

        root.add(header,  BorderLayout.NORTH);
        root.add(content, BorderLayout.CENTER);
        root.add(btnRow,  BorderLayout.SOUTH);

        dlg.pack();
        dlg.setSize(400, dlg.getPreferredSize().height);
        AppUIManager.applyRoundedWindowShape(dlg, 16);
        dlg.setLocationRelativeTo(this);

        cancelBtn.addActionListener(e -> dlg.dispose());
        dlg.getRootPane().setDefaultButton(okBtn);

        okBtn.addActionListener(e -> {
            String titleVal = titleField.getText().trim();
            if (titleVal.isEmpty()) {
                titleField.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(AppColors.DANGER, 1, true),
                    new EmptyBorder(5, 8, 5, 8)));
                titleField.requestFocus();
                return;
            }
            LocalDate chosenDate = selectedDate[0];
            String dateVal = chosenDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
            String timeVal = String.format("%02d:%02d", selectedTime[0], selectedTime[1]);

            // 取得分類
            String selectedCat = (String) catCombo.getSelectedItem();
            if ("（未分類）".equals(selectedCat)) selectedCat = "";

            if (isEdit) {
                editTask.setTitle(titleVal);
                editTask.setDescription(descArea.getText().trim());
                editTask.setHasDeadline(true);
                editTask.setDate(dateVal);
                editTask.setTime(timeVal);
                editTask.setImportant(importantCheck.isSelected());
                editTask.setCategory(selectedCat);
                remindedIds.remove(editTask.getId());
            } else {
                int nextId = tasks.isEmpty() ? 1
                        : tasks.stream().mapToInt(Task::getId).max().orElse(0) + 1;
                Task t = new Task(nextId, titleVal, descArea.getText().trim(),
                                  dateVal, timeVal, true);
                t.setImportant(importantCheck.isSelected());
                t.setCategory(selectedCat);
                tasks.add(t);
            }
            dlg.dispose();
            updateCalendar();
            saveCallback.run();
        });

        dlg.setVisible(true);
    }

    private static JLabel dlgFieldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(AppFonts.LABEL);
        l.setForeground(AppColors.TEXT_SECONDARY);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private static JLabel dlgLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(AppFonts.BODY_SMALL);
        l.setForeground(AppColors.TEXT_SECONDARY);
        return l;
    }

    private JButton pickerBtn(String text) {
        JButton b = new JButton(text);
        b.setFont(AppFonts.BODY_SMALL);
        b.setForeground(AppColors.TEXT_PRIMARY);
        b.setBackground(Color.WHITE);
        b.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(AppColors.BORDER_DEFAULT, 1, true),
            new EmptyBorder(5, 10, 5, 10)));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setOpaque(true);
        return b;
    }

    // ── 更新顯示（套用篩選） ──────────────────────────────────────────────────
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

            JPanel container = taskContainers[i];
            container.removeAll();
            container.setBackground(bgColor);

            // 無截止日任務只顯示在今天
            if (isToday) {
                tasks.stream()
                     .filter(t -> !t.hasDeadline())
                     .filter(t -> matchFilter(t))
                     .sorted(Comparator.comparing(Task::getTitle))
                     .forEach(t -> container.add(buildTaskCard(t, idx)));
            }
            // 有截止日任務
            tasks.stream()
                 .filter(t -> t.hasDeadline() && dateStr.equals(t.getDate()))
                 .filter(t -> matchFilter(t))
                 .sorted(Comparator.comparing(Task::getTime))
                 .forEach(t -> container.add(buildTaskCard(t, idx)));

            container.revalidate();
            container.repaint();
        }
    }

    /** 判斷任務是否符合目前篩選條件 */
    private boolean matchFilter(Task t) {
        if (CategoryManager.ALL.equals(currentFilter)) return true;
        return currentFilter.equals(t.getCategory());
    }

    private static String toHex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
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
