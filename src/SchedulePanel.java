import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

/**
 * SchedulePanel：課表功能主面板。
 *  1. 頂部控制列：[下拉選單] [新增課表] [刪除] [重新命名]，七天格子撐滿寬度
 *  2. 刪除採 inline [取消][確認刪除]，無彈窗，右鍵選單移除
 *  3. 所有 Dialog 改為無邊框圓角+陰影懸浮風格（與 CalendarPanel 一致）
 *  4. 課程卡懸浮窗保留原風格、支援自動拉高 + scrollbar
 *  5. 課表格子加 scrollbar 可上下拉動，節次格高度放大
 *  6. 課程卡懸浮窗標題移除 [課程] 前綴
 *  7. 課代號/教室/教授欄位限制 10 字以內
 */
public class SchedulePanel extends JPanel {

    private static final String[] PERIOD_TIMES = {
        "第0節 06:20", "第1節 08:20", "第2節 09:20", "第3節 10:20",
        "第4節 11:15", "第5節 12:10", "第6節 13:10", "第7節 14:10",
        "第8節 15:10", "第9節 16:05", "第10節 17:30", "第11節 18:30",
        "第12節 19:25", "第13節 20:20", "第14節 21:15"
    };
    private static final String[] DAY_NAMES  = {"一", "二", "三", "四", "五", "六", "日"};
    private static final int[]    COL_TO_DAY = {1, 2, 3, 4, 5, 6, 7};

    private static final Color[] CARD_COLORS = {
        new Color(0xEEF2FF), new Color(0xF0FFF4), new Color(0xFFF9DB),
        new Color(0xFFF5F5), new Color(0xF3F0FF), new Color(0xE3FAFC),
    };
    private static final Color[] CARD_BORDER_COLORS = {
        new Color(0x3B5BDB), new Color(0x2F9E44), new Color(0xE67700),
        new Color(0xC92A2A), new Color(0x7048E8), new Color(0x0C8599),
    };

    // 每節格子高度（放大，讓 scrollbar 有意義）
    private static final int ROW_HEIGHT = 64;

    private final List<Schedule> schedules;
    private final Runnable       saveCallback;
    private Schedule             activeSchedule = null;

    private final JPanel gridPanel      = new JPanel();
    private final JLabel gridTitleLabel = new JLabel("請選擇或新增課表", SwingConstants.LEFT);

    private JComboBox<Schedule> scheduleCombo;
    private JButton             deleteBtn;
    private JButton             renameBtn;
    private JButton             addCourseBtn;
    private JPanel              topControlPanel;
    private CardLayout          topCardLayout;

    private JPanel currentPopover = null;
    private boolean comboUpdating = false;

    public SchedulePanel(List<Schedule> schedules, Runnable saveCallback) {
        this.schedules    = schedules;
        this.saveCallback = saveCallback;

        for (Schedule s : schedules) {
            if (s.isActive()) { activeSchedule = s; break; }
        }
        if (activeSchedule == null && !schedules.isEmpty()) {
            activeSchedule = schedules.get(0);
            activeSchedule.setActive(true);
        }

        setLayout(new BorderLayout(0, 0));
        setBackground(AppColors.BG_SECONDARY);

        add(buildTopNav(),   BorderLayout.NORTH);
        add(buildGridPane(), BorderLayout.CENTER);

        refreshGrid();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 頂部列
    // ══════════════════════════════════════════════════════════════════════════
    private JPanel buildTopNav() {
        JPanel nav = new JPanel(new BorderLayout());
        nav.setBackground(AppColors.BG_SECONDARY);
        nav.setBorder(new EmptyBorder(10, 16, 8, 16));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.setOpaque(false);

        JLabel myLabel = new JLabel("我的課表：");
        myLabel.setFont(AppFonts.BODY_SMALL);
        myLabel.setForeground(AppColors.TEXT_SECONDARY);

        scheduleCombo = new JComboBox<Schedule>() {
            @Override public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.width = Math.max(d.width, 120);
                return d;
            }
        };
        scheduleCombo.setFont(AppFonts.BODY_SMALL);
        scheduleCombo.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(
                    JList<?> list, Object value, int idx, boolean sel, boolean foc) {
                super.getListCellRendererComponent(list, value, idx, sel, foc);
                setText(value instanceof Schedule ? ((Schedule) value).getName() : "（無課表）");
                setFont(AppFonts.BODY_SMALL);
                setBorder(new EmptyBorder(4, 8, 4, 8));
                return this;
            }
        });
        rebuildCombo();
        scheduleCombo.addActionListener(e -> {
            if (comboUpdating) return;
            Schedule sel = (Schedule) scheduleCombo.getSelectedItem();
            if (sel != null && sel != activeSchedule) switchSchedule(sel);
        });

        JButton addScheduleBtn = topBtn("新增課表", AppColors.ACCENT_LIGHT, AppColors.ACCENT);
        addScheduleBtn.addActionListener(e -> showScheduleDialog(null));

        topCardLayout   = new CardLayout();
        topControlPanel = new JPanel(topCardLayout);
        topControlPanel.setOpaque(false);

        JPanel normalCtrl = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        normalCtrl.setOpaque(false);
        deleteBtn = topBtn("刪除",    AppColors.BG_TERTIARY, AppColors.DANGER);
        renameBtn = topBtn("重新命名", AppColors.BG_TERTIARY, AppColors.TEXT_PRIMARY);
        deleteBtn.addActionListener(e -> {
            if (activeSchedule != null) topCardLayout.show(topControlPanel, "confirm");
        });
        renameBtn.addActionListener(e -> {
            if (activeSchedule != null) showScheduleDialog(activeSchedule);
        });
        normalCtrl.add(deleteBtn);
        normalCtrl.add(renameBtn);

        JPanel confirmCtrl = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        confirmCtrl.setOpaque(false);
        JButton cancelDelBtn  = topBtn("取消",    AppColors.BG_TERTIARY, AppColors.TEXT_SECONDARY);
        JButton confirmDelBtn = topBtn("確認刪除", AppColors.DANGER,      Color.WHITE);
        cancelDelBtn.addActionListener(e -> topCardLayout.show(topControlPanel, "normal"));
        confirmDelBtn.addActionListener(e -> {
            if (activeSchedule != null) {
                schedules.remove(activeSchedule);
                activeSchedule = schedules.isEmpty() ? null : schedules.get(0);
                if (activeSchedule != null) activeSchedule.setActive(true);
                topCardLayout.show(topControlPanel, "normal");
                rebuildCombo();
                refreshScheduleButtons();
                refreshGrid();
                saveCallback.run();
            }
        });
        confirmCtrl.add(cancelDelBtn);
        confirmCtrl.add(confirmDelBtn);

        topControlPanel.add(normalCtrl,  "normal");
        topControlPanel.add(confirmCtrl, "confirm");
        topCardLayout.show(topControlPanel, "normal");

        left.add(myLabel);
        left.add(scheduleCombo);
        left.add(addScheduleBtn);
        left.add(topControlPanel);

        addCourseBtn = new JButton("+ 新增課程");
        addCourseBtn.setFont(AppFonts.BODY_SMALL);
        addCourseBtn.setBackground(AppColors.ACCENT);
        addCourseBtn.setForeground(Color.WHITE);
        addCourseBtn.setBorder(new EmptyBorder(7, 16, 7, 16));
        addCourseBtn.setFocusPainted(false);
        addCourseBtn.setOpaque(true);
        addCourseBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addCourseBtn.addActionListener(e -> {
            if (activeSchedule == null) {
                JOptionPane.showMessageDialog(this, "請先新增或選擇一份課表。", "提示",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            showCourseDialog(null);
        });

        nav.add(left,         BorderLayout.WEST);
        nav.add(addCourseBtn, BorderLayout.EAST);
        refreshScheduleButtons();
        return nav;
    }

    private JButton topBtn(String text, Color bg, Color fg) {
        JButton b = new JButton(text);
        b.setFont(AppFonts.BODY_SMALL);
        b.setBackground(bg);
        b.setForeground(fg);
        b.setBorder(new EmptyBorder(5, 10, 5, 10));
        b.setFocusPainted(false);
        b.setOpaque(true);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private void rebuildCombo() {
        comboUpdating = true;
        scheduleCombo.removeAllItems();
        for (Schedule s : schedules) scheduleCombo.addItem(s);
        if (activeSchedule != null) scheduleCombo.setSelectedItem(activeSchedule);
        comboUpdating = false;
    }

    private void refreshScheduleButtons() {
        boolean has = activeSchedule != null;
        if (deleteBtn    != null) deleteBtn.setEnabled(has);
        if (renameBtn    != null) renameBtn.setEnabled(has);
        if (addCourseBtn != null) {
            addCourseBtn.setEnabled(has);
            addCourseBtn.setBackground(has ? AppColors.ACCENT : AppColors.BG_TERTIARY);
            addCourseBtn.setForeground(has ? Color.WHITE : AppColors.TEXT_TERTIARY);
            addCourseBtn.setCursor(Cursor.getPredefinedCursor(
                    has ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 課表格子區（圓角外框 + 垂直 ScrollBar）
    // ══════════════════════════════════════════════════════════════════════════
    private JPanel buildGridPane() {
        JPanel wrapper = new JPanel(new BorderLayout(0, 0));
        wrapper.setBackground(AppColors.BG_SECONDARY);
        wrapper.setBorder(new EmptyBorder(0, 16, 16, 16));

        JPanel card = new JPanel(new BorderLayout(0, 0)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int W = getWidth() - 7, H = getHeight() - 7, R = 12;
                g2.setColor(new Color(0, 0, 0, 18));
                g2.fillRoundRect(5, 7, getWidth() - 5, getHeight() - 5, R, R);
                g2.setColor(AppColors.BG_PRIMARY);
                g2.fillRoundRect(0, 0, W, H, R, R);
                g2.setColor(AppColors.BORDER_DEFAULT);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, W - 1, H - 1, R, R);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(4, 4, 10, 8));

        gridPanel.setLayout(new BorderLayout(0, 0));
        gridPanel.setOpaque(false);

        JScrollPane sp = new JScrollPane(gridPanel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(null);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(ROW_HEIGHT);
        AppUIManager.applySlimScrollBar(sp);

        card.add(sp, BorderLayout.CENTER);
        wrapper.add(card, BorderLayout.CENTER);
        return wrapper;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 更新週課表格子
    // ══════════════════════════════════════════════════════════════════════════
    public void refreshGrid() {
        closePopover();
        gridPanel.removeAll();

        if (activeSchedule == null) {
            JLabel hint = new JLabel("請點擊上方「新增課表」建立課表，再新增課程", SwingConstants.CENTER);
            hint.setFont(AppFonts.BODY_SMALL);
            hint.setForeground(AppColors.TEXT_TERTIARY);
            gridPanel.add(hint, BorderLayout.CENTER);
            gridTitleLabel.setText("課表");
            gridPanel.revalidate();
            gridPanel.repaint();
            return;
        }

        gridTitleLabel.setText(activeSchedule.getName());

        final int ROWS = PERIOD_TIMES.length;
        final int COLS = DAY_NAMES.length;

        JPanel table = new JPanel(new GridBagLayout());
        table.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.BOTH;

        // 表頭列
        gc.gridy = 0; gc.weighty = 0; gc.gridheight = 1;
        gc.gridx = 0; gc.weightx = 0.055;
        table.add(headerCell(""), gc);
        for (int d = 0; d < COLS; d++) {
            boolean isWeekend = (d == 5 || d == 6);
            gc.gridx = d + 1;
            gc.weightx = (1.0 - 0.055) / COLS;
            JPanel h = headerCell("星期" + DAY_NAMES[d]);
            if (isWeekend) {
                h.setBackground(new Color(0xFFF5F5));
                ((JLabel) h.getComponent(0)).setForeground(AppColors.DANGER);
            }
            table.add(h, gc);
        }

        // 節次列（固定高度）
        boolean[][] occupied = new boolean[ROWS][COLS];
        for (int p = 0; p < ROWS; p++) {
            gc.gridy  = p + 1;
            gc.weighty = 0;

            gc.gridx = 0; gc.weightx = 0.055; gc.gridheight = 1;
            table.add(periodCell(PERIOD_TIMES[p]), gc);

            for (int d = 0; d < COLS; d++) {
                if (occupied[p][d]) continue;
                int colDay = COL_TO_DAY[d];
                gc.gridx   = d + 1;
                gc.weightx = (1.0 - 0.055) / COLS;

                Course found = null;
                for (Course c : activeSchedule.getCourses()) {
                    if (c.getDayOfWeek() == colDay && c.getStartPeriod() == p) {
                        found = c; break;
                    }
                }

                if (found != null) {
                    int span = found.getEndPeriod() - found.getStartPeriod() + 1;
                    for (int sp = p; sp < p + span && sp < ROWS; sp++) occupied[sp][d] = true;
                    gc.gridheight = span;
                    table.add(buildCourseCard(found), gc);
                    gc.gridheight = 1;
                } else {
                    occupied[p][d] = true;
                    boolean inRange = false;
                    for (Course c : activeSchedule.getCourses()) {
                        if (c.getDayOfWeek() == colDay
                                && p > c.getStartPeriod() && p <= c.getEndPeriod()) {
                            inRange = true; break;
                        }
                    }
                    if (!inRange) {
                        gc.gridheight = 1;
                        table.add(buildEmptyCell(d == 5 || d == 6), gc);
                    }
                }
            }
        }

        gridPanel.add(table, BorderLayout.NORTH);
        gridPanel.revalidate();
        gridPanel.repaint();
    }

    private JPanel buildEmptyCell(boolean weekend) {
        JPanel cell = new JPanel();
        cell.setOpaque(true);
        cell.setBackground(weekend ? new Color(0xFFFCFC) : AppColors.BG_PRIMARY);
        cell.setBorder(new MatteBorder(0, 0, 1, 1, AppColors.BORDER_DEFAULT));
        cell.setMinimumSize(new Dimension(0, ROW_HEIGHT));
        cell.setPreferredSize(new Dimension(0, ROW_HEIGHT));
        return cell;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 課程卡
    // ══════════════════════════════════════════════════════════════════════════
    private JPanel buildCourseCard(Course c) {
        int   colorIdx = (c.getId() - 1) % CARD_COLORS.length;
        Color bgColor  = CARD_COLORS[colorIdx];
        Color accColor = CARD_BORDER_COLORS[colorIdx];

        JPanel card = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bgColor);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(accColor);
                g2.fillRect(0, 0, 4, getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setOpaque(false);
        card.setBorder(BorderFactory.createCompoundBorder(
            new MatteBorder(0, 0, 1, 1, AppColors.BORDER_DEFAULT),
            new EmptyBorder(4, 8, 4, 5)
        ));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        card.setMinimumSize(new Dimension(0, ROW_HEIGHT));

        JPanel inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        inner.setOpaque(false);

        JTextArea nameLbl = new JTextArea(c.getName());
        nameLbl.setFont(AppFonts.BODY_SMALL.deriveFont(Font.BOLD));
        nameLbl.setForeground(accColor.darker());
        nameLbl.setOpaque(false);
        nameLbl.setEditable(false);
        nameLbl.setFocusable(false);
        nameLbl.setLineWrap(true);
        nameLbl.setWrapStyleWord(true);
        nameLbl.setBorder(null);
        nameLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        MouseAdapter fwd = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e)  { card.dispatchEvent(SwingUtilities.convertMouseEvent(nameLbl, e, card)); }
            @Override public void mouseEntered(MouseEvent e)  { card.dispatchEvent(SwingUtilities.convertMouseEvent(nameLbl, e, card)); }
            @Override public void mouseExited(MouseEvent e)   { card.dispatchEvent(SwingUtilities.convertMouseEvent(nameLbl, e, card)); }
            @Override public void mousePressed(MouseEvent e)  { card.dispatchEvent(SwingUtilities.convertMouseEvent(nameLbl, e, card)); }
            @Override public void mouseReleased(MouseEvent e) { card.dispatchEvent(SwingUtilities.convertMouseEvent(nameLbl, e, card)); }
        };
        nameLbl.addMouseListener(fwd);
        inner.add(nameLbl);

        if (!c.getLocation().isEmpty()) {
            JLabel locLbl = new JLabel(c.getLocation());
            locLbl.setFont(AppFonts.CAPTION);
            locLbl.setForeground(AppColors.TEXT_SECONDARY);
            locLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
            inner.add(locLbl);
        }

        card.add(inner, BorderLayout.NORTH);
        card.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { showCoursePopover(c, card); }
        });
        return card;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Popover 管理
    // ══════════════════════════════════════════════════════════════════════════
    private void closePopover() {
        if (currentPopover == null) return;
        JRootPane root = SwingUtilities.getRootPane(this);
        if (root != null) { root.getLayeredPane().remove(currentPopover); root.getLayeredPane().repaint(); }
        currentPopover = null;
    }

    private void showCoursePopover(Course course, Component anchor) {
        closePopover();
        JRootPane rootPane = SwingUtilities.getRootPane(this);
        if (rootPane == null) return;
        JLayeredPane layered = rootPane.getLayeredPane();

        currentPopover = buildCoursePopover(course);
        int popW = 290;
        currentPopover.setSize(popW, 9999);
        int popH = Math.min(currentPopover.getPreferredSize().height, layered.getHeight() - 20);

        Point al = SwingUtilities.convertPoint(anchor, 0, 0, layered);
        int x = al.x + anchor.getWidth() + 4;
        int y = al.y;
        if (x + popW > layered.getWidth())  x = al.x - popW - 4;
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
                    if (me.getID() == MouseEvent.MOUSE_PRESSED && currentPopover != null) {
                        Point p = SwingUtilities.convertPoint(me.getComponent(), me.getPoint(), currentPopover);
                        if (!currentPopover.contains(p)) {
                            closePopover();
                            Toolkit.getDefaultToolkit().removeAWTEventListener(this);
                        }
                    }
                }
            }
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(closer, AWTEvent.MOUSE_EVENT_MASK);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 課程 Popover（標題去掉 [課程] 前綴）
    // ══════════════════════════════════════════════════════════════════════════
    private JPanel buildCoursePopover(Course course) {
        int   colorIdx = (course.getId() - 1) % CARD_COLORS.length;
        Color headerBg  = CARD_COLORS[colorIdx];
        Color accentCol = CARD_BORDER_COLORS[colorIdx];

        JPanel pop = new JPanel(new BorderLayout(0, 0));
        pop.setBackground(Color.WHITE);
        pop.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(AppColors.BORDER_DEFAULT, 1, true),
            new EmptyBorder(0, 0, 0, 0)));
        pop.setOpaque(true);

        // Header（標題去掉 [課程] 前綴）
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(headerBg);
        header.setBorder(new EmptyBorder(10, 14, 10, 10));

        JTextArea titleLbl = new JTextArea(course.getName()); // 移除 "[課程]  "
        titleLbl.setFont(AppFonts.BODY_MEDIUM.deriveFont(Font.BOLD));
        titleLbl.setForeground(accentCol.darker());
        titleLbl.setBackground(headerBg);
        titleLbl.setEditable(false);
        titleLbl.setFocusable(false);
        titleLbl.setLineWrap(true);
        titleLbl.setWrapStyleWord(true);
        titleLbl.setOpaque(false);
        titleLbl.setBorder(null);

        JButton closeBtn = new JButton("x");
        closeBtn.setFont(new Font(AppFonts.CAPTION.getFamily(), Font.PLAIN, 11));
        closeBtn.setForeground(AppColors.TEXT_TERTIARY);
        closeBtn.setBorder(new EmptyBorder(2, 6, 2, 6));
        closeBtn.setFocusPainted(false);
        closeBtn.setContentAreaFilled(false);
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> closePopover());
        header.add(titleLbl, BorderLayout.CENTER);
        header.add(closeBtn, BorderLayout.EAST);

        // Body
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(Color.WHITE);
        body.setBorder(new EmptyBorder(12, 14, 8, 14));

        String dayName = (course.getDayOfWeek() >= 1 && course.getDayOfWeek() <= 7)
                ? DAY_NAMES[course.getDayOfWeek() - 1] : "?";
        body.add(popRowLabel("[時間]  星期" + dayName + "  第" + course.getStartPeriod() + "～" + course.getEndPeriod() + "節"));
        body.add(Box.createRigidArea(new Dimension(0, 5)));

        if (!course.getCode().isEmpty())       { body.add(popRowLabel("[課代號]  " + course.getCode()));      body.add(Box.createRigidArea(new Dimension(0, 5))); }
        if (!course.getLocation().isEmpty())   { body.add(popRowLabel("[教室]  " + course.getLocation()));    body.add(Box.createRigidArea(new Dimension(0, 5))); }
        if (!course.getDepartment().isEmpty()) { body.add(popRowLabel("[開課系所]  " + course.getDepartment())); body.add(Box.createRigidArea(new Dimension(0, 5))); }
        if (!course.getClassYear().isEmpty())  { body.add(popRowLabel("[開課年班]  " + course.getClassYear()));  body.add(Box.createRigidArea(new Dimension(0, 5))); }

        if (!course.getNote().isEmpty()) {
            body.add(popRowLabel("[備註]"));
            body.add(Box.createRigidArea(new Dimension(0, 3)));
            JTextArea noteArea = new JTextArea(course.getNote());
            noteArea.setFont(AppFonts.BODY_SMALL);
            noteArea.setForeground(AppColors.TEXT_PRIMARY);
            noteArea.setBackground(AppColors.BG_SECONDARY);
            noteArea.setEditable(false);
            noteArea.setLineWrap(true);
            noteArea.setWrapStyleWord(true);
            noteArea.setBorder(new EmptyBorder(6, 8, 6, 8));
            JScrollPane noteSp = new JScrollPane(noteArea,
                    JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            noteSp.setBorder(null);
            noteSp.setAlignmentX(Component.LEFT_ALIGNMENT);
            noteSp.setPreferredSize(new Dimension(252, 72));
            noteSp.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));
            noteSp.getVerticalScrollBar().setUnitIncrement(12);
            AppUIManager.applySlimScrollBar(noteSp);
            body.add(noteSp);
            body.add(Box.createRigidArea(new Dimension(0, 5)));
        }

        JScrollPane bodyScroll = new JScrollPane(body,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        bodyScroll.setBorder(null);
        bodyScroll.getVerticalScrollBar().setUnitIncrement(16);
        AppUIManager.applySlimScrollBar(bodyScroll);

        // 按鈕列
        CardLayout btnCard = new CardLayout();
        JPanel btnRow = new JPanel(btnCard);
        btnRow.setBackground(new Color(0xFAF9F7));
        btnRow.setBorder(new MatteBorder(1, 0, 0, 0, AppColors.BORDER_DEFAULT));

        JButton editBtn = popBtn("編輯", AppColors.ACCENT, Color.WHITE);
        JButton delBtn  = popBtn("刪除", AppColors.BG_TERTIARY, AppColors.DANGER);
        JPanel normalPane = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 8));
        normalPane.setOpaque(false); normalPane.add(delBtn); normalPane.add(editBtn);

        JButton cancelDelBtn  = popBtn("取消",    AppColors.BG_TERTIARY, AppColors.TEXT_SECONDARY);
        JButton confirmDelBtn = popBtn("確認刪除", AppColors.DANGER, Color.WHITE);
        JPanel confirmPane = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 8));
        confirmPane.setOpaque(false); confirmPane.add(cancelDelBtn); confirmPane.add(confirmDelBtn);

        btnRow.add(normalPane,  "normal");
        btnRow.add(confirmPane, "confirm");
        btnCard.show(btnRow, "normal");

        editBtn.addActionListener(e -> { closePopover(); showCourseDialog(course); });
        delBtn.addActionListener(e -> btnCard.show(btnRow, "confirm"));
        cancelDelBtn.addActionListener(e -> btnCard.show(btnRow, "normal"));
        confirmDelBtn.addActionListener(e -> { activeSchedule.getCourses().remove(course); closePopover(); refreshGrid(); saveCallback.run(); });

        pop.add(header,     BorderLayout.NORTH);
        pop.add(bodyScroll, BorderLayout.CENTER);
        pop.add(btnRow,     BorderLayout.SOUTH);
        return pop;
    }

    private JLabel popRowLabel(String text) {
        // 將 [標籤] 與後面的內容分開上色：標籤灰色，內容深色
        String html;
        int bracketEnd = text.indexOf(']');
        if (bracketEnd >= 0 && bracketEnd < text.length() - 1) {
            String tag     = text.substring(0, bracketEnd + 1);
            String content = text.substring(bracketEnd + 1);
            String tagHex     = Integer.toHexString(AppColors.TEXT_TERTIARY.getRGB() & 0xFFFFFF);
            String contentHex = Integer.toHexString(AppColors.TEXT_PRIMARY.getRGB() & 0xFFFFFF);
            html = "<html><span style='color:#" + tagHex + "'>" + tag + "</span>"
                 + "<span style='color:#" + contentHex + "'>" + content + "</span></html>";
        } else {
            html = text;
        }
        JLabel l = new JLabel(html);
        l.setFont(AppFonts.BODY_SMALL);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private JButton popBtn(String text, Color bg, Color fg) {
        JButton b = new JButton(text);
        b.setFont(AppFonts.BODY_SMALL);
        b.setBackground(bg); b.setForeground(fg);
        b.setBorder(new EmptyBorder(5, 14, 5, 14));
        b.setFocusPainted(false); b.setOpaque(true);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 懸浮圓角 Dialog 基礎工廠（與 CalendarPanel 完全一致的風格）
    // ══════════════════════════════════════════════════════════════════════════
    private JPanel buildFloatingRoot(JDialog dlg, String title, Color accentColor) {
        dlg.setUndecorated(true);
        dlg.setLayout(new BorderLayout());
        // 修正 IME（中文輸入法）白屏 bug：
        // setBackground(transparent) 在有輸入法組字時會導致整個視窗白屏。
        // 改為不透明背景；圓角與陰影完全由內層 JPanel 的 paintComponent 繪製。
        dlg.setBackground(new Color(0xF0F0F0));

        Color headerBg = blendWithWhite(accentColor, 0.10f);

        JPanel root = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // 先用外層背景色填滿（配合 dlg 背景，使圓角外的角落不顯眼）
                g2.setColor(new Color(0xF0F0F0));
                g2.fillRect(0, 0, getWidth(), getHeight());
                int W = getWidth() - 7, H = getHeight() - 7, R = 14;
                // 陰影（多層漸層）
                for (int i = 4; i >= 1; i--) {
                    g2.setColor(new Color(0, 0, 0, 7 * i));
                    g2.fillRoundRect(i + 1, i + 2, getWidth() - i * 2 - 1, getHeight() - i * 2 - 1, R, R);
                }
                // 白底
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, W, H, R, R);
                // Header 色帶
                Component comp = getComponentCount() > 0 ? getComponent(0) : null;
                if (comp != null) {
                    int hh = comp.getHeight();
                    g2.setColor(headerBg);
                    g2.fillRoundRect(0, 0, W, R + hh, R, R);
                    g2.fillRect(0, R, W, hh - R);
                }
                // 邊框
                g2.setColor(AppColors.BORDER_HOVER);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(0, 0, W - 1, H - 1, R, R);
                g2.dispose();
            }
        };
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(0, 0, 7, 7));
        dlg.add(root);

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(12, 16, 12, 10));

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(AppFonts.TITLE_SMALL);
        titleLbl.setForeground(accentColor);

        JButton xBtn = new JButton("×");
        xBtn.setFont(new Font(AppFonts.BODY_MEDIUM.getFamily(), Font.PLAIN, 16));
        xBtn.setForeground(AppColors.TEXT_TERTIARY);
        xBtn.setBorder(new EmptyBorder(0, 8, 0, 4));
        xBtn.setFocusPainted(false);
        xBtn.setContentAreaFilled(false);
        xBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        xBtn.addActionListener(e -> dlg.dispose());
        header.add(titleLbl, BorderLayout.CENTER);
        header.add(xBtn,     BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        return root;
    }

    private JPanel buildDlgBtnRow(JDialog dlg, String okLabel, Color okBg, Runnable onOk) {
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
        cancelBtn.addActionListener(e -> dlg.dispose());

        JButton okBtn = new JButton(okLabel);
        okBtn.setFont(AppFonts.BODY_SMALL);
        okBtn.setBackground(okBg);
        okBtn.setForeground(Color.WHITE);
        okBtn.setBorder(new EmptyBorder(6, 18, 6, 18));
        okBtn.setFocusPainted(false);
        okBtn.setOpaque(true);
        okBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        okBtn.addActionListener(e -> onOk.run());

        btnRow.add(cancelBtn);
        btnRow.add(okBtn);
        dlg.getRootPane().setDefaultButton(okBtn);
        return btnRow;
    }

    private Color blendWithWhite(Color c, float ratio) {
        return new Color(
            (int)(c.getRed()   * ratio + 255 * (1 - ratio)),
            (int)(c.getGreen() * ratio + 255 * (1 - ratio)),
            (int)(c.getBlue()  * ratio + 255 * (1 - ratio))
        );
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 新增 / 重新命名課表 Dialog（圓角懸浮風格）
    // ══════════════════════════════════════════════════════════════════════════
    private void showScheduleDialog(Schedule editSchedule) {
        boolean isEdit = (editSchedule != null);
        Window owner = SwingUtilities.getWindowAncestor(this);

        JDialog dlg = new JDialog(owner, "", Dialog.ModalityType.APPLICATION_MODAL);
        JPanel root = buildFloatingRoot(dlg, isEdit ? "重新命名課表" : "新增課表", AppColors.ACCENT);

        JTextField nameField = new JTextField(isEdit ? editSchedule.getName() : "");
        nameField.setFont(AppFonts.BODY_MEDIUM);
        nameField.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(AppColors.BORDER_DEFAULT, 1, true),
            new EmptyBorder(5, 8, 5, 8)));

        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(14, 16, 10, 16));
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0; gc.weightx = 1.0; gc.fill = GridBagConstraints.HORIZONTAL; gc.anchor = GridBagConstraints.WEST;
        gc.gridy = 0; gc.insets = new Insets(0, 0, 4, 0);  content.add(dlgFieldLabel("課表名稱"), gc);
        gc.gridy = 1; gc.insets = new Insets(0, 0, 0, 0);  content.add(nameField, gc);

        Runnable onOk = () -> {
            String val = nameField.getText().trim();
            if (val.isEmpty()) {
                nameField.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(AppColors.DANGER, 1, true), new EmptyBorder(5, 8, 5, 8)));
                nameField.requestFocus();
                return;
            }
            if (isEdit) {
                editSchedule.setName(val);
                gridTitleLabel.setText(val);
                rebuildCombo();
            } else {
                int nextId = schedules.isEmpty() ? 1
                        : schedules.stream().mapToInt(Schedule::getId).max().orElse(0) + 1;
                Schedule s = new Schedule(nextId, val);
                schedules.add(s);
                switchSchedule(s);
            }
            dlg.dispose();
            refreshScheduleButtons();
            saveCallback.run();
        };

        root.add(content, BorderLayout.CENTER);
        root.add(buildDlgBtnRow(dlg, isEdit ? "儲存" : "新增", AppColors.ACCENT, onOk), BorderLayout.SOUTH);
        dlg.pack();
        dlg.setSize(320 + 7, dlg.getHeight());
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 新增 / 編輯課程 Dialog（圓角懸浮風格）
    // ══════════════════════════════════════════════════════════════════════════
    private void showCourseDialog(Course editCourse) {
        boolean isEdit = (editCourse != null);
        Window owner = SwingUtilities.getWindowAncestor(this);
        Color accentColor = isEdit ? AppColors.ACCENT : AppColors.SUCCESS;

        JDialog dlg = new JDialog(owner, "", Dialog.ModalityType.APPLICATION_MODAL);
        JPanel root = buildFloatingRoot(dlg, isEdit ? "編輯課程" : "新增課程", accentColor);

        JTextField nameField = dlgTextField(isEdit ? editCourse.getName()       : "");
        JTextField codeField = dlgTextField(isEdit ? editCourse.getCode()       : "");
        JTextField locField  = dlgTextField(isEdit ? editCourse.getLocation()   : "");
        JTextField deptField = dlgTextField(isEdit ? editCourse.getDepartment() : "");
        JTextField cyearField= dlgTextField(isEdit ? editCourse.getClassYear()  : "");
        limitLength(codeField,  10);
        limitLength(locField,   10);
        limitLength(deptField,  20);
        limitLength(cyearField, 10);

        JTextArea noteArea = new JTextArea(isEdit ? editCourse.getNote() : "", 3, 0);
        noteArea.setFont(AppFonts.BODY_SMALL);
        noteArea.setLineWrap(true);
        noteArea.setWrapStyleWord(true);
        noteArea.setBorder(new EmptyBorder(6, 8, 6, 8));
        JScrollPane noteScroll = new JScrollPane(noteArea,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        noteScroll.setBorder(new LineBorder(AppColors.BORDER_DEFAULT, 1, true));
        noteScroll.setPreferredSize(new Dimension(0, 68));
        noteScroll.getVerticalScrollBar().setUnitIncrement(12);
        AppUIManager.applySlimScrollBar(noteScroll);

        String[] dayOptions = {"星期一","星期二","星期三","星期四","星期五","星期六","星期日"};
        JComboBox<String> dayBox = new JComboBox<>(dayOptions);
        dayBox.setFont(AppFonts.BODY_SMALL);
        if (isEdit) dayBox.setSelectedIndex(editCourse.getDayOfWeek() - 1);

        Integer[] periods = new Integer[PERIOD_TIMES.length];
        for (int i = 0; i < periods.length; i++) periods[i] = i;
        JComboBox<Integer> startBox = new JComboBox<>(periods);
        JComboBox<Integer> endBox   = new JComboBox<>(periods);
        startBox.setFont(AppFonts.BODY_SMALL);
        endBox  .setFont(AppFonts.BODY_SMALL);
        startBox.setRenderer(periodRenderer());
        endBox  .setRenderer(periodRenderer());
        if (isEdit) { startBox.setSelectedItem(editCourse.getStartPeriod()); endBox.setSelectedItem(editCourse.getEndPeriod()); }
        else        { startBox.setSelectedItem(1); endBox.setSelectedItem(2); }

        JPanel periodRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        periodRow.setOpaque(false);
        periodRow.add(startBox);
        JLabel toLabel = new JLabel(" ～ ");
        toLabel.setFont(AppFonts.BODY_SMALL);
        toLabel.setForeground(AppColors.TEXT_SECONDARY);
        periodRow.add(toLabel);
        periodRow.add(endBox);

        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(14, 16, 10, 16));
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0; gc.weightx = 1.0; gc.fill = GridBagConstraints.HORIZONTAL; gc.anchor = GridBagConstraints.WEST;

        int r = 0;
        gc.gridy = r++; gc.insets = new Insets(0,0,4,0);   content.add(dlgFieldLabel("課程名稱 *"), gc);
        gc.gridy = r++; gc.insets = new Insets(0,0,10,0);  content.add(nameField, gc);
        gc.gridy = r++; gc.insets = new Insets(0,0,4,0);   content.add(dlgFieldLabel("課代號（最多 10 字）"), gc);
        gc.gridy = r++; gc.insets = new Insets(0,0,10,0);  content.add(codeField, gc);
        gc.gridy = r++; gc.insets = new Insets(0,0,4,0);   content.add(dlgFieldLabel("教室（最多 10 字）"), gc);
        gc.gridy = r++; gc.insets = new Insets(0,0,10,0);  content.add(locField, gc);
        gc.gridy = r++; gc.insets = new Insets(0,0,4,0);   content.add(dlgFieldLabel("開課系所"), gc);
        gc.gridy = r++; gc.insets = new Insets(0,0,10,0);  content.add(deptField, gc);
        gc.gridy = r++; gc.insets = new Insets(0,0,4,0);   content.add(dlgFieldLabel("開課年班"), gc);
        gc.gridy = r++; gc.insets = new Insets(0,0,10,0);  content.add(cyearField, gc);
        gc.gridy = r++; gc.insets = new Insets(0,0,4,0);   content.add(dlgFieldLabel("星期"), gc);
        gc.gridy = r++; gc.insets = new Insets(0,0,10,0);  content.add(dayBox, gc);
        gc.gridy = r++; gc.insets = new Insets(0,0,4,0);   content.add(dlgFieldLabel("節次（開始 ～ 結束）"), gc);
        gc.gridy = r++; gc.insets = new Insets(0,0,10,0);  content.add(periodRow, gc);
        gc.gridy = r++; gc.insets = new Insets(0,0,4,0);   content.add(dlgFieldLabel("備註"), gc);
        gc.gridy = r++;  gc.insets = new Insets(0,0,0,0);  content.add(noteScroll, gc);

        // 將 content 包進 JScrollPane，超出高度時可捲動
        JScrollPane contentScroll = new JScrollPane(content,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        contentScroll.setBorder(null);
        contentScroll.setOpaque(false);
        contentScroll.getViewport().setOpaque(false);
        contentScroll.getVerticalScrollBar().setUnitIncrement(16);
        AppUIManager.applySlimScrollBar(contentScroll);

        Runnable onOk = () -> {
            String nameVal = nameField.getText().trim();
            if (nameVal.isEmpty()) {
                nameField.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(AppColors.DANGER, 1, true), new EmptyBorder(5, 8, 5, 8)));
                nameField.requestFocus(); return;
            }
            int day = dayBox.getSelectedIndex() + 1;
            int start = (int) startBox.getSelectedItem();
            int end   = (int) endBox.getSelectedItem();
            if (end < start) { endBox.setBorder(new LineBorder(AppColors.DANGER, 2)); return; }

            if (isEdit) {
                editCourse.setName(nameVal);
                editCourse.setCode(codeField.getText().trim());
                editCourse.setLocation(locField.getText().trim());
                editCourse.setDepartment(deptField.getText().trim());
                editCourse.setClassYear(cyearField.getText().trim());
                editCourse.setDayOfWeek(day);
                editCourse.setStartPeriod(start);
                editCourse.setEndPeriod(end);
                editCourse.setNote(noteArea.getText().trim());
            } else {
                List<Course> courses = activeSchedule.getCourses();
                int nextId = courses.isEmpty() ? 1 : courses.stream().mapToInt(Course::getId).max().orElse(0) + 1;
                Course newCourse = new Course(nextId, nameVal,
                        codeField.getText().trim(), locField.getText().trim(),
                        "", day, start, end,
                        noteArea.getText().trim());
                newCourse.setDepartment(deptField.getText().trim());
                newCourse.setClassYear(cyearField.getText().trim());
                courses.add(newCourse);
            }
            dlg.dispose(); refreshGrid(); saveCallback.run();
        };

        root.add(contentScroll, BorderLayout.CENTER);
        root.add(buildDlgBtnRow(dlg, isEdit ? "儲存變更" : "新增課程", accentColor, onOk), BorderLayout.SOUTH);
        dlg.pack();
        // 寬度固定 347，高度限制在螢幕可用高度的 85% 以內（保留空間給懸浮窗）
        int screenH = java.awt.Toolkit.getDefaultToolkit().getScreenSize().height;
        int maxH    = (int)(screenH * 0.85);
        int dlgW    = 340 + 7;
        int dlgH    = Math.min(dlg.getHeight(), maxH);
        dlg.setSize(dlgW, dlgH);
        // 置中於螢幕（而非相對 panel），確保不超出邊界
        dlg.setLocationRelativeTo(null);
        dlg.setVisible(true);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 切換課表
    // ══════════════════════════════════════════════════════════════════════════
    private void switchSchedule(Schedule s) {
        if (activeSchedule != null) activeSchedule.setActive(false);
        activeSchedule = s;
        s.setActive(true);
        rebuildCombo();
        topCardLayout.show(topControlPanel, "normal");
        refreshScheduleButtons();
        refreshGrid();
        saveCallback.run();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Grid Helpers
    // ══════════════════════════════════════════════════════════════════════════
    private JPanel headerCell(String text) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(AppColors.BG_SECONDARY);
        p.setBorder(new MatteBorder(0, 0, 1, 1, AppColors.BORDER_DEFAULT));
        p.setMinimumSize(new Dimension(0, 36));
        p.setPreferredSize(new Dimension(80, 36)); // 給 GridBagLayout 一個合理基準，避免被課程卡文字撐大
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setFont(AppFonts.LABEL);
        l.setForeground(AppColors.TEXT_SECONDARY);
        p.add(l, BorderLayout.CENTER);
        return p;
    }

    private JPanel periodCell(String text) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(AppColors.BG_SECONDARY);
        p.setBorder(new MatteBorder(0, 0, 1, 1, AppColors.BORDER_DEFAULT));
        p.setMinimumSize(new Dimension(0, ROW_HEIGHT));
        p.setPreferredSize(new Dimension(58, ROW_HEIGHT));
        String[] parts = text.split(" ");
        JPanel inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        inner.setOpaque(false);
        inner.setBorder(new EmptyBorder(4, 4, 4, 4));
        JLabel l1 = new JLabel(parts[0]);
        l1.setFont(AppFonts.CAPTION.deriveFont(Font.BOLD));
        l1.setForeground(AppColors.TEXT_SECONDARY);
        l1.setAlignmentX(Component.CENTER_ALIGNMENT);
        inner.add(l1);
        if (parts.length > 1) {
            JLabel l2 = new JLabel(parts[1]);
            l2.setFont(new Font(AppFonts.CAPTION.getFamily(), Font.PLAIN, 10));
            l2.setForeground(AppColors.TEXT_TERTIARY);
            l2.setAlignmentX(Component.CENTER_ALIGNMENT);
            inner.add(l2);
        }
        p.add(inner, BorderLayout.CENTER);
        return p;
    }

    private ListCellRenderer<Object> periodRenderer() {
        return (list, value, index, isSelected, cellHasFocus) -> {
            int v = (Integer) value;
            JLabel l = new JLabel("第" + v + "節  " + PERIOD_TIMES[v].split(" ")[1]);
            l.setFont(AppFonts.BODY_SMALL);
            l.setBorder(new EmptyBorder(3, 8, 3, 8));
            l.setOpaque(true);
            l.setBackground(isSelected ? AppColors.ACCENT_LIGHT : Color.WHITE);
            l.setForeground(isSelected ? AppColors.ACCENT : AppColors.TEXT_PRIMARY);
            return l;
        };
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Dialog 共用工廠
    // ══════════════════════════════════════════════════════════════════════════
    private JLabel dlgFieldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(AppFonts.BODY_SMALL);
        l.setForeground(AppColors.TEXT_SECONDARY);
        return l;
    }

    private JTextField dlgTextField(String text) {
        JTextField f = new JTextField(text);
        f.setFont(AppFonts.BODY_MEDIUM);
        f.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(AppColors.BORDER_DEFAULT, 1, true),
            new EmptyBorder(5, 8, 5, 8)));
        return f;
    }

    /** 限制 JTextField 最多輸入 maxLen 個字元 */
    private void limitLength(JTextField field, int maxLen) {
        String existing = field.getText(); // 先記住原有內容
        field.setDocument(new javax.swing.text.PlainDocument() {
            @Override public void insertString(int offs, String str, javax.swing.text.AttributeSet a)
                    throws javax.swing.text.BadLocationException {
                if (str == null) return;
                String current = getText(0, getLength());
                String candidate = current.substring(0, offs) + str + current.substring(offs);
                if (candidate.length() <= maxLen) super.insertString(offs, str, a);
            }
        });
        // setDocument 會清空欄位，需重新填入原有內容（截斷至 maxLen）
        if (existing != null && !existing.isEmpty()) {
            field.setText(existing.length() > maxLen ? existing.substring(0, maxLen) : existing);
        }
    }
}