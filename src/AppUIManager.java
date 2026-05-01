import javax.swing.*;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/**
 * AppUIManager：共用 UI 工具
 *  - slimScrollBar()   細線 ScrollBar（Google/Notion 風格）
 *  - showDatePicker()  月曆彈出選日期
 *  - showTimePicker()  時鐘轉盤選時間
 */
public class AppUIManager {

    private AppUIManager() {}

    // ══════════════════════════════════════════════════════════
    // 1.  細線 ScrollBar
    // ══════════════════════════════════════════════════════════

    /** 套用細線 scrollbar 至 JScrollPane */
    public static void applySlimScrollBar(JScrollPane sp) {
        sp.getVerticalScrollBar().setUI(new SlimScrollBarUI());
        sp.getHorizontalScrollBar().setUI(new SlimScrollBarUI());
        sp.getVerticalScrollBar().setPreferredSize(new Dimension(6, 0));
        sp.getHorizontalScrollBar().setPreferredSize(new Dimension(0, 6));
        sp.getVerticalScrollBar().setOpaque(false);
        sp.getHorizontalScrollBar().setOpaque(false);
    }

    static class SlimScrollBarUI extends BasicScrollBarUI {

        private static final Color TRACK = new Color(0, 0, 0, 0);          // 透明軌道
        private static final Color THUMB = new Color(0xC8C7C3);            // 靜態 thumb
        private static final Color THUMB_HOVER = new Color(0xA8A7A4);      // hover thumb

        private boolean hovered = false;

        @Override protected void installListeners() {
            super.installListeners();
            scrollbar.addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hovered = true;  scrollbar.repaint(); }
                @Override public void mouseExited(MouseEvent e)  { hovered = false; scrollbar.repaint(); }
            });
        }

        @Override protected void configureScrollBarColors() {
            thumbColor       = THUMB;
            thumbDarkShadowColor = THUMB;
            thumbHighlightColor  = THUMB;
            thumbLightShadowColor = THUMB;
            trackColor       = TRACK;
            trackHighlightColor = TRACK;
        }

        @Override protected JButton createDecreaseButton(int orientation) { return zeroButton(); }
        @Override protected JButton createIncreaseButton(int orientation) { return zeroButton(); }

        private JButton zeroButton() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setMinimumSize(new Dimension(0, 0));
            b.setMaximumSize(new Dimension(0, 0));
            return b;
        }

        @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
            // 透明 track，什麼都不畫
        }

        @Override protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            if (r.width == 0 || r.height == 0) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(hovered ? THUMB_HOVER : THUMB);
            int arc = Math.min(r.width, r.height);
            // 水平 bar 時稍微縮排 top/bottom；垂直 bar 時縮排 left/right
            if (scrollbar.getOrientation() == JScrollBar.VERTICAL) {
                g2.fillRoundRect(r.x + 1, r.y + 2, r.width - 2, r.height - 4, arc, arc);
            } else {
                g2.fillRoundRect(r.x + 2, r.y + 1, r.width - 4, r.height - 2, arc, arc);
            }
            g2.dispose();
        }
    }

    // ══════════════════════════════════════════════════════════
    // 2.  月曆日期 Picker
    // ══════════════════════════════════════════════════════════

    public interface DatePickerCallback {
        void onDateSelected(LocalDate date);
    }

    /**
     * 在 anchor 元件下方彈出月曆 popup。
     * @param anchor    相對元件（通常是觸發按鈕）
     * @param initial   預設顯示月份
     * @param callback  選中日期後的回調
     */
    public static void showDatePicker(Component anchor, LocalDate initial, DatePickerCallback callback) {
        Window owner = SwingUtilities.getWindowAncestor(anchor);
        JDialog popup = new JDialog(owner);
        popup.setUndecorated(true);
        popup.setBackground(new Color(0, 0, 0, 0));

        DatePickerPanel panel = new DatePickerPanel(initial, date -> {
            popup.dispose();
            callback.onDateSelected(date);
        });

        popup.add(panel);
        popup.pack();

        // 定位：anchor 正下方
        Point loc = anchor.getLocationOnScreen();
        int x = loc.x;
        int y = loc.y + anchor.getHeight() + 4;
        // 若超出螢幕右邊就往左對齊
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        if (x + popup.getWidth() > screen.width) x = screen.width - popup.getWidth() - 4;
        popup.setLocation(x, y);
        popup.setVisible(true);

        // 點外面關閉
        popup.addWindowFocusListener(new WindowAdapter() {
            @Override public void windowLostFocus(WindowEvent e) { popup.dispose(); }
        });
    }

    /** 月曆面板 */
    static class DatePickerPanel extends JPanel {

        private static final String[] DAY_NAMES = {"日","一","二","三","四","五","六"};
        private static final DateTimeFormatter HEADER_FMT =
                DateTimeFormatter.ofPattern("yyyy 年 M 月");

        private YearMonth currentMonth;
        private final DatePickerCallback callback;
        private final JLabel headerLabel = new JLabel("", SwingConstants.CENTER);
        private final JPanel gridPanel   = new JPanel(new GridLayout(0, 7, 2, 2));

        DatePickerPanel(LocalDate initial, DatePickerCallback callback) {
            this.currentMonth = YearMonth.from(initial);
            this.callback     = callback;

            setLayout(new BorderLayout(0, 0));
            setBackground(Color.WHITE);
            setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColors.BORDER_DEFAULT, 1, true),
                new EmptyBorder(8, 8, 8, 8)
            ));
            // 陰影效果用 outer compound border 無法做，但 popup 加一圈就夠乾淨

            add(buildHeader(), BorderLayout.NORTH);
            add(buildWeekHeader(), BorderLayout.CENTER);
            // gridPanel 放在下方
            JPanel bottom = new JPanel(new BorderLayout());
            bottom.setBackground(Color.WHITE);
            bottom.add(buildWeekHeader(), BorderLayout.NORTH);
            bottom.add(gridPanel, BorderLayout.CENTER);
            add(bottom, BorderLayout.SOUTH);

            // 重建 layout
            setLayout(new BorderLayout(0, 4));
            removeAll();
            add(buildHeader(),     BorderLayout.NORTH);
            JPanel body = new JPanel(new BorderLayout(0, 4));
            body.setBackground(Color.WHITE);
            body.add(buildWeekHeader(), BorderLayout.NORTH);
            body.add(gridPanel,         BorderLayout.CENTER);
            add(body, BorderLayout.CENTER);

            renderGrid(initial);
        }

        private JPanel buildHeader() {
            JPanel nav = new JPanel(new BorderLayout());
            nav.setBackground(Color.WHITE);

            JButton prev = calNavBtn("<");
            JButton next = calNavBtn(">");
            prev.addActionListener(e -> { currentMonth = currentMonth.minusMonths(1); renderGrid(null); });
            next.addActionListener(e -> { currentMonth = currentMonth.plusMonths(1);  renderGrid(null); });

            headerLabel.setFont(AppFonts.TITLE_SMALL);
            headerLabel.setForeground(AppColors.TEXT_PRIMARY);

            nav.add(prev,        BorderLayout.WEST);
            nav.add(headerLabel, BorderLayout.CENTER);
            nav.add(next,        BorderLayout.EAST);
            return nav;
        }

        private JPanel buildWeekHeader() {
            JPanel row = new JPanel(new GridLayout(1, 7, 2, 0));
            row.setBackground(Color.WHITE);
            for (int i = 0; i < 7; i++) {
                JLabel l = new JLabel(DAY_NAMES[i], SwingConstants.CENTER);
                l.setFont(AppFonts.LABEL);
                l.setForeground(i == 0 || i == 6 ? AppColors.DANGER : AppColors.TEXT_TERTIARY);
                l.setPreferredSize(new Dimension(34, 22));
                row.add(l);
            }
            return row;
        }

        private void renderGrid(LocalDate selectedHint) {
            headerLabel.setText(currentMonth.format(HEADER_FMT));
            gridPanel.removeAll();
            gridPanel.setBackground(Color.WHITE);

            LocalDate today     = LocalDate.now();
            LocalDate first     = currentMonth.atDay(1);
            int       startDow  = first.getDayOfWeek().getValue() % 7; // 0=日
            int       daysInMon = currentMonth.lengthOfMonth();

            // 前面補空格
            for (int i = 0; i < startDow; i++) gridPanel.add(new JLabel());

            for (int d = 1; d <= daysInMon; d++) {
                LocalDate date    = currentMonth.atDay(d);
                boolean   isToday = date.equals(today);
                int       dow     = date.getDayOfWeek().getValue() % 7;

                // hover 效果：直接覆寫 paintComponent
                final boolean isW = (dow == 0 || dow == 6);
                JLabel finalCell = new JLabel(String.valueOf(d), SwingConstants.CENTER) {
                    private boolean hov = false;
                    { addMouseListener(new MouseAdapter() {
                        @Override public void mouseEntered(MouseEvent e) { hov = true;  repaint(); }
                        @Override public void mouseExited(MouseEvent e)  { hov = false; repaint(); }
                        @Override public void mouseClicked(MouseEvent e) { callback.onDateSelected(date); }
                    }); }
                    @Override protected void paintComponent(Graphics g) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        if (isToday) {
                            g2.setColor(AppColors.ACCENT);
                            g2.fillOval(2, 2, getWidth()-4, getHeight()-4);
                        } else if (hov) {
                            g2.setColor(AppColors.ACCENT_LIGHT);
                            g2.fillOval(2, 2, getWidth()-4, getHeight()-4);
                        }
                        g2.dispose();
                        super.paintComponent(g);
                    }
                };
                finalCell.setFont(isToday ? AppFonts.BODY_SMALL.deriveFont(Font.BOLD) : AppFonts.BODY_SMALL);
                finalCell.setForeground(isToday ? Color.WHITE : isW ? AppColors.DANGER : AppColors.TEXT_PRIMARY);
                finalCell.setOpaque(false);
                finalCell.setPreferredSize(new Dimension(34, 30));
                finalCell.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

                gridPanel.add(finalCell);
            }

            gridPanel.revalidate();
            gridPanel.repaint();
        }

        private JButton calNavBtn(String text) {
            JButton b = new JButton(text);
            b.setFont(AppFonts.TITLE_SMALL);
            b.setForeground(AppColors.TEXT_SECONDARY);
            b.setBorder(new EmptyBorder(2, 10, 2, 10));
            b.setFocusPainted(false);
            b.setContentAreaFilled(false);
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            return b;
        }
    }

    // ══════════════════════════════════════════════════════════
    // 3.  時鐘轉盤 Time Picker
    // ══════════════════════════════════════════════════════════

    public interface TimePickerCallback {
        void onTimeSelected(int hour, int minute);
    }

    /**
     * 彈出時鐘轉盤 popup。
     * @param anchor   相對元件
     * @param initHour 初始小時
     * @param initMin  初始分鐘
     * @param callback 選完後回調
     */
    public static void showTimePicker(Component anchor, int initHour, int initMin,
                                      TimePickerCallback callback) {
        Window owner = SwingUtilities.getWindowAncestor(anchor);
        JDialog popup = new JDialog(owner);
        popup.setUndecorated(true);

        TimePickerPanel panel = new TimePickerPanel(initHour, initMin, (h, m) -> {
            popup.dispose();
            callback.onTimeSelected(h, m);
        });

        popup.add(panel);
        popup.pack();

        Point loc = anchor.getLocationOnScreen();
        int x = loc.x;
        int y = loc.y + anchor.getHeight() + 4;
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        if (x + popup.getWidth() > screen.width) x = screen.width - popup.getWidth() - 4;
        popup.setLocation(x, y);
        popup.setVisible(true);

        popup.addWindowFocusListener(new WindowAdapter() {
            @Override public void windowLostFocus(WindowEvent e) { popup.dispose(); }
        });
    }

    /** 時鐘轉盤面板（先選小時，再選分鐘） */
    static class TimePickerPanel extends JPanel {

        enum Mode { HOUR, MINUTE }

        private int selectedHour;
        private int selectedMinute;
        private Mode mode = Mode.HOUR;

        private final TimePickerCallback callback;
        private final ClockFace clockFace;

        // 頂部顯示元件
        private final JLabel hourLabel   = new JLabel("", SwingConstants.CENTER);
        private final JLabel colonLabel  = new JLabel(":", SwingConstants.CENTER);
        private final JLabel minLabel    = new JLabel("", SwingConstants.CENTER);
        private final JLabel amLabel     = new JLabel("上午", SwingConstants.CENTER);
        private final JLabel pmLabel     = new JLabel("下午", SwingConstants.CENTER);
        private final JLabel modeHint    = new JLabel("選擇小時", SwingConstants.CENTER);

        TimePickerPanel(int initHour, int initMin, TimePickerCallback callback) {
            this.selectedHour   = initHour;
            this.selectedMinute = initMin;
            this.callback       = callback;
            this.clockFace      = new ClockFace();

            setLayout(new BorderLayout(0, 8));
            setBackground(Color.WHITE);
            setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColors.BORDER_DEFAULT, 1, true),
                new EmptyBorder(14, 16, 12, 16)
            ));
            setPreferredSize(new Dimension(256, 330));

            add(buildTopArea(), BorderLayout.NORTH);
            add(clockFace,      BorderLayout.CENTER);
            add(buildBottom(),  BorderLayout.SOUTH);

            refreshDisplay();
        }

        // ── 頂部：時間顯示 + AM/PM + 模式提示 ───────────────────────────
        private JPanel buildTopArea() {
            // 時:分 大字顯示，點小時或分鐘切換模式
            hourLabel.setFont(AppFonts.TITLE_LARGE);
            minLabel .setFont(AppFonts.TITLE_LARGE);
            colonLabel.setFont(AppFonts.TITLE_LARGE);
            colonLabel.setForeground(AppColors.TEXT_PRIMARY);

            hourLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            minLabel .setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            hourLabel.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { switchMode(Mode.HOUR); }
            });
            minLabel.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { switchMode(Mode.MINUTE); }
            });

            JPanel timeRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            timeRow.setBackground(Color.WHITE);
            timeRow.add(hourLabel);
            timeRow.add(colonLabel);
            timeRow.add(minLabel);

            // AM / PM 切換
            styleAmPm(amLabel, true);
            styleAmPm(pmLabel, false);
            amLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            pmLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            amLabel.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { setAmPm(true); }
            });
            pmLabel.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { setAmPm(false); }
            });

            JPanel ampmCol = new JPanel();
            ampmCol.setLayout(new BoxLayout(ampmCol, BoxLayout.Y_AXIS));
            ampmCol.setBackground(Color.WHITE);
            ampmCol.setBorder(new EmptyBorder(2, 8, 2, 0));
            ampmCol.add(amLabel);
            ampmCol.add(Box.createRigidArea(new Dimension(0, 2)));
            ampmCol.add(pmLabel);

            JPanel topRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            topRow.setBackground(Color.WHITE);
            topRow.add(timeRow);
            topRow.add(ampmCol);

            // 模式提示
            modeHint.setFont(AppFonts.CAPTION);
            modeHint.setForeground(AppColors.TEXT_TERTIARY);

            JPanel top = new JPanel(new BorderLayout(0, 4));
            top.setBackground(Color.WHITE);
            top.add(topRow,   BorderLayout.CENTER);
            top.add(modeHint, BorderLayout.SOUTH);
            return top;
        }

        private void styleAmPm(JLabel l, boolean isAm) {
            l.setFont(AppFonts.CAPTION.deriveFont(Font.BOLD));
            l.setPreferredSize(new Dimension(36, 22));
            l.setOpaque(true);
            // 初始顏色由 refreshAmPm() 在 refreshDisplay() 內設定
        }

        private void refreshAmPm() {
            boolean isPm = selectedHour >= 12;
            // 上午
            amLabel.setBackground(!isPm ? AppColors.ACCENT        : AppColors.BG_TERTIARY);
            amLabel.setForeground(!isPm ? Color.WHITE             : AppColors.TEXT_SECONDARY);
            // 下午
            pmLabel.setBackground( isPm ? AppColors.ACCENT        : AppColors.BG_TERTIARY);
            pmLabel.setForeground( isPm ? Color.WHITE             : AppColors.TEXT_SECONDARY);
            // 邊框
            LineBorder b = new LineBorder(AppColors.BORDER_DEFAULT, 1);
            amLabel.setBorder(BorderFactory.createCompoundBorder(b, new EmptyBorder(2,4,2,4)));
            pmLabel.setBorder(BorderFactory.createCompoundBorder(b, new EmptyBorder(2,4,2,4)));
        }

        private void setAmPm(boolean am) {
            boolean wasPm = selectedHour >= 12;
            if (am && wasPm)  selectedHour -= 12;
            if (!am && !wasPm) selectedHour += 12;
            // 邊界
            if (selectedHour < 0)  selectedHour = 0;
            if (selectedHour > 23) selectedHour = 23;
            refreshDisplay();
            clockFace.repaint();
        }

        private void switchMode(Mode m) {
            mode = m;
            modeHint.setText(m == Mode.HOUR ? "點選小時" : "點選分鐘");
            refreshDisplay();
            clockFace.repaint();
        }

        private void refreshDisplay() {
            // 時：24h 轉 12h 顯示
            int display12 = selectedHour % 12;
            if (display12 == 0) display12 = 12;

            boolean activeHour = (mode == Mode.HOUR);
            hourLabel.setText(String.format("%02d", display12));
            minLabel .setText(String.format("%02d", selectedMinute));

            // 目前選擇的欄位用 accent 色強調，另一個用淡灰
            hourLabel.setForeground(activeHour ? AppColors.ACCENT        : new Color(0xC0BFBC));
            minLabel .setForeground(activeHour ? new Color(0xC0BFBC)     : AppColors.ACCENT);

            modeHint.setText(mode == Mode.HOUR ? "點選小時" : "點選分鐘");
            refreshAmPm();
        }

        private JPanel buildBottom() {
            JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            bottom.setBackground(Color.WHITE);
            JButton cancelBtn = timeBtn("取消", false);
            JButton okBtn     = timeBtn("確認", true);
            cancelBtn.addActionListener(e -> SwingUtilities.getWindowAncestor(this).dispose());
            okBtn.addActionListener(e -> callback.onTimeSelected(selectedHour, selectedMinute));
            bottom.add(cancelBtn);
            bottom.add(okBtn);
            return bottom;
        }

        private JButton timeBtn(String text, boolean primary) {
            JButton b = new JButton(text);
            b.setFont(AppFonts.BODY_SMALL);
            b.setBackground(primary ? AppColors.ACCENT : AppColors.BG_TERTIARY);
            b.setForeground(primary ? Color.WHITE : AppColors.TEXT_SECONDARY);
            b.setBorder(new EmptyBorder(5, 14, 5, 14));
            b.setFocusPainted(false);
            b.setOpaque(true);
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            return b;
        }

        /** 圓形時鐘面 */
        class ClockFace extends JPanel {

            ClockFace() {
                setBackground(Color.WHITE);
                setPreferredSize(new Dimension(200, 200));

                addMouseListener(new MouseAdapter() {
                    @Override public void mouseClicked(MouseEvent e) {
                        handleClick(e.getX(), e.getY());
                    }
                });
                addMouseMotionListener(new MouseMotionAdapter() {
                    @Override public void mouseDragged(MouseEvent e) {
                        handleClick(e.getX(), e.getY());
                    }
                });
            }

            private void handleClick(int mx, int my) {
                int cx = getWidth() / 2, cy = getHeight() / 2;
                double angle = Math.atan2(my - cy, mx - cx);
                double deg = Math.toDegrees(angle) + 90;
                if (deg < 0) deg += 360;

                if (mode == Mode.HOUR) {
                    int h12 = (int) Math.round(deg / 30) % 12;
                    // 判斷 AM/PM 保持不變，只更新小時部分
                    boolean isPm = selectedHour >= 12;
                    selectedHour = h12 + (isPm ? 12 : 0);
                    if (selectedHour == 12 && !isPm) selectedHour = 0;
                    if (selectedHour == 24)          selectedHour = 12;
                    // 選完小時自動切到分鐘
                    mode = Mode.MINUTE;
                } else {
                    selectedMinute = (int) Math.round(deg / 6) % 60;
                }
                refreshDisplay();
                repaint();
            }

            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth(), h = getHeight();
                int cx = w / 2, cy = h / 2;
                int R  = (int)(Math.min(w, h) / 2.0 * 0.9);

                // 外圈背景
                g2.setColor(AppColors.BG_SECONDARY);
                g2.fillOval(cx - R, cy - R, R * 2, R * 2);

                if (mode == Mode.HOUR) {
                    paintHours(g2, cx, cy, R);
                } else {
                    paintMinutes(g2, cx, cy, R);
                }

                // 中心點
                g2.setColor(AppColors.ACCENT);
                g2.fillOval(cx - 4, cy - 4, 8, 8);

                // 指針
                paintHand(g2, cx, cy, R);

                g2.dispose();
            }

            private void paintHours(Graphics2D g2, int cx, int cy, int R) {
                double r = R * 0.82;
                // 只畫 1-12，AM/PM 由上方按鈕控制
                for (int h12 = 1; h12 <= 12; h12++) {
                    double ang = Math.toRadians(h12 * 30 - 90);
                    int nx = (int)(cx + r * Math.cos(ang));
                    int ny = (int)(cy + r * Math.sin(ang));

                    // 計算目前 selectedHour 對應的 12h 值
                    int sel12 = selectedHour % 12;
                    if (sel12 == 0) sel12 = 12;
                    boolean selected = (h12 == sel12);

                    if (selected) {
                        g2.setColor(AppColors.ACCENT);
                        g2.fillOval(nx - 15, ny - 15, 30, 30);
                    }

                    String label = String.valueOf(h12);
                    g2.setFont(AppFonts.BODY_SMALL);
                    g2.setColor(selected ? Color.WHITE : AppColors.TEXT_PRIMARY);
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(label,
                        nx - fm.stringWidth(label) / 2,
                        ny + fm.getAscent() / 2 - 1);
                }
            }

            private void paintMinutes(Graphics2D g2, int cx, int cy, int R) {
                double r = R * 0.85;
                for (int m = 0; m < 60; m++) {
                    double ang = Math.toRadians(m * 6 - 90);
                    int nx = (int)(cx + r * Math.cos(ang));
                    int ny = (int)(cy + r * Math.sin(ang));

                    boolean selected = (m == selectedMinute);
                    boolean major    = (m % 5 == 0);

                    if (selected) {
                        g2.setColor(AppColors.ACCENT);
                        g2.fillOval(nx - 13, ny - 13, 26, 26);
                    } else if (major) {
                        g2.setColor(AppColors.BG_TERTIARY);
                        g2.fillOval(nx - 10, ny - 10, 20, 20);
                    }

                    if (major) {
                        String label = String.valueOf(m);
                        g2.setFont(AppFonts.BODY_SMALL);
                        g2.setColor(selected ? Color.WHITE : AppColors.TEXT_PRIMARY);
                        FontMetrics fm = g2.getFontMetrics();
                        g2.drawString(label,
                            nx - fm.stringWidth(label) / 2,
                            ny + fm.getAscent() / 2 - 1);
                    } else {
                        // 小點
                        g2.setColor(selected ? Color.WHITE : AppColors.TEXT_TERTIARY);
                        g2.fillOval(nx - 2, ny - 2, 4, 4);
                    }
                }
            }

            private void paintHand(Graphics2D g2, int cx, int cy, int R) {
                double ang;
                int nx, ny;
                double handR = R * 0.82;

                if (mode == Mode.HOUR) {
                    int sel12 = selectedHour % 12;
                    if (sel12 == 0) sel12 = 12;
                    ang = Math.toRadians(sel12 * 30 - 90);
                } else {
                    handR = R * 0.85;
                    ang   = Math.toRadians(selectedMinute * 6 - 90);
                }
                nx = (int)(cx + handR * Math.cos(ang));
                ny = (int)(cy + handR * Math.sin(ang));

                g2.setColor(new Color(AppColors.ACCENT.getRed(),
                                      AppColors.ACCENT.getGreen(),
                                      AppColors.ACCENT.getBlue(), 80));
                g2.setStroke(new BasicStroke(2f));
                g2.drawLine(cx, cy, nx, ny);
            }
        }
    }
}