package service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import model.Course;
import model.Schedule;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

public class PdfScheduleImporter {
    private static final String[] DAY_NAMES = {"一", "二", "三", "四", "五", "六", "日"};
    private static final Pattern TIME_PATTERN = Pattern.compile("\\d{1,2}:\\d{2}~\\d{1,2}:\\d{2}");
    private static final Pattern TERM_PATTERN = Pattern.compile("(\\d{3})學年度第(\\d+)學期");
    private static final Pattern CLASS_SUFFIX_PATTERN = Pattern.compile("^(.*?)(\\d[A-Z0-9]*|[一二三四五六七八九十]+[甲乙丙丁戊己庚辛壬癸A-Z]?)$");

    public Schedule importSchedule(File pdfFile, int scheduleId) throws IOException {
        if (pdfFile == null || !pdfFile.isFile()) {
            throw new IOException("PDF 檔案不存在。");
        }

        PositionedTextStripper stripper = new PositionedTextStripper();
        try (PDDocument doc = Loader.loadPDF(pdfFile)) {
            stripper.getText(doc);
        }

        List<Token> tokens = stripper.getTokens();
        if (tokens.isEmpty()) {
            throw new IOException("PDF 中沒有可解析的文字。");
        }

        String scheduleName = buildScheduleName(tokens, pdfFile);
        Map<Integer, Float> dayCenters = findDayCenters(tokens);
        List<PeriodMark> periodMarks = findPeriodMarks(tokens);
        if (dayCenters.size() < 5 || periodMarks.isEmpty()) {
            throw new IOException("找不到課表的星期欄位或節次列，無法匯入。");
        }

        Map<CellKey, List<Token>> cellTokens = collectCellTokens(tokens, dayCenters, periodMarks);
        Map<CourseKey, List<Slot>> groupedSlots = new LinkedHashMap<>();

        List<Map.Entry<CellKey, List<Token>>> orderedCells = new ArrayList<>(cellTokens.entrySet());
        orderedCells.sort(Comparator
                .comparingInt((Map.Entry<CellKey, List<Token>> entry) -> entry.getKey().day)
                .thenComparingInt(entry -> entry.getKey().period));

        for (Map.Entry<CellKey, List<Token>> entry : orderedCells) {
            CellKey cell = entry.getKey();
            List<String> lines = toDistinctLines(entry.getValue());
            if (lines.size() < 4) continue;

            CourseInfo info = new CourseInfo(lines.get(0), lines.get(1), lines.get(2), lines.get(3));
            if (info.name.isEmpty() || info.code.isEmpty()) continue;

            CourseKey courseKey = new CourseKey(info);
            groupedSlots.computeIfAbsent(courseKey, ignored -> new ArrayList<>())
                    .add(new Slot(cell.day, cell.period, cell.period));
        }

        Schedule schedule = new Schedule(scheduleId, scheduleName);
        int courseId = 1;
        for (Map.Entry<CourseKey, List<Slot>> entry : groupedSlots.entrySet()) {
            CourseKey key = entry.getKey();
            List<Slot> slots = mergeSlots(entry.getValue());
            if (slots.isEmpty()) continue;

            Slot first = slots.get(0);
            Course course = new Course(courseId++, key.name, key.code, key.location, "",
                    first.day, first.startPeriod, first.endPeriod, "");
            course.setDepartment(key.department);
            course.setClassYear(key.classYear);
            course.setColorIndex((course.getId() - 1) % 12);
            course.setScheduleString(buildScheduleString(slots));
            schedule.getCourses().add(course);
        }

        if (schedule.getCourses().isEmpty()) {
            throw new IOException("沒有解析到任何課程。");
        }
        return schedule;
    }

    private String buildScheduleName(List<Token> tokens, File pdfFile) {
        for (Token token : tokens) {
            Matcher matcher = TERM_PATTERN.matcher(token.text);
            if (matcher.find()) {
                return matcher.group(1) + "-" + matcher.group(2) + " 匯入課表";
            }
        }
        String name = pdfFile.getName();
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        return name + " 匯入課表";
    }

    private Map<Integer, Float> findDayCenters(List<Token> tokens) {
        Map<Integer, Float> centers = new HashMap<>();
        for (Token token : tokens) {
            for (int i = 0; i < DAY_NAMES.length; i++) {
                if (("星期" + DAY_NAMES[i]).equals(token.text)) {
                    centers.put(i + 1, token.centerX());
                }
            }
        }
        return centers;
    }

    private List<PeriodMark> findPeriodMarks(List<Token> tokens) {
        List<PeriodMark> marks = new ArrayList<>();
        for (Token token : tokens) {
            Integer period = parsePeriod(token.text);
            if (period != null && period >= 0 && period <= 14) {
                marks.add(new PeriodMark(period, token.y));
            }
        }
        marks.sort(Comparator.comparingDouble(mark -> mark.y));
        return marks;
    }

    private Map<CellKey, List<Token>> collectCellTokens(
            List<Token> tokens, Map<Integer, Float> dayCenters, List<PeriodMark> periodMarks) {
        float minDayX = dayCenters.values().stream().min(Float::compare).orElse(0f) - 30f;
        float maxDayX = dayCenters.values().stream().max(Float::compare).orElse(0f) + 45f;
        float firstPeriodY = periodMarks.get(0).y - 20f;
        float lastPeriodY = periodMarks.get(periodMarks.size() - 1).y + 35f;

        Map<CellKey, List<Token>> cells = new HashMap<>();
        for (Token token : tokens) {
            if (token.centerX() < minDayX || token.centerX() > maxDayX) continue;
            if (token.y < firstPeriodY || token.y > lastPeriodY) continue;
            if (isScaffoldToken(token.text)) continue;

            int day = nearestDay(token.centerX(), dayCenters);
            int period = nearestPeriod(token.y, periodMarks);
            if (day < 1 || period < 0) continue;

            cells.computeIfAbsent(new CellKey(day, period), ignored -> new ArrayList<>()).add(token);
        }
        return cells;
    }

    private boolean isScaffoldToken(String text) {
        if (text == null || text.isBlank()) return true;
        if (text.startsWith("星期")) return true;
        if (parsePeriod(text) != null) return true;
        return TIME_PATTERN.matcher(text).matches();
    }

    private int nearestDay(float x, Map<Integer, Float> dayCenters) {
        int day = -1;
        float bestDistance = Float.MAX_VALUE;
        for (Map.Entry<Integer, Float> entry : dayCenters.entrySet()) {
            float distance = Math.abs(x - entry.getValue());
            if (distance < bestDistance) {
                bestDistance = distance;
                day = entry.getKey();
            }
        }
        return day;
    }

    private int nearestPeriod(float y, List<PeriodMark> marks) {
        int period = -1;
        float bestDistance = Float.MAX_VALUE;
        for (PeriodMark mark : marks) {
            float distance = Math.abs(y - mark.y);
            if (distance < bestDistance) {
                bestDistance = distance;
                period = mark.period;
            }
        }
        return period;
    }

    private List<String> toDistinctLines(List<Token> tokens) {
        tokens.sort(Comparator
                .comparingDouble((Token token) -> token.y)
                .thenComparingDouble(token -> token.x));
        List<LineBucket> buckets = new ArrayList<>();
        for (Token token : tokens) {
            LineBucket target = null;
            for (LineBucket bucket : buckets) {
                if (Math.abs(bucket.y - token.y) < 3.5f) {
                    target = bucket;
                    break;
                }
            }
            if (target == null) {
                target = new LineBucket(token.y);
                buckets.add(target);
            }
            target.tokens.add(token);
        }

        List<String> lines = new ArrayList<>();
        for (LineBucket bucket : buckets) {
            bucket.tokens.sort(Comparator.comparingDouble(token -> token.x));
            StringBuilder line = new StringBuilder();
            for (Token token : bucket.tokens) {
                if (line.length() > 0) line.append(' ');
                line.append(token.text);
            }
            String normalized = normalizeWhitespace(line.toString());
            if (!normalized.isEmpty() && (lines.isEmpty() || !lines.get(lines.size() - 1).equals(normalized))) {
                lines.add(normalized);
            }
        }
        return lines;
    }

    private List<Slot> mergeSlots(List<Slot> rawSlots) {
        rawSlots.sort(Comparator
                .comparingInt((Slot slot) -> slot.day)
                .thenComparingInt(slot -> slot.startPeriod));

        List<Slot> merged = new ArrayList<>();
        for (Slot slot : rawSlots) {
            if (merged.isEmpty()) {
                merged.add(new Slot(slot.day, slot.startPeriod, slot.endPeriod));
                continue;
            }
            Slot last = merged.get(merged.size() - 1);
            if (last.day == slot.day && slot.startPeriod <= last.endPeriod + 1) {
                last.endPeriod = Math.max(last.endPeriod, slot.endPeriod);
            } else {
                merged.add(new Slot(slot.day, slot.startPeriod, slot.endPeriod));
            }
        }
        return merged;
    }

    private String buildScheduleString(List<Slot> slots) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            sb.append("星期").append(DAY_NAMES[slot.day - 1])
              .append(" 第").append(slot.startPeriod).append("～").append(slot.endPeriod).append("節");
            if (i < slots.size() - 1) sb.append(";");
        }
        return sb.toString();
    }

    private Integer parsePeriod(String text) {
        if (text == null || !text.startsWith("第") || !text.endsWith("節")) return null;
        String value = text.substring(1, text.length() - 1).trim();
        if ("0".equals(value)) return 0;
        return switch (value) {
            case "一" -> 1;
            case "二" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            case "七" -> 7;
            case "八" -> 8;
            case "九" -> 9;
            case "十" -> 10;
            case "十一" -> 11;
            case "十二" -> 12;
            case "十三" -> 13;
            case "十四" -> 14;
            default -> null;
        };
    }

    private static String normalizeWhitespace(String value) {
        return value == null ? "" : value.replace('\u3000', ' ').replaceAll("\\s+", " ").trim();
    }

    private static DepartmentClass splitDepartmentClass(String text) {
        String normalized = normalizeWhitespace(text);
        Matcher matcher = CLASS_SUFFIX_PATTERN.matcher(normalized);
        if (matcher.matches()) {
            return new DepartmentClass(matcher.group(1), matcher.group(2));
        }
        return new DepartmentClass(normalized, "");
    }

    private static class PositionedTextStripper extends PDFTextStripper {
        private final List<Token> tokens = new ArrayList<>();

        PositionedTextStripper() throws IOException {
            setSortByPosition(true);
            setShouldSeparateByBeads(false);
        }

        List<Token> getTokens() {
            return tokens;
        }

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
            addTokens(textPositions);
        }

        private void addTokens(List<TextPosition> positions) {
            StringBuilder sb = new StringBuilder();
            float minX = 0f;
            float maxX = 0f;
            float y = 0f;
            TextPosition previous = null;

            for (TextPosition position : positions) {
                String unicode = position.getUnicode();
                if (unicode == null || unicode.isEmpty()) continue;

                float x = position.getXDirAdj();
                float charEndX = x + position.getWidthDirAdj();
                boolean blank = unicode.trim().isEmpty();
                boolean largeGap = previous != null
                        && x - (previous.getXDirAdj() + previous.getWidthDirAdj()) > Math.max(9f, position.getWidthDirAdj() * 2.8f);

                if (blank || largeGap) {
                    flush(sb, minX, maxX, y);
                    sb.setLength(0);
                    previous = blank ? null : position;
                    if (blank) continue;
                }

                if (sb.length() == 0) {
                    minX = x;
                    maxX = charEndX;
                    y = position.getYDirAdj();
                } else {
                    maxX = Math.max(maxX, charEndX);
                    y = (y + position.getYDirAdj()) / 2f;
                }
                sb.append(unicode);
                previous = position;
            }
            flush(sb, minX, maxX, y);
        }

        private void flush(StringBuilder sb, float x, float maxX, float y) {
            String text = normalizeWhitespace(sb.toString());
            if (!text.isEmpty()) {
                tokens.add(new Token(text, x, y, Math.max(1f, maxX - x)));
            }
        }
    }

    private static class CourseInfo {
        final String name;
        final String code;
        final String department;
        final String classYear;
        final String location;

        CourseInfo(String name, String code, String deptClass, String location) {
            DepartmentClass dc = splitDepartmentClass(deptClass);
            this.name = normalizeWhitespace(name);
            this.code = normalizeWhitespace(code).toUpperCase(Locale.ROOT);
            this.department = dc.department;
            this.classYear = dc.classYear;
            this.location = normalizeWhitespace(location);
        }
    }

    private static class CourseKey {
        final String name;
        final String code;
        final String department;
        final String classYear;
        final String location;

        CourseKey(CourseInfo info) {
            this.name = info.name;
            this.code = info.code;
            this.department = info.department;
            this.classYear = info.classYear;
            this.location = info.location;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof CourseKey other)) return false;
            return name.equals(other.name) && code.equals(other.code)
                    && department.equals(other.department) && classYear.equals(other.classYear)
                    && location.equals(other.location);
        }

        @Override
        public int hashCode() {
            int result = name.hashCode();
            result = 31 * result + code.hashCode();
            result = 31 * result + department.hashCode();
            result = 31 * result + classYear.hashCode();
            result = 31 * result + location.hashCode();
            return result;
        }
    }

    private static class Token {
        final String text;
        final float x;
        final float y;
        final float width;

        Token(String text, float x, float y, float width) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.width = width;
        }

        float centerX() {
            return x + width / 2f;
        }
    }

    private record PeriodMark(int period, float y) {}
    private record CellKey(int day, int period) {}
    private record DepartmentClass(String department, String classYear) {}

    private static class Slot {
        final int day;
        final int startPeriod;
        int endPeriod;

        Slot(int day, int startPeriod, int endPeriod) {
            this.day = day;
            this.startPeriod = startPeriod;
            this.endPeriod = endPeriod;
        }
    }

    private static class LineBucket {
        final float y;
        final List<Token> tokens = new ArrayList<>();

        LineBucket(float y) {
            this.y = y;
        }
    }
}
