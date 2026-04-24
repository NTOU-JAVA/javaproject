import java.awt.Color;

/**
 * AppColors 統一定義整個應用程式的色彩系統。.
 */
public class AppColors {
    // 背景層次
    public static final Color BG_PRIMARY   = new Color(0xFFFFFF);
    public static final Color BG_SECONDARY = new Color(0xF5F5F3);
    public static final Color BG_TERTIARY  = new Color(0xECEBE8);

    // 文字層次
    public static final Color TEXT_PRIMARY   = new Color(0x1A1917);
    public static final Color TEXT_SECONDARY = new Color(0x6B6A67);
    public static final Color TEXT_TERTIARY  = new Color(0xA8A7A4);

    // 邊框
    public static final Color BORDER_DEFAULT = new Color(0xE2E1DD);
    public static final Color BORDER_HOVER   = new Color(0xC8C7C3);

    // 強調色 — 靛藍
    public static final Color ACCENT         = new Color(0x3B5BDB);
    public static final Color ACCENT_LIGHT   = new Color(0xEEF2FF);
    public static final Color ACCENT_TEXT    = new Color(0x3B5BDB);

    // 危險／重要
    public static final Color DANGER         = new Color(0xC92A2A);
    public static final Color DANGER_LIGHT   = new Color(0xFFF5F5);
    public static final Color DANGER_BORDER  = new Color(0xFFC9C9);

    // 成功
    public static final Color SUCCESS        = new Color(0x2F9E44);
    public static final Color SUCCESS_LIGHT  = new Color(0xEBFBEE);

    // 警告
    public static final Color WARNING        = new Color(0xE67700);
    public static final Color WARNING_LIGHT  = new Color(0xFFF9DB);

    // 今天的高亮
    public static final Color TODAY_BG       = new Color(0xEEF2FF);
    public static final Color TODAY_BORDER   = new Color(0x3B5BDB);
    public static final Color TODAY_TEXT     = new Color(0x3B5BDB);

    // Sidebar
    public static final Color SIDEBAR_BG     = new Color(0xFAF9F7);
    public static final Color NAV_ACTIVE_BG  = new Color(0xECEBE8);
    public static final Color NAV_HOVER_BG   = new Color(0xF0EFEC);

    // Topbar
    public static final Color TOPBAR_BG      = new Color(0xFFFFFF);

    private AppColors() {}
}