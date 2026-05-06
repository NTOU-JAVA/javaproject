import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

/**
 * TronclassService：整合登入 Cookie 後的資料同步。
 *  - parseName()     從首頁 HTML 解析使用者姓名
 *  - syncTodos()     抓取待辦事項並合併寫入 todos.xml
 */
public class TronclassService {

    private static final String BASE_URL = "https://tronclass.ntou.edu.tw";

    private static final String[] TODO_CANDIDATES = {
        "/api/todos",
        "/api/v2/todos",
        "/api/todo/list",
        "/api/activities/todo",
        "/api/user/todos",
        "/api/lms/todos",
        "/api/course/todos",
        "/api/notifications/todo",
        "/api/homepage/todos",
        "/lms/api/todos",
    };

    private static final List<String> FILTER_KEYWORDS =
        Arrays.asList("工程認證", "COVID", "COVID-19");

    // ── 公開 API ────────────────────────────────────────────────────────────

    /**
     * 從首頁 HTML 解析登入後的使用者姓名。
     * 嘗試多種常見的 HTML 模式（ng-init、JSON、data-name 等）。
     *
     * @param cookie 已登入的 Cookie 字串
     * @return 使用者姓名，若解析失敗則回傳 null
     */
    public static String parseName(String cookie) throws Exception {
        String html = get(BASE_URL + "/user/index", cookie);
        if (html == null || html.length() < 100) return null;

        // 1. ng-init="userCurrentName='林昱安'"
        Pattern p1 = Pattern.compile("userCurrentName\\s*=\\s*'([^']+)'");
        Matcher m1 = p1.matcher(html);
        if (m1.find()) return m1.group(1).trim();

        // 2. "userCurrentName":"林昱安"  （JSON 格式）
        Pattern p2 = Pattern.compile("\"userCurrentName\"\\s*:\\s*\"([^\"]+)\"");
        Matcher m2 = p2.matcher(html);
        if (m2.find()) return m2.group(1).trim();

        // 3. id="userCurrentName"...>林昱安<
        Pattern p3 = Pattern.compile("id=[\"']userCurrentName[\"'][^>]*>([^<]+)<");
        Matcher m3 = p3.matcher(html);
        if (m3.find()) return m3.group(1).trim();

        // 4. span ...ng-bind="currentUserName"...>林昱安<
        Pattern p4 = Pattern.compile("ng-bind=[\"']currentUserName[\"'][^>]*>([^<]+)<");
        Matcher m4 = p4.matcher(html);
        if (m4.find()) return m4.group(1).trim();

        // 5. data-name="林昱安"
        Pattern p5 = Pattern.compile("data-name=[\"']([^\"']+)[\"']");
        Matcher m5 = p5.matcher(html);
        if (m5.find()) return m5.group(1).trim();

        return null;
    }

    /**
     * 從 Tronclass 取得待辦事項，並合併寫入 data/todos.xml。
     *
     * @param cookie 已登入的 Cookie 字串
     * @return 新增的待辦事項數量；-1 表示找不到 API
     */
    public static int syncTodos(String cookie, java.util.List<TodoItem> currentTodos,
                                 Runnable saveCallback) throws Exception {
        // 1. 找可用的 API 路徑
        String endpoint = findWorkingEndpoint(cookie);
        if (endpoint == null) return -1;

        // 2. 取得 JSON
        String json = get(BASE_URL + endpoint, cookie);
        if (json == null) return -1;

        // 3. 解析
        List<TronclassTodo> fetched = parseTodos(json);

        // 4. 收集現有 titles（避免重複）
        Set<String> existingTitles = new HashSet<>();
        int maxId = 0;
        for (TodoItem t : currentTodos) {
            existingTitles.add(t.getTitle());
            maxId = Math.max(maxId, t.getId());
        }

        // 5. 合併新資料
        int added = 0;
        for (TronclassTodo tt : fetched) {
            if (existingTitles.contains(tt.title)) continue;
            maxId++;
            TodoItem item = new TodoItem(maxId, tt.title, "", formatDeadline(tt.deadline));
            currentTodos.add(item);
            added++;
        }

        // 6. 通知儲存
        if (added > 0 && saveCallback != null) saveCallback.run();
        return added;
    }

    // ── 內部邏輯 ────────────────────────────────────────────────────────────

    private static String findWorkingEndpoint(String cookie) {
        for (String path : TODO_CANDIDATES) {
            try {
                String resp = get(BASE_URL + path, cookie);
                if (resp != null && resp.length() > 10 &&
                    (resp.contains("[") || resp.contains("\"data\""))) {
                    return path;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static List<TronclassTodo> parseTodos(String json) {
        List<TronclassTodo> result = new ArrayList<>();
        int arrayStart = json.indexOf("[");
        if (arrayStart == -1) return result;

        int depth = 0, objStart = -1;
        for (int i = arrayStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') {
                if (depth++ == 0) objStart = i;
            } else if (c == '}') {
                if (--depth == 0 && objStart != -1) {
                    String obj = json.substring(objStart, i + 1);
                    TronclassTodo item = parseSingle(obj);
                    if (item != null) result.add(item);
                    objStart = -1;
                }
            }
        }
        return result;
    }

    private static TronclassTodo parseSingle(String obj) {
        String title = first(obj, "title", "name", "subject");
        if (title == null) return null;
        for (String kw : FILTER_KEYWORDS) {
            if (title.contains(kw)) return null;
        }
        TronclassTodo item = new TronclassTodo();
        item.title    = title;
        item.type     = coalesce(first(obj, "type", "activity_type", "category"), "其他");
        item.deadline = coalesce(first(obj, "deadline", "end_time", "due_date", "end_at"), null);
        return item;
    }

    private static String first(String obj, String... keys) {
        for (String key : keys) {
            String val = extractJsonString(obj, key);
            if (val != null && !val.equals("null")) return val;
        }
        return null;
    }

    private static String coalesce(String val, String fallback) {
        return val != null ? val : fallback;
    }

    private static String extractJsonString(String obj, String key) {
        String search = "\"" + key + "\"";
        int idx = obj.indexOf(search);
        if (idx == -1) return null;
        int colon = obj.indexOf(":", idx + search.length());
        if (colon == -1) return null;
        int vs = colon + 1;
        while (vs < obj.length() && obj.charAt(vs) == ' ') vs++;
        if (vs >= obj.length()) return null;
        char fc = obj.charAt(vs);
        if (fc == '"') {
            int ve = vs + 1;
            while (ve < obj.length()) {
                char c = obj.charAt(ve);
                if (c == '"' && obj.charAt(ve - 1) != '\\') break;
                ve++;
            }
            return decodeUnicode(obj.substring(vs + 1, ve));
        }
        if (fc == 'n') return null;
        int ve = vs;
        while (ve < obj.length() && ",}\n ".indexOf(obj.charAt(ve)) == -1) ve++;
        return obj.substring(vs, ve).trim();
    }

    private static String decodeUnicode(String s) {
        if (!s.contains("\\u")) return s;
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            if (i + 5 < s.length() && s.charAt(i) == '\\' && s.charAt(i + 1) == 'u') {
                String hex = s.substring(i + 2, i + 6);
                try {
                    sb.append((char) Integer.parseInt(hex, 16));
                    i += 6;
                    continue;
                } catch (NumberFormatException ignored) {}
            }
            sb.append(s.charAt(i++));
        }
        return sb.toString();
    }

    private static String formatDeadline(String deadline) {
        if (deadline == null) return null;
        try {
            String[] parts = deadline.split("T");
            if (parts.length < 2) return deadline;
            String date = parts[0];
            String time = parts[1].replaceAll(":00Z$", "").replaceAll("Z$", "");
            if (time.length() > 5) time = time.substring(0, 5);
            return date + " " + time;
        } catch (Exception e) {
            return deadline;
        }
    }

    // ── HTTP GET ─────────────────────────────────────────────────────────────

    static String get(String urlString, String cookie) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(12_000);
        conn.setInstanceFollowRedirects(false);
        conn.setRequestProperty("User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        conn.setRequestProperty("Accept", "text/html,application/json,*/*");
        conn.setRequestProperty("X-Requested-With", "XMLHttpRequest");
        conn.setRequestProperty("Referer", BASE_URL + "/user/index");
        if (cookie != null && !cookie.isEmpty()) conn.setRequestProperty("Cookie", cookie);

        int code = conn.getResponseCode();
        if (code == 301 || code == 302) {
            String loc = conn.getHeaderField("Location");
            if (loc != null && (loc.contains("cas") || loc.contains("login")))
                throw new RuntimeException("SESSION_EXPIRED");
            return null;
        }
        if (code != 200) return null;

        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
        }
        return sb.toString();
    }

    // ── 內部 DTO ─────────────────────────────────────────────────────────────

    static class TronclassTodo {
        String title, type, deadline;
    }
}