package model;

import java.util.ArrayList;
import java.util.List;

/**
 * 行事曆上的任務資料。
 * 任務可設定日期、時間、重要程度、完成狀態、分類與最多 10 筆提醒。
 */
public class Task {
    private int id;
    private String title;
    private String description;
    private String date;
    private String time;
    private boolean hasDeadline;
    private boolean important;
    private boolean completed;
    private String category;
    private final List<Reminder> reminders = new ArrayList<>();

    public Task() {}

    public Task(int id, String title, String description,
                String date, String time, boolean hasDeadline) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.date = date;
        this.time = time;
        this.hasDeadline = hasDeadline;
        this.important = false;
        this.completed = false;
        this.category = "";
    }

    public Task(int id, String date, String time, String content) {
        this.id = id;
        this.title = content;
        this.description = "";
        this.date = date;
        this.time = time;
        this.hasDeadline = true;
        this.important = false;
        this.completed = false;
        this.category = "";
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getTitle() { return title != null ? title : ""; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return getTitle(); }
    public void setContent(String c) { setTitle(c); }

    public String getDescription() { return description != null ? description : ""; }
    public void setDescription(String description) { this.description = description; }

    public String getDate() { return date != null ? date : ""; }
    public void setDate(String date) { this.date = date; }

    public String getTime() { return time != null ? time : ""; }
    public void setTime(String time) { this.time = time; }

    public boolean hasDeadline() { return hasDeadline; }
    public void setHasDeadline(boolean hasDeadline) { this.hasDeadline = hasDeadline; }

    public boolean isImportant() { return important; }
    public void setImportant(boolean important) { this.important = important; }

    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }

    public String getCategory() { return category != null ? category : ""; }
    public void setCategory(String category) { this.category = category != null ? category : ""; }

    public List<Reminder> getReminders() { return reminders; }
    public void setReminders(List<Reminder> reminders) {
        this.reminders.clear();
        if (reminders == null) return;
        for (Reminder reminder : reminders) {
            if (reminder == null || this.reminders.size() >= 10) break;
            this.reminders.add(reminder);
        }
    }
}
