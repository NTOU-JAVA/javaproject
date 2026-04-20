/**
 * TodoItem 表示一筆任務記錄，包含日期、時間與內容。
 */
public class TodoItem {
    private int id;
    private String date;
    private String time;
    private String content;

    /**
     * 預設建構子，供序列化或框架使用。
     */
    public TodoItem() {}

    /**
     * 建構完整的代辦項目。
     *
     * @param id    代辦項目編號
     * @param date  任務日期（格式 YYYY-MM-DD）
     * @param time  任務時間，格式 HH:mm
     * @param content 代辦內容
     */
    public TodoItem(int id, String date, String time, String content) {
        this.id = id;
        this.date = date;
        this.time = time;
        this.content = content;
    }

    /**
     * 建構一筆預設時間為 09:00 的代辦事項。
     *
     * @param id 代辦項目編號
     * @param date 代辦日期
     * @param content 代辦內容
     */
    public TodoItem(int id, String date, String content) {
        this(id, date, "09:00", content);
    }

    /**
     * 取得代辦項目編號。
     *
     * @return 代辦編號
     */
    public int getId() {
        return id;
    }

    /**
     * 設定代辦項目編號。
     *
     * @param id 代辦編號
     */
    public void setId(int id) {
        this.id = id;
    }

    /**
     * 取得代辦日期。
     *
     * @return 日期字串
     */
    public String getDate() {
        return date;
    }

    /**
     * 設定代辦日期。
     *
     * @param date 日期字串
     */
    public void setDate(String date) {
        this.date = date;
    }

    /**
     * 取得代辦時間。
     *
     * @return 時間字串
     */
    public String getTime() {
        return time;
    }

    /**
     * 設定代辦時間。
     *
     * @param time 時間字串，格式 HH:mm
     */
    public void setTime(String time) {
        this.time = time;
    }

    /**
     * 取得代辦內容。
     *
     * @return 內容文字
     */
    public String getContent() {
        return content;
    }

    /**
     * 設定代辦內容。
     *
     * @param content 內容文字
     */
    public void setContent(String content) {
        this.content = content;
    }
}