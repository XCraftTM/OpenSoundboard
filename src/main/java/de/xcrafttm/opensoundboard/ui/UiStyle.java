package de.xcrafttm.opensoundboard.ui;

import de.xcrafttm.opensoundboard.config.SoundboardConfig;

/** Runtime-selected component style shared by every OpenSoundboard screen. */
public final class UiStyle {

    /** Vanilla text colors, matching Minecraft's own buttons and labels. */
    public static final int VANILLA_TEXT = 0xFFFFFFFF;
    public static final int VANILLA_TEXT_DISABLED = 0xFFA0A0A0;
    public static final int VANILLA_TEXT_MUTED = 0xFFA0A0A0;
    public static final int VANILLA_HINT = 0xFF808080;
    public static final int VANILLA_HEADER_H = 33;
    public static final int VANILLA_FOOTER_H = 33;

    private UiStyle() {
    }

    public static boolean useVanillaComponents() {
        return SoundboardConfig.data != null && SoundboardConfig.data.isVanillaComponents();
    }

    /** Standard height of buttons, sliders, and text fields in the active style. */
    public static int controlHeight() {
        return useVanillaComponents() ? 20 : 18;
    }

    public static float uiWidthScale() {
        return SoundboardConfig.data == null
                ? SoundboardConfig.DEFAULT_UI_WIDTH_SCALE
                : SoundboardConfig.data.getUiWidthScale();
    }

    public static float uiHeightScale() {
        return SoundboardConfig.data == null
                ? SoundboardConfig.DEFAULT_UI_HEIGHT_SCALE
                : SoundboardConfig.data.getUiHeightScale();
    }

    public static float fontScale() {
        return SoundboardConfig.data == null
                ? SoundboardConfig.DEFAULT_FONT_SCALE
                : SoundboardConfig.data.getFontScale();
    }
}
