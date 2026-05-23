package ui;

import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import model.NewsItem;
import service.SchoolNewsCrawler;

/**
 * SchoolNewsPanel：學校/系所公告列表。
 */
public class SchoolNewsPanel extends JPanel {

    private static final Path STAR_FILE = Path.of("data", "news-stars.txt");
    private static final Path DEFAULT_UNSTAR_FILE = Path.of("data", "news-default-unstarred.txt");
    private static final String CATEGORY_ALL = "全部";
    // 請將原本的選項替換為這四個你需要的分類
    private static final String[] CATEGORY_OPTIONS = {
        CATEGORY_ALL, "行事曆", "CPE", "學業資訊", "最新消息"
    };

    private final SchoolNewsCrawler crawler;
    private final JPanel listContainer = new JPanel();
    private final JLabel statusLabel = new JLabel("尚未載入公告");
    private final JTextField searchField = new JTextField();
    private final JComboBox<String> categoryCombo = new JComboBox<>(CATEGORY_OPTIONS);
    private final JCheckBox starredOnlyCheck = new JCheckBox("只看星號");
    private final Set<String> starredUrls = new HashSet<>();
    private final Set<String> unstarredDefaultUrls = new HashSet<>();

    private JButton refreshBtn;
    private JScrollPane listScrollPane;
    private boolean hasLoaded = false;

    public SchoolNewsPanel() {
        this.crawler = new SchoolNewsCrawler(this::onCrawlComplete);
        loadStars();
        loadDefaultUnstars();

        setLayout(new BorderLayout(0, 0));
        setBackground(AppColors.BG_SECONDARY);

        add(buildTopNav(),   BorderLayout.NORTH);
        add(buildListArea(), BorderLayout.CENTER);
        add(buildHintBar(),  BorderLayout.SOUTH);

        if (!hasLoaded) {
            SwingUtilities.invokeLater(() -> {
                if (refreshBtn != null) refreshBtn.doClick();
            });
        }
    }

    private JPanel buildTopNav() {
        JPanel nav = new JPanel(new BorderLayout(0, 8));
        nav.setBackground(AppColors.BG_SECONDARY);
        nav.setBorder(new EmptyBorder(12, 16, 8, 16));

        JLabel title = new JLabel("學校公告 / 系所資訊");
        title.setFont(AppFonts.TITLE_MEDIUM);
        title.setForeground(AppColors.TEXT_PRIMARY);

        refreshBtn = new JButton("重新整理");
        refreshBtn.setFont(AppFonts.BODY_SMALL);
        refreshBtn.setBackground(AppColors.ACCENT);
        refreshBtn.setForeground(Color.WHITE);
        refreshBtn.setBorder(new EmptyBorder(7, 16, 7, 16));
        refreshBtn.setFocusPainted(false);
        refreshBtn.setOpaque(true);
        refreshBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        refreshBtn.addActionListener(e -> onRefreshClick());

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(title, BorderLayout.WEST);
        header.add(refreshBtn, BorderLayout.EAST);

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filters.setOpaque(false);

        searchField.setFont(AppFonts.BODY_SMALL);
        searchField.setToolTipText("輸入關鍵字搜尋公告標題");
        searchField.setPreferredSize(new Dimension(260, 32));
        searchField.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColors.BORDER_DEFAULT, 1, true),
                new EmptyBorder(4, 9, 4, 9)));
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { renderNewsList(true); }
            @Override public void removeUpdate(DocumentEvent e) { renderNewsList(true); }
            @Override public void changedUpdate(DocumentEvent e) { renderNewsList(true); }
        });

        categoryCombo.setFont(AppFonts.BODY_SMALL);
        categoryCombo.setBackground(Color.WHITE);
        categoryCombo.setForeground(AppColors.TEXT_PRIMARY);
        SchedulePanel.applyComboStyle(categoryCombo);
        categoryCombo.addActionListener(e -> {
            renderNewsList(true);
        });

        starredOnlyCheck.setFont(AppFonts.BODY_SMALL);
        starredOnlyCheck.setForeground(AppColors.TEXT_SECONDARY);
        starredOnlyCheck.setOpaque(false);
        starredOnlyCheck.addActionListener(e -> renderNewsList(true));

        filters.add(fieldLabel("搜尋"));
        filters.add(searchField);
        filters.add(fieldLabel("分類"));
        filters.add(categoryCombo);
        filters.add(starredOnlyCheck);

        nav.add(header, BorderLayout.NORTH);
        nav.add(filters, BorderLayout.SOUTH);
        return nav;
    }

    private JScrollPane buildListArea() {
        listContainer.setLayout(new BoxLayout(listContainer, BoxLayout.Y_AXIS));
        listContainer.setBackground(AppColors.BG_PRIMARY);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(AppColors.BG_PRIMARY);
        wrapper.add(listContainer, BorderLayout.NORTH);

        listScrollPane = new JScrollPane(wrapper);
        listScrollPane.setBorder(new MatteBorder(1, 0, 0, 0, AppColors.BORDER_DEFAULT));
        listScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        listScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        listScrollPane.getViewport().setBackground(AppColors.BG_PRIMARY);
        listScrollPane.getVerticalScrollBar().setUnitIncrement(20);
        AppUIManager.applySlimScrollBar(listScrollPane);
        return listScrollPane;
    }

    private JPanel buildHintBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(AppColors.BG_SECONDARY);
        bar.setBorder(new MatteBorder(1, 0, 0, 0, AppColors.BORDER_DEFAULT));

        statusLabel.setFont(AppFonts.CAPTION);
        statusLabel.setForeground(AppColors.TEXT_TERTIARY);
        statusLabel.setBorder(new EmptyBorder(6, 16, 6, 16));

        JLabel hint = new JLabel("點擊公告即可開啟網頁；星號可加入收藏");
        hint.setFont(AppFonts.CAPTION);
        hint.setForeground(AppColors.TEXT_TERTIARY);
        hint.setBorder(new EmptyBorder(6, 16, 6, 16));

        bar.add(statusLabel, BorderLayout.WEST);
        bar.add(hint, BorderLayout.EAST);
        return bar;
    }

    private JPanel buildNewsRow(NewsItem news) {
        boolean starred = isStarred(news);
        String category = classifyNews(news);

        JPanel row = new JPanel(new BorderLayout(10, 0)) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(AppColors.BORDER_DEFAULT);
                g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
            }
        };
        row.setOpaque(true);
        row.setBackground(AppColors.BG_PRIMARY);
        row.setMinimumSize(new Dimension(0, 66));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        row.setBorder(new EmptyBorder(10, 12, 10, 14));
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        row.setToolTipText(news.getUrl());

        JButton starBtn = new JButton(starred ? "★" : "☆");
        starBtn.setFont(new Font(AppFonts.BODY_MEDIUM.getFamily(), Font.PLAIN, 18));
        starBtn.setForeground(starred ? AppColors.WARNING : AppColors.TEXT_TERTIARY);
        starBtn.setBorder(new EmptyBorder(0, 4, 0, 4));
        starBtn.setFocusPainted(false);
        starBtn.setContentAreaFilled(false);
        starBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        starBtn.setToolTipText(starred ? "取消星號" : "加入星號");
        starBtn.addActionListener(e -> toggleStar(news));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);

        JTextArea titleArea = new JTextArea(news.getTitle());
        titleArea.setFont(AppFonts.BODY_SMALL);
        titleArea.setForeground(AppColors.TEXT_PRIMARY);
        titleArea.setEditable(false);
        titleArea.setFocusable(false);
        titleArea.setLineWrap(true);
        titleArea.setWrapStyleWord(true);
        titleArea.setOpaque(false);
        titleArea.setBorder(null);
        titleArea.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(titleArea);

        content.add(Box.createRigidArea(new Dimension(0, 5)));

        JPanel metaRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        metaRow.setOpaque(false);
        metaRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        metaRow.add(categoryTag(category));
        metaRow.add(metaLabel(news.getFetchedTime()));
        content.add(metaRow);

        MouseAdapter openLink = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                openUrl(news.getUrl());
            }
            @Override public void mouseEntered(MouseEvent e) {
                row.setBackground(AppColors.ACCENT_LIGHT);
            }
            @Override public void mouseExited(MouseEvent e) {
                row.setBackground(AppColors.BG_PRIMARY);
            }
        };
        row.addMouseListener(openLink);
        content.addMouseListener(openLink);
        titleArea.addMouseListener(openLink);

        row.add(starBtn, BorderLayout.WEST);
        row.add(content, BorderLayout.CENTER);
        return row;
    }

    private void onRefreshClick() {
        statusLabel.setText("正在載入公告...");
        refreshBtn.setEnabled(false);
        crawler.fetchNewsAsync();
    }

    private void onCrawlComplete() {
        SwingUtilities.invokeLater(() -> {
            hasLoaded = true;
            renderNewsList(true);
            refreshBtn.setEnabled(true);
        });
    }

    private void renderNewsList() {
        renderNewsList(false);
    }

    private void renderNewsList(boolean scrollToTop) {
        List<NewsItem> news = new ArrayList<>(crawler.getCachedNews());
        listContainer.removeAll();

        String query = searchField.getText().trim().toLowerCase();
        String selectedCategory = (String) categoryCombo.getSelectedItem();
        boolean starredOnly = starredOnlyCheck.isSelected();

        if (CATEGORY_ALL.equals(selectedCategory) && !starredOnly) {
            news.sort(Comparator
                    .comparing((NewsItem item) -> !isStarred(item))
                    .thenComparing(NewsItem::getFetchedTime, Comparator.reverseOrder()));
        }

        int displayCount = 0;
        for (NewsItem item : news) {
            String category = classifyNews(item);
            if (category == null) continue; // 【新增】如果不是我們要的四種分類，直接跳過不顯示

            if (!isVisibleByKeyword(item, query)) continue;
            if (!isVisibleByCategory(item, selectedCategory)) continue;
            if (starredOnly && !isStarred(item)) continue;
            listContainer.add(buildNewsRow(item));
            displayCount++;
        }

        if (news.isEmpty()) {
            addEmptyMessage("目前沒有公告資料，請重新整理。");
        } else if (displayCount == 0) {
            addEmptyMessage("沒有符合目前篩選條件的公告。");
        }

        statusLabel.setText("顯示 " + displayCount + " / " + news.size() + " 則公告");
        listContainer.revalidate();
        listContainer.repaint();
        if (scrollToTop && listScrollPane != null) {
            SwingUtilities.invokeLater(() ->
                    listScrollPane.getVerticalScrollBar().setValue(0));
        }
    }

    private boolean isVisibleByKeyword(NewsItem item, String query) {
        if (query.isEmpty()) return true;
        String category = classifyNews(item);
        String categoryStr = (category != null) ? category : ""; // 【修改】防呆處理
        
        return item.getTitle().toLowerCase().contains(query)
                || categoryStr.toLowerCase().contains(query)
                || extractHost(item.getUrl()).toLowerCase().contains(query);
    }

    private boolean isVisibleByCategory(NewsItem item, String selectedCategory) {
        if (selectedCategory == null || CATEGORY_ALL.equals(selectedCategory)) {
            return true;
        }
        return selectedCategory.equals(classifyNews(item));
    }

    private void addEmptyMessage(String text) {
        JLabel empty = new JLabel(text, SwingConstants.CENTER);
        empty.setFont(AppFonts.BODY_SMALL);
        empty.setForeground(AppColors.TEXT_TERTIARY);
        empty.setAlignmentX(Component.CENTER_ALIGNMENT);
        empty.setBorder(new EmptyBorder(40, 0, 0, 0));
        listContainer.add(empty);
    }

    private boolean isStarred(NewsItem news) {
        String url = news.getUrl();
        return starredUrls.contains(url)
                || (isDefaultStarred(news) && !unstarredDefaultUrls.contains(url));
    }

    private static boolean isDefaultStarred(NewsItem news) {
        return news.getTitle().contains("行事曆");
    }

    private void toggleStar(NewsItem news) {
        String url = news.getUrl();
        boolean defaultStarred = isDefaultStarred(news);
        if (isStarred(news)) {
            starredUrls.remove(url);
            if (defaultStarred) {
                unstarredDefaultUrls.add(url);
            }
        } else {
            if (defaultStarred) {
                unstarredDefaultUrls.remove(url);
            } else {
                starredUrls.add(url);
            }
        }
        saveStars();
        saveDefaultUnstars();
        renderNewsList();
    }

    private void loadStars() {
        try {
            if (!Files.exists(STAR_FILE)) return;
            for (String line : Files.readAllLines(STAR_FILE, StandardCharsets.UTF_8)) {
                String url = line.trim();
                if (!url.isEmpty()) starredUrls.add(url);
            }
        } catch (IOException ignored) {
            // Starred news is a convenience feature; the list still works if loading fails.
        }
    }

    private void loadDefaultUnstars() {
        try {
            if (!Files.exists(DEFAULT_UNSTAR_FILE)) return;
            for (String line : Files.readAllLines(DEFAULT_UNSTAR_FILE, StandardCharsets.UTF_8)) {
                String url = line.trim();
                if (!url.isEmpty()) unstarredDefaultUrls.add(url);
            }
        } catch (IOException ignored) {
            // Default star exclusions are optional local preferences.
        }
    }

    private void saveStars() {
        try {
            Files.createDirectories(STAR_FILE.getParent());
            Files.write(STAR_FILE, starredUrls, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Keep the UI responsive even if local preference saving fails.
        }
    }

    private void saveDefaultUnstars() {
        try {
            Files.createDirectories(DEFAULT_UNSTAR_FILE.getParent());
            Files.write(DEFAULT_UNSTAR_FILE, unstarredDefaultUrls, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Keep the UI responsive even if local preference saving fails.
        }
    }

    private static String classifyNews(NewsItem news) {
        String title = news.getTitle();
        // 1. 行事曆
        if (title.contains("行事曆")) {
            return "行事曆";
        }
        // 2. CPE (大學程式能力檢定)
        if (containsAny(title.toUpperCase(), "CPE", "大學程式能力檢定")) {
            return "CPE";
        }
        // 3. 學業/修業資訊 (畢業門檻、選課修業規定、學分等)
        if (containsAny(title, "修業", "學業", "修課", "畢業門檻", "選課", "學分", "規定")) {
            return "學業資訊";
        }
        // 4. 最新消息 (通常包含重要通知、最新重要公告等)
        if (containsAny(title, "最新消息", "重要公告", "重要通知")) {
            return "最新消息";
        }
    
        // 如果都不符合，回傳 null 代表是不需要的訊息
        return null;
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) return true;
        }
        return false;
    }

    private static JLabel categoryTag(String text) {
        JLabel tag = new JLabel(text);
        tag.setFont(AppFonts.CAPTION);
        tag.setForeground(AppColors.ACCENT);
        tag.setBackground(AppColors.ACCENT_LIGHT);
        tag.setOpaque(true);
        tag.setBorder(new EmptyBorder(2, 7, 2, 7));
        return tag;
    }

    private static JLabel metaLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(AppFonts.CAPTION);
        label.setForeground(AppColors.TEXT_TERTIARY);
        return label;
    }

    private static JLabel fieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(AppFonts.BODY_SMALL);
        label.setForeground(AppColors.TEXT_SECONDARY);
        return label;
    }

    private static String extractHost(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            return host != null ? host.replaceFirst("^www\\.", "") : url;
        } catch (Exception e) {
            return url;
        }
    }

    private static void openUrl(String url) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null,
                    "無法開啟連結: " + url,
                    "開啟失敗", JOptionPane.ERROR_MESSAGE);
        }
    }
}
