import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

/**
 * MainFrame：主視窗，含 Topbar、Sidebar、CardLayout 內容區。
 */
public class MainFrame extends JFrame {

    private final JPanel     contentArea;
    private final CardLayout cardLayout;
    private NavItem          activeNav;

    private final CalendarPanel   calendarPanel;
    private final TodoPanel       todoPanel;
    private final SchoolNewsPanel newsPanel;
    private final SchedulePanel   schedulePanel;

    public MainFrame(List<Task> tasks, List<TodoItem> todos, List<Schedule> schedules,
                     Runnable saveTasksCallback, Runnable saveTodosCallback,
                     Runnable saveSchedulesCallback) {

        calendarPanel = new CalendarPanel(tasks);
        todoPanel     = new TodoPanel(todos, saveTodosCallback);
        newsPanel     = new SchoolNewsPanel();
        schedulePanel = new SchedulePanel(schedules, saveSchedulesCallback);

        setTitle("學生行程與任務管理系統");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 700);
        setMinimumSize(new Dimension(900, 560));
        setLocationRelativeTo(null);

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                saveTasksCallback.run();
                saveTodosCallback.run();
                saveSchedulesCallback.run();
            }
        });

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(AppColors.BG_SECONDARY);
        setContentPane(root);

        root.add(buildTopbar(),  BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout());
        body.setBackground(AppColors.BG_SECONDARY);
        body.add(buildSidebar(), BorderLayout.WEST);

        cardLayout  = new CardLayout();
        contentArea = new JPanel(cardLayout);
        contentArea.setBackground(AppColors.BG_SECONDARY);
        contentArea.add(calendarPanel, "calendar");
        contentArea.add(todoPanel,     "todo");
        contentArea.add(newsPanel,     "news");
        contentArea.add(schedulePanel, "schedule");
        body.add(contentArea, BorderLayout.CENTER);

        root.add(body, BorderLayout.CENTER);
    }

    // ── Topbar ─────────────────────────────────────────────────────────────
    private JPanel buildTopbar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(AppColors.TOPBAR_BG);
        bar.setPreferredSize(new Dimension(0, 52));
        bar.setBorder(new MatteBorder(0, 0, 1, 0, AppColors.BORDER_DEFAULT));

        // 左側：圖標 + 應用名稱
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        left.setBorder(new EmptyBorder(0, 18, 0, 0));

        // 圖標：書本符號，圓角背景 — 改為淺藍底色 + 深藍文字
        JLabel logoBox = new JLabel("S", SwingConstants.CENTER) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(AppColors.ACCENT_LIGHT);          // 淺藍背景
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                // 淺藍底上畫一圈淡邊框
                g2.setColor(new Color(0xC5D0FA));
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        logoBox.setFont(AppFonts.TITLE_SMALL);
        logoBox.setForeground(AppColors.ACCENT);              // 深藍文字
        logoBox.setOpaque(false);
        logoBox.setPreferredSize(new Dimension(36, 36));

        JLabel appName = new JLabel("  學生行程與任務管理系統");
        appName.setFont(AppFonts.TITLE_SMALL);
        appName.setForeground(AppColors.TEXT_PRIMARY);

        left.add(logoBox);
        left.add(appName);
        bar.add(left, BorderLayout.WEST);

        // 右側：提示 + 頭像
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 0));
        right.setOpaque(false);

        JLabel hint = new JLabel("登入功能開發中");
        hint.setFont(AppFonts.CAPTION);
        hint.setForeground(AppColors.TEXT_TERTIARY);

        JLabel avatar = new JLabel("?", SwingConstants.CENTER);
        avatar.setFont(AppFonts.BODY_SMALL);
        avatar.setForeground(AppColors.ACCENT_TEXT);
        avatar.setBackground(AppColors.ACCENT_LIGHT);
        avatar.setOpaque(true);
        avatar.setPreferredSize(new Dimension(32, 32));
        avatar.setBorder(new LineBorder(AppColors.ACCENT, 1));

        right.add(hint);
        right.add(avatar);
        bar.add(right, BorderLayout.EAST);

        return bar;
    }

    // ── Sidebar ────────────────────────────────────────────────────────────
    private JPanel buildSidebar() {
        JPanel sb = new JPanel();
        sb.setLayout(new BoxLayout(sb, BoxLayout.Y_AXIS));
        sb.setBackground(AppColors.SIDEBAR_BG);
        sb.setPreferredSize(new Dimension(200, 0));
        sb.setBorder(new MatteBorder(0, 0, 0, 1, AppColors.BORDER_DEFAULT));

        sb.add(Box.createRigidArea(new Dimension(0, 16)));
        sb.add(sectionLabel("主要功能"));

        NavItem calNav      = new NavItem("任務行事曆");
        NavItem todoNav     = new NavItem("代辦事項");
        NavItem newsNav     = new NavItem("學校公告");
        NavItem scheduleNav = new NavItem("課程課表");

        calNav.addActionListener(e      -> switchTo("calendar", calNav));
        todoNav.addActionListener(e     -> switchTo("todo",     todoNav));
        newsNav.addActionListener(e     -> switchTo("news",     newsNav));
        scheduleNav.addActionListener(e -> switchTo("schedule", scheduleNav));

        sb.add(calNav);
        sb.add(todoNav);
        sb.add(newsNav);
        sb.add(scheduleNav);

        sb.add(Box.createRigidArea(new Dimension(0, 12)));

        sb.add(Box.createVerticalGlue());

        JSeparator sep = new JSeparator(SwingConstants.HORIZONTAL);
        sep.setForeground(AppColors.BORDER_DEFAULT);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sb.add(sep);
        sb.add(Box.createRigidArea(new Dimension(0, 8)));

        JLabel ver = new JLabel("  v0.3  早期預覽版");
        ver.setFont(AppFonts.CAPTION);
        ver.setForeground(AppColors.TEXT_TERTIARY);
        ver.setAlignmentX(Component.LEFT_ALIGNMENT);
        sb.add(ver);
        sb.add(Box.createRigidArea(new Dimension(0, 10)));

        activeNav = calNav;
        calNav.setActive(true);

        return sb;
    }

    private JLabel sectionLabel(String text) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setFont(AppFonts.LABEL);
        l.setForeground(AppColors.TEXT_TERTIARY);
        l.setBorder(new EmptyBorder(4, 16, 4, 16));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        return l;
    }

    private JPanel disabledItem(String label) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 7));
        p.setOpaque(false);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setBorder(new EmptyBorder(0, 8, 0, 0));

        JLabel nm = new JLabel(label);
        nm.setFont(AppFonts.NAV);
        nm.setForeground(AppColors.TEXT_TERTIARY);

        JLabel badge = new JLabel("即將推出");
        badge.setFont(new Font(AppFonts.CAPTION.getFamily(), Font.PLAIN, 10));
        badge.setForeground(AppColors.TEXT_TERTIARY);
        badge.setBackground(AppColors.BG_TERTIARY);
        badge.setOpaque(true);
        badge.setBorder(new EmptyBorder(1, 5, 1, 5));

        p.add(nm); p.add(badge);
        return p;
    }

    private void switchTo(String key, NavItem nav) {
        if (activeNav != null) activeNav.setActive(false);
        nav.setActive(true);
        activeNav = nav;
        cardLayout.show(contentArea, key);
    }

    // ── NavItem ────────────────────────────────────────────────────────────
    static class NavItem extends JPanel {
        private boolean active  = false;
        private boolean hovered = false;
        private final java.util.List<ActionListener> listeners = new java.util.ArrayList<>();
        private final JLabel nameLabel;

        NavItem(String label) {
            setLayout(new FlowLayout(FlowLayout.LEFT, 8, 7));
            setOpaque(false);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(0, 8, 0, 8));

            nameLabel = new JLabel(label);
            nameLabel.setFont(AppFonts.NAV);
            nameLabel.setForeground(AppColors.TEXT_SECONDARY);

            add(nameLabel);

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hovered = true;  repaint(); }
                @Override public void mouseExited(MouseEvent e)  { hovered = false; repaint(); }
                @Override public void mouseClicked(MouseEvent e) {
                    for (ActionListener l : listeners) l.actionPerformed(null);
                }
            });
        }

        void addActionListener(ActionListener l) { listeners.add(l); }

        void setActive(boolean a) {
            this.active = a;
            nameLabel.setFont(a ? AppFonts.NAV_ACTIVE : AppFonts.NAV);
            nameLabel.setForeground(a ? AppColors.TEXT_PRIMARY : AppColors.TEXT_SECONDARY);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color bg = active ? AppColors.NAV_ACTIVE_BG : hovered ? AppColors.NAV_HOVER_BG : null;
            if (bg != null) {
                g2.setColor(bg);
                g2.fillRoundRect(6, 2, getWidth() - 12, getHeight() - 4, 8, 8);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    public void showWindow() { setVisible(true); }
}