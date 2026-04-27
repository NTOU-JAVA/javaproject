import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;
import java.time.Duration;
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
     * 同步爬取公告（支援 JavaScript）。
     */
    private void fetchNews() {
        cachedNews.clear();
        WebDriver driver = null;
        try {
            System.out.println("==== 初始化 Selenium ChromeDriver ====");
            
            // 設定 Chrome 選項
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--headless"); // 無頭模式
            
            driver = new ChromeDriver(options);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            
            System.out.println("✓ ChromeDriver 啟動成功");
            System.out.println("正在訪問: " + SCHOOL_URL);
            
            driver.get(SCHOOL_URL);
            
            // 等待頁面加載
            wait.until(ExpectedConditions.presenceOfElementLocated(By.className("mbox")));
            System.out.println("✓ 主頁加載完成");
            
            // 獲取所有新聞
            String fetchTime = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    .format(java.time.LocalDateTime.now());
            
            int id = 1;
            
            // 1. 爬取主頁新聞（靜態內容 + 已渲染的 JS）
            System.out.println("\n==== 爬取主頁公告 ====");
            String pageSource = driver.getPageSource();
            Document doc = Jsoup.parse(pageSource);
            Elements links = doc.select(".mbox .mtitle a");
            
            System.out.println("找到 " + links.size() + " 個連結");
            
            for (Element link : links) {
                String title = link.text().trim();
                String href = link.attr("href");
                
                if (!title.isEmpty() && !href.isEmpty()) {
                    if (!href.startsWith("http")) {
                        href = SCHOOL_URL + href;
                    }
                    cachedNews.add(new NewsItem(id++, title, href, fetchTime));
                    System.out.println("✓ " + title);
                }
            }
            
            // 2. 嘗試爬取「最新消息」標籤頁內容
            System.out.println("\n==== 嘗試載入「最新消息」標籤頁 ====");
            try {
                // 找到「最新消息」標籤
                var latestNewsTab = wait.until(
                    ExpectedConditions.elementToBeClickable(By.id("sm_a36d3253d06c1f9b41d3452cb0a98cded_0"))
                );
                System.out.println("✓ 找到「最新消息」標籤");
                
                latestNewsTab.click();
                Thread.sleep(2000); // 等待內容加載
                
                // 重新取得頁面源碼
                pageSource = driver.getPageSource();
                doc = Jsoup.parse(pageSource);
                
                // 尋找標籤內容區域
                Elements newsItems = doc.select("#cmb_460_0 a");
                System.out.println("在最新消息中找到 " + newsItems.size() + " 個連結");
                
                for (Element item : newsItems) {
                    String title = item.text().trim();
                    String href = item.attr("href");
                    
                    if (!title.isEmpty() && !href.isEmpty()) {
                        if (!href.startsWith("http")) {
                            href = SCHOOL_URL + href;
                        }
                        cachedNews.add(new NewsItem(id++, title, href, fetchTime));
                        System.out.println("✓ [最新消息] " + title);
                    }
                }
            } catch (Exception e) {
                System.out.println("⚠ 無法載入最新消息標籤: " + e.getMessage());
            }
            
            // 3. 嘗試爬取「學業資訊」標籤頁內容
            System.out.println("\n==== 嘗試載入「學業資訊」標籤頁 ====");
            try {
                // 找到「學業資訊」標籤
                var academicTab = wait.until(
                    ExpectedConditions.elementToBeClickable(By.id("sm_a36d3253d06c1f9b41d3452cb0a98cded_1"))
                );
                System.out.println("✓ 找到「學業資訊」標籤");
                
                academicTab.click();
                Thread.sleep(2000); // 等待內容加載
                
                // 重新取得頁面源碼
                pageSource = driver.getPageSource();
                doc = Jsoup.parse(pageSource);
                
                // 尋找標籤內容區域
                Elements academicItems = doc.select("#cmb_460_1 a");
                System.out.println("在學業資訊中找到 " + academicItems.size() + " 個連結");
                
                for (Element item : academicItems) {
                    String title = item.text().trim();
                    String href = item.attr("href");
                    
                    if (!title.isEmpty() && !href.isEmpty()) {
                        if (!href.startsWith("http")) {
                            href = SCHOOL_URL + href;
                        }
                        cachedNews.add(new NewsItem(id++, title, href, fetchTime));
                        System.out.println("✓ [學業資訊] " + title);
                    }
                }
            } catch (Exception e) {
                System.out.println("⚠ 無法載入學業資訊標籤: " + e.getMessage());
            }
            
            System.out.println("\n✓ 總共獲得 " + cachedNews.size() + " 筆公告");
            
        } catch (Exception e) {
            System.err.println("❌ 爬蟲錯誤: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                    System.out.println("\n✓ ChromeDriver 已關閉");
                } catch (Exception e) {
                    System.err.println("關閉 ChromeDriver 時出錯: " + e.getMessage());
                }
            }
        }
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
