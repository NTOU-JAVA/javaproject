/**
 * NewsItem 表示一筆學校公告。
 */
public class NewsItem {
    private int     id;
    private String  title;
    private String  url;
    private String  fetchedTime;

    public NewsItem() {}

    public NewsItem(int id, String title, String url, String fetchedTime) {
        this.id         = id;
        this.title      = title;
        this.url        = url;
        this.fetchedTime = fetchedTime;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getTitle() { return title != null ? title : ""; }
    public void setTitle(String title) { this.title = title; }

    public String getUrl() { return url != null ? url : ""; }
    public void setUrl(String url) { this.url = url; }

    public String getFetchedTime() { return fetchedTime != null ? fetchedTime : ""; }
    public void setFetchedTime(String fetchedTime) { this.fetchedTime = fetchedTime; }
}
