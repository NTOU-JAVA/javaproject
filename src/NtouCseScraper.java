import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class NtouCseScraper {

    static final String BASE_URL         = "https://cse.ntou.edu.tw";
    static final LocalDate ONE_MONTH_AGO = LocalDate.now().minusMonths(1);
    static final DateTimeFormatter FMT   = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    static final Map<String, String> CATEGORIES = new LinkedHashMap<>();
    static {
        CATEGORIES.put("最新消息", "1034");
        CATEGORIES.put("學業資訊", "1112");
    }

    public static void main(String[] args) {
        List<NewsItem> news = scrapeRecentNews();
        for (NewsItem item : news) {
            System.out.println("標題：" + item.getTitle());
            System.out.println("網址：" + item.getUrl());
            System.out.println("時間：" + item.getFetchedTime());
            System.out.println("---");
        }
    }

    public static List<NewsItem> scrapeRecentNews() {
        List<NewsItem> result = new ArrayList<>();
        String fetchTime = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .format(java.time.LocalDateTime.now());

        int id = 1;
        for (Map.Entry<String, String> entry : CATEGORIES.entrySet()) {
            id = scrapeCategory(entry.getKey(), entry.getValue(), result, fetchTime, id);
        }
        return result;
    }

    private static int scrapeCategory(String categoryName, String catId,
                                      List<NewsItem> result,
                                      String fetchTime,
                                      int startId) {
        for (int page = 1; page <= 10; page++) {
            String url = String.format(
                "%s/p/403-1063-%s-%d.php?Lang=zh-tw",
                BASE_URL, catId, page
            );

            try {
                Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36")
                    .timeout(30_000)
                    .get();

                Elements items = doc.select("div.row.listBS");
                if (items.isEmpty()) {
                    break;
                }

                boolean stop = false;
                for (Element item : items) {
                    Element link   = item.selectFirst("div.mtitle > a");
                    Element dateEl = item.selectFirst("i.mdate");
                    if (link == null) continue;

                    String title = link.text().trim();
                    String href  = link.attr("href");
                    String dateStr = dateEl != null ? dateEl.text().trim() : "";

                    if (!href.startsWith("http")) {
                        href = BASE_URL + (href.startsWith("/") ? "" : "/") + href.replaceFirst("^\\./", "");
                    }

                    String itemTime = fetchTime;
                    if (!dateStr.isEmpty()) {
                        try {
                            LocalDate date = LocalDate.parse(dateStr, FMT);
                            if (date.isBefore(ONE_MONTH_AGO)) {
                                stop = true;
                                break;
                            }
                            itemTime = dateStr;
                        } catch (Exception ignored) {
                            // 解析失敗時保留目前抓取時間
                        }
                    }

                    result.add(new NewsItem(startId++,
                            String.format("【%s】 %s", categoryName, title),
                            href, itemTime));
                }

                if (stop) break;
                Thread.sleep(1000);

            } catch (IOException e) {
                System.err.println("連線失敗：" + e.getMessage());
                break;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return startId;
    }

    static void scrapeCategory(String catId) {
        for (int page = 1; page <= 10; page++) {
            String url = String.format(
                "%s/p/403-1063-%s-%d.php?Lang=zh-tw",
                BASE_URL, catId, page
            );

            try {
                Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36")
                    .timeout(30_000)
                    .get();

                Elements items = doc.select("div.row.listBS");
                if (items.isEmpty()) {
                    System.out.println("第 " + page + " 頁無資料，停止");
                    break;
                }

                boolean stop = false;
                for (Element item : items) {
                    Element link   = item.selectFirst("div.mtitle > a");
                    Element dateEl = item.selectFirst("i.mdate");
                    if (link == null) continue;

                    String dateStr = dateEl != null ? dateEl.text().trim() : "";
                    if (!dateStr.isEmpty()) {
                        try {
                            LocalDate date = LocalDate.parse(dateStr, FMT);
                            if (date.isBefore(ONE_MONTH_AGO)) {
                                stop = true;
                                break;
                            }
                        } catch (Exception ignored) {}
                    }

                    System.out.println("標題：" + link.text().trim());
                    System.out.println("網址：" + link.attr("href"));
                    System.out.println("日期：" + dateStr);
                    System.out.println("---");
                }

                if (stop) break;
                Thread.sleep(1000);

            } catch (IOException e) {
                System.err.println("連線失敗：" + e.getMessage());
                break;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}