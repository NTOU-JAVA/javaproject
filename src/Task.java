/**
 * Task 表示一筆行事曆任務。
 * 新增：title（標題）、description（詳細內容）、completed（完成狀態）
 * deadline 即原本的 date+time，可選（hasDeadline 為 false 時不顯示）
 */
public class Task {
    private int     id;
    private String  title;
    private String  description; // 詳細說明，可為空字串
    private String  date;        // 格式 YYYY-MM-DD，hasDeadline=false 時為空字串
    private String  time;        // 格式 HH:mm，hasDeadline=false 時為空字串
    private boolean hasDeadline;
    private boolean important;
    private boolean completed;

    public Task() {}

    public Task(int id, String title, String description,
                String date, String time, boolean hasDeadline) {
        this.id          = id;
        this.title       = title;
        this.description = description;
        this.date        = date;
        this.time        = time;
        this.hasDeadline = hasDeadline;
        this.important   = false;
        this.completed   = false;
    }

    // 向下相容舊建構子（date+time 固定存在）
    public Task(int id, String date, String time, String content) {
        this.id          = id;
        this.title       = content;
        this.description = "";
        this.date        = date;
        this.time        = time;
        this.hasDeadline = true;
        this.important   = false;
        this.completed   = false;
    }

    public int     getId()          { return id; }
    public void    setId(int id)    { this.id = id; }

    public String  getTitle()                { return title != null ? title : ""; }
    public void    setTitle(String title)    { this.title = title; }

    // getContent / setContent 為 legacy alias（XML 讀寫與 CalendarPanel 仍用此名）
    public String  getContent()              { return getTitle(); }
    public void    setContent(String c)      { setTitle(c); }

    public String  getDescription()                    { return description != null ? description : ""; }
    public void    setDescription(String description)  { this.description = description; }

    public String  getDate()                { return date != null ? date : ""; }
    public void    setDate(String date)     { this.date = date; }

    public String  getTime()                { return time != null ? time : ""; }
    public void    setTime(String time)     { this.time = time; }

    public boolean hasDeadline()                       { return hasDeadline; }
    public void    setHasDeadline(boolean hasDeadline) { this.hasDeadline = hasDeadline; }

    public boolean isImportant()                    { return important; }
    public void    setImportant(boolean important)  { this.important = important; }

    public boolean isCompleted()                    { return completed; }
    public void    setCompleted(boolean completed)  { this.completed = completed; }
}