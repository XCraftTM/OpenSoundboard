package de.xcrafttm.opensoundboard.ui;

import de.xcrafttm.opensoundboard.config.SoundboardConfig;

/** Runtime-selected component style shared by every OpenSoundboard screen. */
public final class UiStyle {

    private UiStyle() {
    }

    public static boolean useVanillaComponents() {
        return SoundboardConfig.data != null && SoundboardConfig.data.isVanillaComponents();
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
