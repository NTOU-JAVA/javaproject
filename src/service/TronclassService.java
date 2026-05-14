package service;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import model.TodoItem;

/**
 * TronclassService：整合登入 Cookie 後的資料同步。
 *  - parseName()       從首頁 HTML 解析使用者姓名
 *  - syncTodos()       抓取待辦事項並合併寫入 todos.xml
 *  - validateCookie()  主動驗證 cookie 是否仍有效
 *
 * v0.6 變更：
 *  - 所有 HTTP 呼叫遇到 redirect 到登入頁時，統一拋出 SessionExpiredException
 *  - 新增 SessionExpiredException（受檢例外，強制呼叫端處理）
 *  - 新增 validateCookie()，供背景定時器使用
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

    // ── 自訂例外 ─────────────────────────────────────────────────────────────

    /** Cookie 已失效或被登出時拋出。 */
    public static class SessionExpiredException extends Exception {
        public SessionExpiredException() { super("SESSION_EXPIRED"); }
        public SessionExpiredException(String msg) { super(msg); }
    }

    // ── 公開 API ─────────────────────────────────────────────────────────────

    /**
     * 從首頁 HTML 解析登入後的使用者姓名。
     *
     * @param cookie 已登入的 Cookie 字串
     * @return 使用者姓名；null 表示解析失敗但 cookie 仍有效
     * @throws SessionExpiredException cookie 已失效
     * @throws Exception               網路或其他錯誤
     */
    public static String parseName(String cookie) throws SessionExpiredException, Exception {
        String html = get(BASE_URL + "/user/index", cookie);
        if (html == null || html.length() < 100) return null;

        Pattern p1 = Pattern.compile("userCurrentName\\s*=\\s*'([^']+)'");
        Matcher m1 = p1.matcher(html);
        if (m1.find()) return m1.group(1).trim();

        Pattern p2 = Pattern.compile("\"userCurrentName\"\\s*:\\s*\"([^\"]+)\"");
        Matcher m2 = p2.matcher(html);
        if (m2.find()) return m2.group(1).trim();

        Pattern p3 = Pattern.compile("id=[\"']userCurrentName[\"'][^>]*>([^<]+)<");
        Matcher m3 = p3.matcher(html);
        if (m3.find()) return m3.group(1).trim();

        Pattern p4 = Pattern.compile("ng-bind=[\"']currentUserName[\"'][^>]*>([^<]+)<");
        Matcher m4 = p4.matcher(html);
        if (m4.find()) return m4.group(1).trim();

        Pattern p5 = Pattern.compile("data-name=[\"']([^\"']+)[\"']");
        Matcher m5 = p5.matcher(html);
        if (m5.find()) return m5.group(1).trim();

        return null;
    }

    /**
     * 主動驗證 cookie 是否仍有效（向首頁發送輕量請求）。
     *
     * @param cookie 要驗證的 Cookie 字串
     * @return true = 有效；false = 失效
     */
    public static boolean validateCookie(String cookie) {
        if (cookie == null || cookie.isEmpty()) return false;
        try {
            get(BASE_URL + "/user/index", cookie);
            return true;
        } catch (SessionExpiredException e) {
            return false;
        } catch (Exception e) {
            // 網路錯誤：不確定，維持已登入狀態
            return true;
        }
    }

    /**
     * 從 Tronclass 取得待辦事項，並合併寫入 data/todos.xml。
     *
     * @param cookie       已登入的 Cookie 字串
     * @param currentTodos 現有待辦事項清單（會直接修改）
     * @param saveCallback 儲存回調
     * @return 新增的待辦事項數量；-1 表示找不到 API
     * @throws SessionExpiredException cookie 已失效
     * @throws Exception               網路或其他錯誤
     */
    public static int syncTodos(String cookie, java.util.List<TodoItem> currentTodos,
                                 Runnable saveCallback) throws SessionExpiredException, Exception {
        String endpoint = findWorkingEndpoint(cookie);
        if (endpoint == null) return -1;

        String json = get(BASE_URL + endpoint, cookie);
        if (json == null) return -1;

        List<TronclassTodo> fetched = parseTodos(json);

        Set<String> existingTitles = new HashSet<>();
        int maxId = 0;
        for (TodoItem t : currentTodos) {
            existingTitles.add(t.getTitle());
            maxId = Math.max(maxId, t.getId());
        }

        int added = 0;
        for (TronclassTodo tt : fetched) {
            if (existingTitles.contains(tt.title)) continue;
            maxId++;
            TodoItem item = new TodoItem(maxId, tt.title, "", formatDeadline(tt.deadline));
            currentTodos.add(item);
            added++;
        }

        if (added > 0 && saveCallback != null) saveCallback.run();
        return added;
    }

    // ── 內部邏輯 ─────────────────────────────────────────────────────────────

    private static String findWorkingEndpoint(String cookie) throws SessionExpiredException {
        for (String path : TODO_CANDIDATES) {
            try {
                String resp = get(BASE_URL + path, cookie);
                if (resp != null && resp.length() > 10 &&
                    (resp.contains("[") || resp.contains("\"data\""))) {
                    return path;
                }
            } catch (SessionExpiredException e) {
                throw e; // 直接向上傳遞
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

    /**
     * 執行 HTTP GET，遇到 redirect 到登入頁時拋出 SessionExpiredException。
     */
    static String get(String urlString, String cookie) throws SessionExpiredException, Exception {
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
            if (loc != null && (loc.contains("cas") || loc.contains("login"))) {
                throw new SessionExpiredException();
            }
            return null;
        }
        if (code == 401 || code == 403) {
            throw new SessionExpiredException("HTTP " + code);
        }
        if (code != 200) return null;

        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
        }

        // 某些平台直接回 200 但 HTML 已是登入頁
        String body = sb.toString();
        if (body.contains("cas.ntou.edu.tw") || body.contains("/user/login")) {
            throw new SessionExpiredException("Redirected to login page");
        }
        return body;
    }

    // ── 內部 DTO ─────────────────────────────────────────────────────────────

    static class TronclassTodo {
        String title, type, deadline;
    }
}
