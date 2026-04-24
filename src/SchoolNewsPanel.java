import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.Desktop;
import java.net.URI;
import java.util.List;

/**
 * SchoolNewsPanel：顯示學校公告的面板。
 */
public class SchoolNewsPanel extends JPanel {

    private final SchoolNewsCrawler crawler;
    private final JPanel listContainer = new JPanel();
    private final JLabel statusLabel = new JLabel("按下「重新整理」載入公告...");
    private JButton refreshBtn;
    private boolean hasLoaded = false;

    public SchoolNewsPanel() {
        this.crawler = new SchoolNewsCrawler(this::onCrawlComplete);

        setLayout(new BorderLayout(0, 0));
        setBackground(AppColors.BG_SECONDARY);

        add(buildTopNav(),   BorderLayout.NORTH);
        add(buildListArea(), BorderLayout.CENTER);
        add(buildHintBar(),  BorderLayout.SOUTH);

        // 初次進入時自動載入
        if (!hasLoaded) {
            SwingUtilities.invokeLater(() -> {
                if (refreshBtn != null) {
                    refreshBtn.doClick();
                }
            });
        }
    }

    // ── 頂部列 ──────────────────────────────────────────────────────────────
    private JPanel buildTopNav() {
        JPanel nav = new JPanel(new BorderLayout());
        nav.setBackground(AppColors.BG_SECONDARY);
        nav.setBorder(new EmptyBorder(12, 16, 8, 16));

        JLabel title = new JLabel("學校公告");
        title.setFont(AppFonts.TITLE_MEDIUM);
        title.setForeground(AppColors.TEXT_PRIMARY);

        refreshBtn = new JButton("🔄 重新整理");
        refreshBtn.setFont(AppFonts.BODY_SMALL);
        refreshBtn.setBackground(AppColors.ACCENT);
        refreshBtn.setForeground(Color.WHITE);
        refreshBtn.setBorder(new EmptyBorder(7, 16, 7, 16));
        refreshBtn.setFocusPainted(false);
        refreshBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        refreshBtn.addActionListener(e -> onRefreshClick());

        nav.add(title,      BorderLayout.WEST);
        nav.add(refreshBtn, BorderLayout.EAST);
        return nav;
    }

    // ── 清單區 ──────────────────────────────────────────────────────────────
    private JScrollPane buildListArea() {
        listContainer.setLayout(new BoxLayout(listContainer, BoxLayout.Y_AXIS));
        listContainer.setBackground(AppColors.BG_PRIMARY);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(AppColors.BG_PRIMARY);
        wrapper.add(listContainer, BorderLayout.NORTH);

        JScrollPane sp = new JScrollPane(wrapper);
        sp.setBorder(new MatteBorder(1, 0, 0, 0, AppColors.BORDER_DEFAULT));
        sp.getVerticalScrollBar().setPreferredSize(new Dimension(12, 0));
        sp.getViewport().setBackground(AppColors.BG_PRIMARY);
        return sp;
    }

    // ── 底部提示列 ────────────────────────────────────────────────────────────
    private JPanel buildHintBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 6));
        bar.setBackground(AppColors.BG_SECONDARY);
        bar.setBorder(new MatteBorder(1, 0, 0, 0, AppColors.BORDER_DEFAULT));

        statusLabel.setFont(AppFonts.CAPTION);
        statusLabel.setForeground(AppColors.TEXT_TERTIARY);
        bar.add(statusLabel);

        return bar;
    }

    // ── 建立每一列公告的 UI ─────────────────────────────────────────────────
    private JPanel buildNewsRow(NewsItem news) {
        JPanel row = new JPanel(new BorderLayout(0, 0)) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(AppColors.BORDER_DEFAULT);
                g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
            }
        };
        row.setOpaque(true);
        row.setBackground(AppColors.BG_PRIMARY);
        row.setMinimumSize(new Dimension(0, 60));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        row.setBorder(new EmptyBorder(12, 12, 12, 12));
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // ── 標題 + URL ──
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);

        JLabel titleLbl = new JLabel(news.getTitle());
        titleLbl.setFont(AppFonts.BODY_SMALL);
        titleLbl.setForeground(AppColors.TEXT_PRIMARY);
        titleLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(titleLbl);

        content.add(Box.createRigidArea(new Dimension(0, 4)));

        JLabel urlLbl = new JLabel("<html><u>" + truncateUrl(news.getUrl()) + "</u></html>");
        urlLbl.setFont(AppFonts.CAPTION);
        urlLbl.setForeground(AppColors.ACCENT);
        urlLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(urlLbl);

        content.add(Box.createRigidArea(new Dimension(0, 2)));

        JLabel timeLbl = new JLabel(news.getFetchedTime());
        timeLbl.setFont(AppFonts.CAPTION);
        timeLbl.setForeground(AppColors.TEXT_TERTIARY);
        timeLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(timeLbl);

        // ── 點擊開啟連結 ──
        MouseAdapter openLink = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                openUrl(news.getUrl());
            }
            @Override public void mouseEntered(MouseEvent e) {
                row.setBackground(AppColors.ACCENT_LIGHT);
                urlLbl.setForeground(AppColors.ACCENT);
            }
            @Override public void mouseExited(MouseEvent e) {
                row.setBackground(AppColors.BG_PRIMARY);
                urlLbl.setForeground(AppColors.ACCENT);
            }
        };
        row.addMouseListener(openLink);
        content.addMouseListener(openLink);
        urlLbl.addMouseListener(openLink);

        row.add(content, BorderLayout.CENTER);
        return row;
    }

    private static String truncateUrl(String url) {
        if (url.length() > 70) {
            return url.substring(0, 67) + "...";
        }
        return url;
    }

    private static void openUrl(String url) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null,
                    "無法開啟連結: " + url,
                    "錯誤", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ── 判斷新聞是否應該顯示 ───────────────────────────────────────────────
    private boolean isNewsIncluded(String title) {
        return title.contains("校園行事曆") ||
               title.contains("大學程式檢定") ||
               title.contains("1142學期資工系新生電腦選課") ||
               title.contains("2026海洋盃程式競賽");
    }

    // ── 事件處理 ──────────────────────────────────────────────────────────────
    private void onRefreshClick() {
        statusLabel.setText("正在載入公告...");
        refreshBtn.setEnabled(false);
        crawler.fetchNewsAsync();
    }

    private void onCrawlComplete() {
        SwingUtilities.invokeLater(() -> {
            hasLoaded = true;
            List<NewsItem> news = crawler.getCachedNews();

            listContainer.removeAll();

            if (news.isEmpty()) {
                JLabel empty = new JLabel(
                        "目前沒有公告或網路連線失敗，請稍後重試。",
                        SwingConstants.CENTER);
                empty.setFont(AppFonts.BODY_SMALL);
                empty.setForeground(AppColors.TEXT_TERTIARY);
                empty.setAlignmentX(Component.CENTER_ALIGNMENT);
                empty.setBorder(new EmptyBorder(40, 0, 0, 0));
                listContainer.add(empty);
                statusLabel.setText("載入完成 — " + news.size() + " 筆公告");
            } else {
                int displayCount = 0;
                for (NewsItem item : news) {
                    if (isNewsIncluded(item.getTitle())) {
                        listContainer.add(buildNewsRow(item));
                        displayCount++;
                    }
                }
                statusLabel.setText("載入完成 — " + displayCount + " 筆公告");
            }

            listContainer.revalidate();
            listContainer.repaint();
            refreshBtn.setEnabled(true);
        });
    }
}