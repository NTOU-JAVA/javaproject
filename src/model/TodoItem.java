package model;

import java.util.ArrayList;
import java.util.List;

public class TodoItem {
    private int id;
    private String title;
    private String description;
    private String reminderTime;
    private String deadlineTime;
    private boolean completed;
    private String category;
    private String sourceUrl;
    private final List<Reminder> reminders = new ArrayList<>();

    public TodoItem() {}

    public TodoItem(int id, String title, String description, String reminderTime) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.reminderTime = reminderTime;
        this.deadlineTime = reminderTime;
        this.completed = false;
        this.category = "";
    }

    public TodoItem(int id, String content, String reminderTime) {
        this(id, content, "", reminderTime);
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getTitle() { return title != null ? title : ""; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return getTitle(); }
    public void setContent(String c) { setTitle(c); }

    public String getDescription() { return description != null ? description : ""; }
    public void setDescription(String description) { this.description = description; }

    public String getReminderTime() { return reminderTime; }
    public void setReminderTime(String reminderTime) { this.reminderTime = reminderTime; }

    public String getDeadlineTime() {
        return deadlineTime != null ? deadlineTime : reminderTime;
    }

    public void setDeadlineTime(String deadlineTime) {
        this.deadlineTime = deadlineTime;
        this.reminderTime = deadlineTime;
    }

    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }

    public String getCategory() { return category != null ? category : ""; }
    public void setCategory(String category) { this.category = category != null ? category : ""; }

    public String getSourceUrl() { 
    return sourceUrl != null ? sourceUrl : ""; 
    }

    public void setSourceUrl(String sourceUrl) { 
        this.sourceUrl = sourceUrl; 
    }

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
