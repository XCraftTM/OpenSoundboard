package de.xcrafttm.opensoundboard.screens;

import de.xcrafttm.opensoundboard.config.SoundboardConfig;
import de.xcrafttm.opensoundboard.tools.GuiTools;
import de.xcrafttm.opensoundboard.tools.SoundboardAudioSystem;
import de.xcrafttm.opensoundboard.ui.Icons;
import de.xcrafttm.opensoundboard.ui.Theme;
import de.xcrafttm.opensoundboard.ui.UiCanvas;
import de.xcrafttm.opensoundboard.ui.UiStyle;
import de.xcrafttm.opensoundboard.ui.widgets.ScrollList;
import net.minecraft.network.chat.Component;

import java.io.File;

/**
 * Shared drawing for sound and folder rows, used by the soundboard and the song picker so both
 * lists look and behave the same: play button, favorite star, name, and duration.
 */
final class SoundRows {

    static final int PLAY_W = 18;
    static final int STAR_W = 12;

    private SoundRows() {
    }

    static int rowHeight() {
        return UiStyle.useVanillaComponents() ? 20 : 18;
    }

    static boolean inPlayZone(double mx, int rx) {
        return mx < rx + PLAY_W;
    }

    static boolean inStarZone(double mx, int rx) {
        return mx >= rx + PLAY_W && mx < rx + PLAY_W + STAR_W;
    }

    static void drawSound(UiCanvas c, File file, int rx, int ry, int rw, int rh,
                          boolean selected, boolean hovered, boolean showStar) {
        drawSound(c, file, rx, ry, rw, rh, selected, hovered, showStar, null);
    }

    /**
     * @param folderHint folder path shown next to the name (e.g. for search results from
     *                   subfolders), or null
     */
    static void drawSound(UiCanvas c, File file, int rx, int ry, int rw, int rh,
                          boolean selected, boolean hovered, boolean showStar, String folderHint) {
        String name = file.getName();
        boolean vanilla = UiStyle.useVanillaComponents();
        boolean playing = SoundboardAudioSystem.isPlaying(name);
        boolean fav = SoundboardConfig.get(name).isFavorite();
        ScrollList.drawRowBackground(c, rx, ry, rw, rh, selected, hovered);

        boolean playHover = hovered && c.hovered(rx, ry, PLAY_W, rh);
        if (playHover) {
            if (vanilla) c.fillRect(rx + 2, ry + 2, PLAY_W - 3, rh - 4, 0x40FFFFFF);
            else c.fillRoundRect(rx + 2, ry + 2, PLAY_W - 3, rh - 4, Theme.controlHover);
        }
        int playColor;
        if (vanilla) playColor = playing ? 0xFFFFFF55 : UiStyle.VANILLA_TEXT;
        else playColor = playing ? Theme.accent : (playHover ? Theme.text : Theme.textMuted);
        Icons playIcon = playing ? Icons.STOP : Icons.PLAY;
        c.icon(playIcon, rx + 2, ry, PLAY_W - 2, rh, playColor);

        int nameX = rx + PLAY_W + 2;
        if (showStar) {
            if (fav) {
                c.icon(Icons.STAR, rx + PLAY_W, ry, STAR_W, rh, Theme.FAVORITE);
            } else if (hovered) {
                boolean starHover = c.hovered(rx + PLAY_W, ry, STAR_W, rh);
                int faint = vanilla ? 0xFF6A6A6A : Theme.textFaint;
                c.icon(Icons.STAR, rx + PLAY_W, ry, STAR_W, rh, starHover ? Theme.FAVORITE : faint);
            }
            nameX = rx + PLAY_W + STAR_W + 3;
        }

        long millis = SoundboardAudioSystem.getDurationMillis(name);
        String duration = millis > 0 ? GuiTools.formatTimeSeconds((int) (millis / 1000)) : "";
        int durationW = duration.isEmpty() ? 0 : c.textWidth(duration) + 8;
        int textY = c.centeredTextY(ry, rh);
        if (!duration.isEmpty()) {
            c.rightText(duration, rx + rw - 6, textY, vanilla ? UiStyle.VANILLA_TEXT_MUTED : Theme.textFaint);
        }

        int right = rx + rw - 6 - durationW;
        if (folderHint != null && !folderHint.isEmpty()) {
            int maxHintW = Math.max(0, (right - nameX) * 2 / 5);
            String hint = c.trimText(folderHint, maxHintW);
            int hintW = c.textWidth(hint);
            if (hintW > 0) {
                c.rightText(hint, right, textY, vanilla ? 0xFF808080 : Theme.textFaint);
                right -= hintW + 8;
            }
        }

        int nameColor;
        if (vanilla) nameColor = playing ? 0xFFFFFF55 : UiStyle.VANILLA_TEXT;
        else nameColor = playing ? Theme.accentHover : Theme.text;
        String label = c.trimText(GuiTools.baseName(file), right - nameX);
        c.text(label, nameX, textY, nameColor);
    }

    /** Folder row: folder icon, name, number of sounds inside (all depths), and a chevron. */
    static void drawFolder(UiCanvas c, String name, int soundCount, int rx, int ry, int rw, int rh, boolean hovered) {
        boolean vanilla = UiStyle.useVanillaComponents();
        ScrollList.drawRowBackground(c, rx, ry, rw, rh, false, hovered);
        c.icon(Icons.FOLDER, rx + 2, ry, PLAY_W - 2, rh, Theme.FOLDER);

        int textY = c.centeredTextY(ry, rh);
        int chevronColor = vanilla ? (hovered ? UiStyle.VANILLA_TEXT : UiStyle.VANILLA_TEXT_MUTED)
                : (hovered ? Theme.text : Theme.textFaint);
        c.icon(Icons.CHEVRON_RIGHT, rx + rw - 12, ry, 8, rh, chevronColor);

        String count = soundCount == 1
                ? Component.translatable("gui.opensoundboard.folder.count_one").getString()
                : Component.translatable("gui.opensoundboard.folder.count", soundCount).getString();
        int countRight = rx + rw - 16;
        c.rightText(count, countRight, textY, vanilla ? UiStyle.VANILLA_TEXT_MUTED : Theme.textFaint);

        int nameX = rx + PLAY_W + STAR_W + 3;
        int color = vanilla ? UiStyle.VANILLA_TEXT : Theme.text;
        c.text(c.trimText(name, countRight - c.textWidth(count) - 8 - nameX), nameX, textY, color);
    }

    /** Thin rule separating the folder group from the sounds below it. */
    static void drawDivider(UiCanvas c, int rx, int ry, int rw, int rh) {
        int color = UiStyle.useVanillaComponents() ? 0x40FFFFFF : Theme.border;
        c.hLine(rx + 6, ry + rh / 2, rw - 12, color);
    }
}
