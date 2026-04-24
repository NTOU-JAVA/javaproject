import java.awt.Font;

/**
 * AppFonts 統一定義整個應用程式的字型規格。
 */
public class AppFonts {
    // 基礎字型（Swing 使用系統字型，在中文環境下優先用微軟正黑 / 蘋方）
    private static final String FAMILY;

    static {
        String[] preferred = {"PingFang TC", "Microsoft JhengHei", "Noto Sans TC", "SansSerif"};
        String found = "SansSerif";
        for (String f : preferred) {
            Font test = new Font(f, Font.PLAIN, 12);
            if (!test.getFamily().equals("Dialog")) { found = f; break; }
        }
        FAMILY = found;
    }

    public static final Font TITLE_LARGE  = new Font(FAMILY, Font.BOLD,   20);
    public static final Font TITLE_MEDIUM = new Font(FAMILY, Font.BOLD,   15);
    public static final Font TITLE_SMALL  = new Font(FAMILY, Font.BOLD,   13);
    public static final Font BODY_MEDIUM  = new Font(FAMILY, Font.PLAIN,  13);
    public static final Font BODY_SMALL   = new Font(FAMILY, Font.PLAIN,  12);
    public static final Font CAPTION      = new Font(FAMILY, Font.PLAIN,  11);
    public static final Font LABEL        = new Font(FAMILY, Font.BOLD,   11);
    public static final Font NAV          = new Font(FAMILY, Font.PLAIN,  13);
    public static final Font NAV_ACTIVE   = new Font(FAMILY, Font.BOLD,   13);

    private AppFonts() {}
}