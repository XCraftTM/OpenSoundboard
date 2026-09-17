package de.xcrafttm.opensoundboard.ui;

import de.xcrafttm.opensoundboard.config.SoundboardConfig;

import java.util.List;

/**
 * Live color palette for the modern style. Values are derived from the user's accent color,
 * surface tone, panel opacity, and corner setting, and are recomputed whenever those change.
 * Pure ARGB math with no Minecraft dependency, so it is fully version-neutral.
 */
public final class Theme {

    private Theme() {
    }

    public record Swatch(String key, int rgb) {
    }

    public record Tone(String key, int panel, int header, int surface, int surfaceHover, int control,
                       int controlHover, int border, int borderStrong, int field, int text, int textMuted,
                       int textFaint) {
    }

    public static final List<Swatch> ACCENTS = List.of(
            new Swatch("emerald", 0x5FAE6E),
            new Swatch("teal", 0x3FA9A0),
            new Swatch("sky", 0x4F97D6),
            new Swatch("lavender", 0x9A83D6),
            new Swatch("rose", 0xD4688F),
            new Swatch("coral", 0xDD6E55),
            new Swatch("amber", 0xDFA23C),
            new Swatch("snow", 0xD2D2D2)
    );

    public static final List<Tone> TONES = List.of(
            new Tone("graphite", 0x1C1C1E, 0x242426, 0x29292C, 0x323235, 0x2F2F33, 0x3A3A3F,
                    0x3A3A3F, 0x4C4C52, 0x141416, 0xE8E8E8, 0x9E9EA4, 0x6C6C72),
            new Tone("midnight", 0x161A21, 0x1C212A, 0x212732, 0x29303C, 0x262D39, 0x303846,
                    0x313A48, 0x414C5D, 0x10131A, 0xE4E9F0, 0x95A1B3, 0x5F6B7D),
            new Tone("stone", 0x1F1D1A, 0x272421, 0x2C2925, 0x35312C, 0x322E29, 0x3C3732,
                    0x403A34, 0x524B43, 0x171512, 0xECE6DC, 0xA69E91, 0x746C61)
    );

    // ---- metrics -------------------------------------------------------------------------
    public static final int PAD = 8;
    public static final int GAP = 4;
    public static final int HEADER_H = 22;

    // ---- live palette (ARGB) ------------------------------------------------------------
    public static int accent;
    public static int accentHover;
    public static int accentSoft;
    public static int onAccent;

    public static int scrim;
    public static int panel;
    public static int header;
    public static int surface;
    public static int surfaceHover;
    public static int control;
    public static int controlHover;
    public static int controlDisabled;
    public static int border;
    public static int borderStrong;
    public static int field;
    public static int text;
    public static int textMuted;
    public static int textFaint;
    public static int selection;

    public static final int FAVORITE = 0xFFE3B341;
    public static final int FOLDER = 0xFFC99A4B;
    public static final int DANGER = 0xFFD9594C;

    public static int radius;

    private static String appliedKey = null;

    static {
        sync();
    }

    /** Recompute the palette if any appearance option changed since the last call. */
    public static void sync() {
        SoundboardConfig data = SoundboardConfig.data;
        String accentHex = data != null ? data.getAccentColor() : SoundboardConfig.DEFAULT_ACCENT_COLOR;
        String toneKey = data != null ? data.getSurfaceTone() : SoundboardConfig.DEFAULT_SURFACE_TONE;
        float opacity = data != null ? data.getPanelOpacity() : SoundboardConfig.DEFAULT_PANEL_OPACITY;
        boolean rounded = data == null || data.isRoundedCorners();

        String key = accentHex + '|' + toneKey + '|' + opacity + '|' + rounded;
        if (key.equals(appliedKey)) return;
        appliedKey = key;

        int accentRgb = SoundboardConfig.parseHexColor(accentHex);
        if (accentRgb < 0) accentRgb = SoundboardConfig.parseHexColor(SoundboardConfig.DEFAULT_ACCENT_COLOR);
        Tone tone = tone(toneKey);
        int alpha = Math.round(Math.max(0.6f, Math.min(1f, opacity)) * 255);

        accent = opaque(accentRgb);
        accentHover = mix(accent, 0xFFFFFFFF, 0.14f);
        accentSoft = mix(opaque(tone.surface()), accent, 0.32f);
        onAccent = luminance(accentRgb) > 0.6f ? 0xFF161616 : 0xFFFFFFFF;

        scrim = 0x70000000;
        panel = withAlpha(tone.panel(), alpha);
        header = withAlpha(tone.header(), Math.min(255, alpha + 12));
        surface = opaque(tone.surface());
        surfaceHover = opaque(tone.surfaceHover());
        control = opaque(tone.control());
        controlHover = opaque(tone.controlHover());
        controlDisabled = mix(opaque(tone.control()), opaque(tone.panel()), 0.5f);
        border = opaque(tone.border());
        borderStrong = opaque(tone.borderStrong());
        field = opaque(tone.field());
        text = opaque(tone.text());
        textMuted = opaque(tone.textMuted());
        textFaint = opaque(tone.textFaint());
        selection = withAlpha(accentRgb, 0x70);

        radius = rounded ? 2 : 0;
    }

    public static Tone tone(String key) {
        for (Tone tone : TONES) {
            if (tone.key().equalsIgnoreCase(key)) return tone;
        }
        return TONES.get(0);
    }

    // ---- color math ---------------------------------------------------------------------

    public static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    public static int withAlpha(int rgb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
    }

    /** Linear blend of two ARGB colors; {@code t = 0} returns {@code a}, {@code t = 1} returns {@code b}. */
    public static int mix(int a, int b, float t) {
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return (Math.round(aa + (ba - aa) * t) << 24)
                | (Math.round(ar + (br - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8)
                | Math.round(ab + (bb - ab) * t);
    }

    /** Relative luminance in 0..1 (sRGB weights, no gamma — good enough to pick a text color). */
    public static float luminance(int rgb) {
        return (0.2126f * ((rgb >> 16) & 0xFF) + 0.7152f * ((rgb >> 8) & 0xFF) + 0.0722f * (rgb & 0xFF)) / 255f;
    }
}
