package persistence;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * TronclassSessionStore：保存 Tronclass session，讓程式重開後可嘗試恢復登入。
 */
public class TronclassSessionStore {
    private static final String SESSION_FILE = "data/tronclass-session.properties";

    public static class SavedSession {
        private final String userName;
        private final String cookie;

        public SavedSession(String userName, String cookie) {
            this.userName = userName;
            this.cookie = cookie;
        }

        public String getUserName() { return userName; }
        public String getCookie() { return cookie; }
    }

    public SavedSession load() {
        File file = new File(SESSION_FILE);
        if (!file.exists()) return null;

        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(file)) {
            props.load(in);
            String cookie = props.getProperty("cookie", "").trim();
            if (cookie.isEmpty()) return null;
            String userName = props.getProperty("userName", "").trim();
            return new SavedSession(userName.isEmpty() ? null : userName, cookie);
        } catch (IOException e) {
            return null;
        }
    }

    public void save(String userName, String cookie) {
        if (cookie == null || cookie.isBlank()) return;
        new File("data").mkdirs();

        Properties props = new Properties();
        props.setProperty("userName", userName != null ? userName : "");
        props.setProperty("cookie", cookie);
        props.setProperty("savedAt", Long.toString(System.currentTimeMillis()));

        try (FileOutputStream out = new FileOutputStream(SESSION_FILE)) {
            props.store(out, "Local Tronclass session. Do not commit this file.");
        } catch (IOException ignored) {
            // Session persistence is a convenience feature; login should still work if saving fails.
        }
    }

    public void clear() {
        File file = new File(SESSION_FILE);
        if (file.exists()) file.delete();
    }
}
