import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

/**
 * MainFrame：主視窗，含 Topbar、Sidebar、CardLayout 內容區。
 * v0.4：整合 Tronclass 登入功能，登入後在 Topbar 顯示姓名。
 */
public class MainFrame extends JFrame {

    private final JPanel     contentArea;
    private final CardLayout cardLayout;
    private NavItem          activeNav;

    private final CalendarPanel   calendarPanel;
    private final TodoPanel       todoPanel;
    private final SchoolNewsPanel newsPanel;
    private final SchedulePanel   schedulePanel;

    // 資料 callback（供登入後重新儲存 todos）
    private final Runnable saveTodosCallback;

    // 登入後更新的 Topbar 元件
    private JLabel  topbarAvatarLabel;
    private JLabel  topbarHintLabel;
    private JButton loginBtn;

    // 目前登入狀態
    private String  loggedInName   = null;
    private String  loggedInCookie = null;

    public MainFrame(List<Task> tasks, List<TodoItem> todos, List<Schedule> schedules,
                     Runnable saveTasksCallback, Runnable saveTodosCallback,
                     Runnable saveSchedulesCallback) {

        this.saveTodosCallback = saveTodosCallback;

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

        JLabel logoBox = new JLabel("S", SwingConstants.CENTER) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(AppColors.ACCENT_LIGHT);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(new Color(0xC5D0FA));
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        logoBox.setFont(AppFonts.TITLE_SMALL);
        logoBox.setForeground(AppColors.ACCENT);
        logoBox.setOpaque(false);
        logoBox.setPreferredSize(new Dimension(36, 36));

        JLabel appName = new JLabel("  學生行程與任務管理系統");
        appName.setFont(AppFonts.TITLE_SMALL);
        appName.setForeground(AppColors.TEXT_PRIMARY);

        left.add(logoBox);
        left.add(appName);
        bar.add(left, BorderLayout.WEST);

        // 右側：提示 / 姓名 + 頭像 + 登入按鈕
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);

        // 狀態提示（未登入顯示提示；登入後顯示姓名）
        topbarHintLabel = new JLabel("尚未登入");
        topbarHintLabel.setFont(AppFonts.CAPTION);
        topbarHintLabel.setForeground(AppColors.TEXT_TERTIARY);

        // 頭像圓圈（顯示姓名第一個字或 ?）
        topbarAvatarLabel = new JLabel("?", SwingConstants.CENTER);
        topbarAvatarLabel.setFont(AppFonts.BODY_SMALL);
        topbarAvatarLabel.setForeground(AppColors.ACCENT_TEXT);
        topbarAvatarLabel.setBackground(AppColors.ACCENT_LIGHT);
        topbarAvatarLabel.setOpaque(true);
        topbarAvatarLabel.setPreferredSize(new Dimension(32, 32));
        topbarAvatarLabel.setBorder(new LineBorder(AppColors.ACCENT, 1));

        // 登入 / 重新同步 按鈕
        loginBtn = new JButton("登入 Tronclass");
        loginBtn.setFont(AppFonts.CAPTION);
        loginBtn.setBackground(AppColors.ACCENT);
        loginBtn.setForeground(Color.WHITE);
        loginBtn.setBorder(new EmptyBorder(5, 12, 5, 12));
        loginBtn.setFocusPainted(false);
        loginBtn.setOpaque(true);
        loginBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        loginBtn.addActionListener(e -> openLoginDialog());

        right.add(topbarHintLabel);
        right.add(topbarAvatarLabel);
        right.add(loginBtn);
        bar.add(right, BorderLayout.EAST);

        return bar;
    }

    // ── 開啟登入 Dialog ──────────────────────────────────────────────────────
    private void openLoginDialog() {
        // 取得目前 todoPanel 所持有的 todos 參考（與 Main 共用同一份 List）
        // 透過 saveTodosCallback 在同步完成後自動持久化
        List<TodoItem> todos = todoPanel.getTodos();

        TronclassLoginDialog dlg = new TronclassLoginDialog(
            this, todos, saveTodosCallback,
            (name, cookie, added) -> {
                // 回到 EDT 更新 UI
                SwingUtilities.invokeLater(() -> {
                    loggedInName   = name;
                    loggedInCookie = cookie;
                    updateTopbarUser(name);
                    todoPanel.refreshList();

                    // 通知使用者
                    String userName = name != null ? name : "使用者";
                    String msg = added > 0
                        ? "✓ 已同步 " + added + " 筆待辦事項到「代辦事項」頁面。"
                        : "✓ 登入成功，無新的待辦事項。";
                    JOptionPane.showMessageDialog(this,
                        "<html><b>" + userName + "</b> 您好！<br>" + msg + "</html>",
                        "Tronclass 同步完成",
                        JOptionPane.INFORMATION_MESSAGE);
                });
            }
        );
        dlg.setVisible(true);
    }

    /**
     * 登入成功後更新 Topbar 顯示。
     */
    private void updateTopbarUser(String name) {
        if (name != null && !name.isEmpty()) {
            // 取第一個中文字作為頭像
            String initial = name.substring(0, 1);
            topbarAvatarLabel.setText(initial);
            topbarHintLabel.setText(name);
            topbarHintLabel.setForeground(AppColors.TEXT_PRIMARY);
            topbarHintLabel.setFont(AppFonts.BODY_SMALL);
        } else {
            topbarAvatarLabel.setText("✓");
            topbarHintLabel.setText("已登入");
            topbarHintLabel.setForeground(AppColors.SUCCESS);
        }
        loginBtn.setText("重新同步");
        loginBtn.setBackground(AppColors.BG_TERTIARY);
        loginBtn.setForeground(AppColors.TEXT_PRIMARY);
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

        JLabel ver = new JLabel("  v0.4  早期預覽版");
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