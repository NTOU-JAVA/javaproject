/**
 * Task 表示一筆行事曆任務，包含日期、時間與內容。
 */
public class Task {
    private int id;
    private String date;
    private String time;
    private String content;

    public Task() {}

    /**
     * 建構完整的任務項目。
     *
     * @param id      任務編號
     * @param date    任務日期（格式 YYYY-MM-DD）
     * @param time    任務時間（格式 HH:mm）
     * @param content 任務內容
     */
    public Task(int id, String date, String time, String content) {
        this.id = id;
        this.date = date;
        this.time = time;
        this.content = content;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}