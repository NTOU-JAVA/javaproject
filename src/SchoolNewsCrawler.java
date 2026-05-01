import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.util.*;

/**
 * SchoolNewsCrawler：從學校網站爬取公告資訊。
 * 支援後台非同步執行，避免阻塞 UI。
 */
public class SchoolNewsCrawler {
    private static final String SCHOOL_URL = "https://cse.ntou.edu.tw/";
    private List<NewsItem> cachedNews = new ArrayList<>();
    private boolean isLoading = false;
    private Runnable onLoadComplete;

    public SchoolNewsCrawler(Runnable onLoadComplete) {
        this.onLoadComplete = onLoadComplete;
    }

    /**
     * 在後台執行爬蟲（非同步）。
     */
    public void fetchNewsAsync() {
        if (isLoading) return;
        isLoading = true;

        new Thread(() -> {
            try {
                fetchNews();
            } finally {
                isLoading = false;
                if (onLoadComplete != null) onLoadComplete.run();
            }
        }).start();
    }

    /**
     * 同步爬取公告與系所最新消息（用於初始化）。
     */
    private void fetchNews() {
        cachedNews.clear();
        try {
            String fetchTime = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    .format(java.time.LocalDateTime.now());

            cachedNews.addAll(fetchSchoolAnnouncements(fetchTime));
            cachedNews.addAll(NtouCseScraper.scrapeRecentNews());
        } catch (Exception e) {
            System.err.println("爬蟲錯誤: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private List<NewsItem> fetchSchoolAnnouncements(String fetchTime) throws Exception {
        List<NewsItem> result = new ArrayList<>();
        Document doc = Jsoup.connect(SCHOOL_URL).timeout(10000).get();
        Elements links = doc.select(".mbox .mtitle a");

        int id = 1;
        for (Element link : links) {
            String title = link.text();
            String href = link.attr("href");
            if (title.isEmpty()) continue;

            if (!href.startsWith("http")) {
                href = SCHOOL_URL + href;
            }
            result.add(new NewsItem(id++, title, href, fetchTime));
        }
        return result;
    }

    /**
     * 獲取已快取的公告列表。
     */
    public List<NewsItem> getCachedNews() {
        return new ArrayList<>(cachedNews);
    }

    /**
     * 檢查是否正在載入。
     */
    public boolean isLoading() {
        return isLoading;
    }

    /**
     * 清空快取（手動）。
     */
    public void clearCache() {
        cachedNews.clear();
    }
}
