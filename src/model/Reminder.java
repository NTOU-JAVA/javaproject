package model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 單筆提醒設定。
 * 可表示固定時間提醒，或依任務/代辦截止時間往前推算的提醒。
 */
public class Reminder {
    /**
     * ABSOLUTE 代表指定固定提醒時間；
     * BEFORE_DEADLINE 代表依截止時間往前推算。
     */
    public enum Type {
        ABSOLUTE,
        BEFORE_DEADLINE
    }

    private static final DateTimeFormatter DATE_TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private Type type;
    private String dateTime;
    private int minutesBefore;

    public Reminder() {
        this(Type.BEFORE_DEADLINE, "", 10);
    }

    public Reminder(Type type, String dateTime, int minutesBefore) {
        this.type = type != null ? type : Type.BEFORE_DEADLINE;
        this.dateTime = dateTime != null ? dateTime : "";
        this.minutesBefore = Math.max(0, minutesBefore);
    }

    public static Reminder absolute(String dateTime) {
        return new Reminder(Type.ABSOLUTE, dateTime, 0);
    }

    public static Reminder beforeDeadline(int minutesBefore) {
        return new Reminder(Type.BEFORE_DEADLINE, "", minutesBefore);
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type != null ? type : Type.BEFORE_DEADLINE;
    }

    public String getDateTime() {
        return dateTime != null ? dateTime : "";
    }

    public void setDateTime(String dateTime) {
        this.dateTime = dateTime != null ? dateTime : "";
    }

    public int getMinutesBefore() {
        return minutesBefore;
    }

    public void setMinutesBefore(int minutesBefore) {
        this.minutesBefore = Math.max(0, minutesBefore);
    }

    public LocalDateTime resolve(LocalDateTime deadline) {
        try {
            if (type == Type.ABSOLUTE) {
                return LocalDateTime.parse(getDateTime(), DATE_TIME_FMT);
            }
            if (deadline != null) {
                return deadline.minusMinutes(minutesBefore);
            }
        } catch (DateTimeParseException ignored) {
        }
        return null;
    }

    public String toDisplayText() {
        if (type == Type.ABSOLUTE) {
            return getDateTime();
        }
        if (minutesBefore == 0) return "截止時提醒";
        if (minutesBefore % 1440 == 0) return "截止前 " + (minutesBefore / 1440) + " 天";
        if (minutesBefore % 60 == 0) return "截止前 " + (minutesBefore / 60) + " 小時";
        return "截止前 " + minutesBefore + " 分鐘";
    }
}
