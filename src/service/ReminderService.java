package service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.Timer;
import model.Reminder;
import model.Task;
import model.TodoItem;

public class ReminderService {
    public interface Notifier {
        void showReminder(ReminderNotification notification);
    }

    public enum TargetType {
        TASK,
        TODO
    }

    public static class ReminderNotification {
        private final TargetType targetType;
        private final int targetId;
        private final String title;
        private final String message;

        public ReminderNotification(TargetType targetType, int targetId, String title, String message) {
            this.targetType = targetType;
            this.targetId = targetId;
            this.title = title;
            this.message = message;
        }

        public TargetType getTargetType() { return targetType; }
        public int getTargetId() { return targetId; }
        public String getTitle() { return title; }
        public String getMessage() { return message; }
    }

    private static final DateTimeFormatter DATE_TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final List<Task> tasks;
    private final List<TodoItem> todos;
    private final Notifier notifier;
    private final Runnable onChanged;
    private final Set<String> shownReminderKeys = new HashSet<>();
    private final Timer scanTimer;
    private boolean paused;

    public ReminderService(List<Task> tasks, List<TodoItem> todos,
                           Notifier notifier, Runnable onChanged) {
        this.tasks = tasks;
        this.todos = todos;
        this.notifier = notifier;
        this.onChanged = onChanged != null ? onChanged : () -> {};
        this.scanTimer = new Timer(30_000, e -> scanReminders());
        this.scanTimer.setInitialDelay(5_000);
        markPastRemindersAsShown();
    }

    public void start() {
        scanTimer.start();
    }

    public void stop() {
        scanTimer.stop();
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public boolean togglePaused() {
        paused = !paused;
        return paused;
    }

    private void scanReminders() {
        if (paused) return;

        LocalDateTime now = LocalDateTime.now();
        boolean changed = false;
        changed |= scanTaskReminders(now);
        changed |= scanTodoReminders(now);
        if (changed) onChanged.run();
    }

    private boolean scanTaskReminders(LocalDateTime now) {
        boolean changed = false;
        for (Task task : tasks) {
            LocalDateTime deadline = parseTaskDeadline(task);
            if (deadline == null) continue;

            if (isDue(deadline, now)) {
                if (shownReminderKeys.add(taskDeadlineKey(task))) {
                    notifier.showReminder(new ReminderNotification(
                            TargetType.TASK, task.getId(), "任務到期", task.getTitle()));
                }
                continue;
            }

            for (int i = task.getReminders().size() - 1; i >= 0; i--) {
                Reminder reminder = task.getReminders().get(i);
                LocalDateTime target = reminder.resolve(deadline);
                if (target != null && isDue(target, now)
                        && shownReminderKeys.add(taskReminderKey(task, i, reminder))) {
                    notifier.showReminder(new ReminderNotification(
                            TargetType.TASK, task.getId(),
                            "任務提醒", displayReminder(reminder) + "：" + task.getTitle()));
                    task.getReminders().remove(i);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private boolean scanTodoReminders(LocalDateTime now) {
        boolean changed = false;
        for (TodoItem todo : todos) {
            LocalDateTime deadline = parseTodoDeadline(todo);
            if (deadline == null) continue;

            if (isDue(deadline, now)) {
                if (shownReminderKeys.add(todoDeadlineKey(todo))) {
                    notifier.showReminder(new ReminderNotification(
                            TargetType.TODO, todo.getId(), "待辦到期", todo.getTitle()));
                }
                continue;
            }

            for (int i = todo.getReminders().size() - 1; i >= 0; i--) {
                Reminder reminder = todo.getReminders().get(i);
                LocalDateTime target = reminder.resolve(deadline);
                if (target != null && isDue(target, now)
                        && shownReminderKeys.add(todoReminderKey(todo, i, reminder))) {
                    notifier.showReminder(new ReminderNotification(
                            TargetType.TODO, todo.getId(),
                            "待辦提醒", displayReminder(reminder) + "：" + todo.getTitle()));
                    todo.getReminders().remove(i);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private void markPastRemindersAsShown() {
        LocalDateTime now = LocalDateTime.now();
        boolean changed = false;

        for (Task task : tasks) {
            LocalDateTime deadline = parseTaskDeadline(task);
            if (deadline == null) continue;

            if (isDue(deadline, now)) {
                shownReminderKeys.add(taskDeadlineKey(task));
            }
            for (int i = task.getReminders().size() - 1; i >= 0; i--) {
                Reminder reminder = task.getReminders().get(i);
                LocalDateTime target = reminder.resolve(deadline);
                if (target != null && (isDue(target, now) || isDue(deadline, now))) {
                    shownReminderKeys.add(taskReminderKey(task, i, reminder));
                    task.getReminders().remove(i);
                    changed = true;
                }
            }
        }

        for (TodoItem todo : todos) {
            LocalDateTime deadline = parseTodoDeadline(todo);
            if (deadline == null) continue;

            if (isDue(deadline, now)) {
                shownReminderKeys.add(todoDeadlineKey(todo));
            }
            for (int i = todo.getReminders().size() - 1; i >= 0; i--) {
                Reminder reminder = todo.getReminders().get(i);
                LocalDateTime target = reminder.resolve(deadline);
                if (target != null && isDue(target, now)) {
                    shownReminderKeys.add(todoReminderKey(todo, i, reminder));
                    todo.getReminders().remove(i);
                    changed = true;
                }
            }
        }

        if (changed) onChanged.run();
    }

    private boolean isDue(LocalDateTime target, LocalDateTime now) {
        return !target.isAfter(now);
    }

    private LocalDateTime parseTaskDeadline(Task task) {
        if (task == null || !task.hasDeadline() || task.isCompleted()
                || task.getDate().isEmpty() || task.getTime().isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(task.getDate() + " " + task.getTime(), DATE_TIME_FMT);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private LocalDateTime parseTodoDeadline(TodoItem todo) {
        if (todo == null || todo.isCompleted() || todo.getDeadlineTime() == null
                || todo.getDeadlineTime().isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(todo.getDeadlineTime(), DATE_TIME_FMT);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String taskReminderKey(Task task, int index, Reminder reminder) {
        return "task-reminder:" + task.getId() + ":" + index + ":"
                + reminder.getType() + ":" + reminder.getDateTime() + ":" + reminder.getMinutesBefore();
    }

    private String taskDeadlineKey(Task task) {
        return "task-deadline:" + task.getId() + ":" + task.getDate() + " " + task.getTime();
    }

    private String todoDeadlineKey(TodoItem todo) {
        return "todo-deadline:" + todo.getId() + ":" + todo.getDeadlineTime();
    }

    private String todoReminderKey(TodoItem todo, int index, Reminder reminder) {
        return "todo-reminder:" + todo.getId() + ":" + index + ":"
                + reminder.getType() + ":" + reminder.getDateTime() + ":" + reminder.getMinutesBefore();
    }

    private String displayReminder(Reminder reminder) {
        if (reminder.getType() == Reminder.Type.ABSOLUTE) {
            return reminder.getDateTime();
        }
        int minutes = reminder.getMinutesBefore();
        if (minutes == 0) return "截止時提醒";
        if (minutes % 1440 == 0) return "截止前 " + (minutes / 1440) + " 天";
        if (minutes % 60 == 0) return "截止前 " + (minutes / 60) + " 小時";
        return "截止前 " + minutes + " 分鐘";
    }
}
