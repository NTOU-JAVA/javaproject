/**
 * TodoItem 表示一筆代辦事項，包含內容、提醒時間與完成狀態。
 */
public class TodoItem {
    private int id;
    private String content;
    private String reminderTime; // 格式 YYYY-MM-DD HH:mm，可為 null
    private boolean completed;

    public TodoItem() {}

    /**
     * 建構代辦事項。
     *
     * @param id           代辦編號
     * @param content      代辦內容
     * @param reminderTime 提醒時間（可為 null）
     */
    public TodoItem(int id, String content, String reminderTime) {
        this.id = id;
        this.content = content;
        this.reminderTime = reminderTime;
        this.completed = false;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getReminderTime() { return reminderTime; }
    public void setReminderTime(String reminderTime) { this.reminderTime = reminderTime; }

    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
}