package de.xcrafttm.opensoundboard.ui;

/**
 * Central color + metric palette for the custom UI. Sleek dark, flat, deep-indigo accent.
 * Pure constants (ARGB ints) — no Minecraft dependency, so it is fully version-neutral.
 */
public final class Theme {

    private Theme() {
    }

    // Accent (deep indigo)
    public static final int ACCENT        = 0xFF4F46E5;
    public static final int ACCENT_HOVER  = 0xFF6366F1;
    public static final int ACCENT_MUTED  = 0x334F46E5;

    // Surfaces
    public static final int SCRIM         = 0x99000000; // dim behind panels
    public static final int PANEL         = 0xF014141A; // near-black translucent
    public static final int PANEL_RAISED  = 0xFF1C1C24;
    public static final int ROW           = 0x14FFFFFF;
    public static final int ROW_HOVER      = 0x24FFFFFF;

    // Borders
    public static final int BORDER        = 0xFF2C2C38;
    public static final int BORDER_STRONG  = 0xFF3C3C4A;

    // Text
    public static final int TEXT          = 0xFFECECF1;
    public static final int TEXT_MUTED    = 0xFF9A9AA6;
    public static final int TEXT_ON_ACCENT = 0xFFFFFFFF;

    // Text selection highlight (semi-transparent indigo)
    public static final int SELECTION     = 0x804F46E5;
    public static final int FIELD_BG      = 0xFF0F0F14;

    // Metrics
    public static final int PAD           = 8;
    public static final int GAP           = 6;
    public static final int ROW_H         = 22;
    public static final int RADIUS        = 2; // very slight rounding
}
