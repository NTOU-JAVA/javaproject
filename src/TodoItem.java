/**
 * TodoItem 表示一筆代辦事項。
 * 新增：title（標題）、description（詳細說明）
 * reminderTime 改為可選的 deadline（語意更清楚）
 */
public class TodoItem {
    private int     id;
    private String  title;
    private String  description;  // 詳細說明，可為空字串
    private String  reminderTime; // 格式 YYYY-MM-DD HH:mm，可為 null（無 deadline）
    private boolean completed;

    public TodoItem() {}

    public TodoItem(int id, String title, String description, String reminderTime) {
        this.id          = id;
        this.title       = title;
        this.description = description;
        this.reminderTime = reminderTime;
        this.completed   = false;
    }

    // 向下相容舊建構子
    public TodoItem(int id, String content, String reminderTime) {
        this(id, content, "", reminderTime);
    }

    public int     getId()          { return id; }
    public void    setId(int id)    { this.id = id; }

    public String  getTitle()               { return title != null ? title : ""; }
    public void    setTitle(String title)   { this.title = title; }

    // getContent / setContent 為 legacy alias
    public String  getContent()             { return getTitle(); }
    public void    setContent(String c)     { setTitle(c); }

    public String  getDescription()                   { return description != null ? description : ""; }
    public void    setDescription(String description) { this.description = description; }

    public String  getReminderTime()                       { return reminderTime; }
    public void    setReminderTime(String reminderTime)    { this.reminderTime = reminderTime; }

    public boolean isCompleted()                    { return completed; }
    public void    setCompleted(boolean completed)  { this.completed = completed; }
}