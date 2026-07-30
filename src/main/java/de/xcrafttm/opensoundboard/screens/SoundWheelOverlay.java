package de.xcrafttm.opensoundboard.screens;

import de.xcrafttm.opensoundboard.OpenSoundboardClient;
import de.xcrafttm.opensoundboard.config.SoundboardConfig;
import de.xcrafttm.opensoundboard.config.WheelLayoutConfig;
import de.xcrafttm.opensoundboard.tools.GuiTools;
import de.xcrafttm.opensoundboard.tools.McCompat;
import de.xcrafttm.opensoundboard.tools.SoundboardAudioSystem;
import de.xcrafttm.opensoundboard.tools.WheelLayout;
import de.xcrafttm.opensoundboard.ui.OsbScreen;
import de.xcrafttm.opensoundboard.ui.Theme;
import de.xcrafttm.opensoundboard.ui.UiCanvas;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * In-world radial overlay opened while the wheel key is held. Scroll to change page; release the
 * key to play the hovered sound (folders/back navigate instead). Fully custom-drawn.
 */
public class SoundWheelOverlay extends OsbScreen {

    private static final int BUTTON_W = 160;
    private static final int BUTTON_H = 20;
    private static final int CENTER_W = 120;
    private static final String BACK = "__BACK__";

    private static int page = 0;
    private static File currentFolder = null;

    private record Slot(int x, int y, int w, int h, File file, boolean isBack, boolean isFolder) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private List<File> allSounds = new ArrayList<>();
    private final List<Slot> slots = new ArrayList<>();
    private Slot hovered;

    public SoundWheelOverlay() {
        super(Component.translatable("key.opensoundboard.wheel"));
    }

    /** Reset folder/page state (call when intentionally leaving the wheel). */
    public static void resetState() {
        currentFolder = null;
        page = 0;
    }

    @Override
    protected void buildUi() {
        if (currentFolder == null) {
            currentFolder = SoundboardConfig.resolveLastOpenedFolder(OpenSoundboardClient.soundDir);
        }
        loadSounds();
        rebuild();
    }

    // ---------------------------------------------------------------- data

    private void loadSounds() {
        File soundDir = OpenSoundboardClient.soundDir;
        allSounds = new ArrayList<>();
        if (SoundboardConfig.data.isWheelCustomLayout()) {
            int total = perPage();
            WheelLayoutConfig.resize(total);
            for (int i = 0; i < total; i++) {
                String nm = WheelLayoutConfig.get(i);
                File f = (nm != null && !nm.isBlank()) ? new File(soundDir, nm) : null;
                allSounds.add(f != null && f.exists() ? f : null);
            }
            return;
        }
        if (currentFolder != null) {
            allSounds.add(new File(soundDir, BACK));
            addSorted(currentFolder.listFiles((d, n) -> n.endsWith(".mp3")));
        } else {
            if (SoundboardConfig.data.isShowSubfolders()) {
                File[] subs = soundDir.listFiles(File::isDirectory);
                if (subs != null) {
                    Arrays.sort(subs, Comparator.comparing(File::getName));
                    for (File s : subs) {
                        File[] m = s.listFiles((d, n) -> n.endsWith(".mp3"));
                        if (m != null && m.length > 0) allSounds.add(s);
                    }
                }
            }
            addSorted(soundDir.listFiles((d, n) -> n.endsWith(".mp3")));
        }
    }

    private void addSorted(File[] files) {
        if (files == null) return;
        Arrays.stream(files)
                .filter(f -> !SoundboardConfig.data.isWheelFavoritesOnly() || SoundboardConfig.get(f.getName()).isFavorite())
                .sorted(Comparator.comparing((File f) -> SoundboardConfig.get(f.getName()).isFavorite()).reversed()
                        .thenComparing(File::getName))
                .forEach(allSounds::add);
    }

    private int perPage() {
        return Math.max(1, SoundboardConfig.data.getWheelSoundsPerPage());
    }

    private int totalPages() {
        if (SoundboardConfig.data.isWheelCustomLayout() || allSounds.isEmpty()) return 1;
        return (int) Math.ceil((double) allSounds.size() / perPage());
    }

    private List<File> currentPageSounds() {
        if (SoundboardConfig.data.isWheelCustomLayout()) return allSounds;
        int start = page * perPage();
        int end = Math.min(start + perPage(), allSounds.size());
        if (start >= allSounds.size()) return List.of();
        return allSounds.subList(start, end);
    }

    private void rebuild() {
        slots.clear();
        List<File> pageSounds = currentPageSounds();
        int num = perPage();
        int cx = this.width / 2;
        int cy = this.height / 2;
        for (int i = 0; i < num; i++) {
            int[] pos = WheelLayout.buttonPos(i, num, cx, cy, BUTTON_W, BUTTON_H, CENTER_W, this.width, this.height);
            File f = i < pageSounds.size() ? pageSounds.get(i) : null;
            if (f == null) {
                slots.add(null);
                continue;
            }
            slots.add(new Slot(pos[0], pos[1], BUTTON_W, BUTTON_H, f, f.getName().equals(BACK), f.isDirectory()));
        }
    }

    // ---------------------------------------------------------------- render

    @Override
    protected void renderContent(UiCanvas c) {
        c.fillRect(0, 0, this.width, this.height, 0x66000000);
        int cx = this.width / 2;
        int cy = this.height / 2;

        hovered = null;
        for (Slot s : slots) {
            if (s != null && s.contains(c.mouseX, c.mouseY)) hovered = s;
        }

        if (currentFolder != null) {
            c.centeredText(Component.literal(currentFolder.getName()), cx, cy - BUTTON_H / 2 - 14, 0xFFF0C044);
        }

        c.fillRoundRect(cx - CENTER_W / 2, cy - BUTTON_H / 2, CENTER_W, BUTTON_H, Theme.PANEL);
        c.roundBorder(cx - CENTER_W / 2, cy - BUTTON_H / 2, CENTER_W, BUTTON_H, Theme.BORDER);
        String center = allSounds.isEmpty()
                ? Component.translatable("gui.opensoundboard.wheel.empty").getString()
                : Component.translatable("gui.opensoundboard.wheel.page", String.valueOf(page + 1), String.valueOf(totalPages())).getString();
        c.centeredText(Component.literal(center), cx, cy - 4, Theme.TEXT_MUTED);

        for (Slot s : slots) {
            if (s != null) drawSlot(c, s, s == hovered);
        }
    }

    private void drawSlot(UiCanvas c, Slot s, boolean hov) {
        int bg;
        int color;
        String text;
        if (s.isBack) {
            bg = hov ? Theme.BTN_HOVER : Theme.BTN;
            text = "← " + (currentFolder != null ? currentFolder.getName() : "");
            color = Theme.TEXT_MUTED;
        } else if (s.isFolder) {
            bg = hov ? Theme.BTN_HOVER : Theme.BTN;
            text = "📁 " + s.file.getName();
            color = 0xFFF0C044;
        } else {
            boolean fav = SoundboardConfig.get(s.file.getName()).isFavorite();
            boolean playing = SoundboardAudioSystem.isPlaying(s.file.getName());
            bg = hov ? Theme.ACCENT_HOVER : (playing ? 0xFF2E7D32 : Theme.BTN);
            text = (fav ? "★ " : "") + GuiTools.baseName(s.file) + (playing ? " ▶" : "");
            color = fav ? 0xFFF5D76A : (playing ? 0xFFB6E3B8 : Theme.TEXT);
        }
        c.fillRoundRect(s.x, s.y, s.w, s.h, bg);
        c.roundBorder(s.x, s.y, s.w, s.h, hov ? Theme.ACCENT : Theme.BORDER);
        c.centeredText(Component.literal(GuiTools.trimName(this.font, text, s.w - 8)), s.x + s.w / 2, s.y + (s.h - 8) / 2, color);
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        int total = totalPages();
        if (total <= 1) return true;
        page = vertical > 0 ? (page - 1 + total) % total : (page + 1) % total;
        rebuild();
        return true;
    }

    @Override
    protected boolean screenKeyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return false;
    }

    /** Click a folder/back slot while holding to navigate (stays open). */
    @Override
    protected boolean screenMouseClicked(double mx, double my, int button) {
        if (button != 0 || hovered == null) return false;
        if (hovered.isBack) {
            currentFolder = null;
            SoundboardConfig.saveLastOpenedFolder(null);
            page = 0;
            loadSounds();
            rebuild();
            return true;
        }
        if (hovered.isFolder) {
            currentFolder = hovered.file;
            SoundboardConfig.saveLastOpenedFolder(hovered.file);
            page = 0;
            loadSounds();
            rebuild();
            return true;
        }
        return false;
    }

    /** Called by the client when the wheel key is released: play the hovered sound, then close. */
    public void playHoveredAndClose() {
        if (hovered != null && !hovered.isBack && !hovered.isFolder) {
            playSound(hovered.file);
        }
        close();
    }

    private void playSound(File file) {
        String name = file.getName();
        var data = SoundboardConfig.get(name);
        boolean playing = SoundboardAudioSystem.isPlaying(name);
        switch (SoundboardConfig.data.getKeybindMode()) {
            case "pause_resume" -> {
                if (playing) {
                    if (SoundboardAudioSystem.isPaused(name)) SoundboardAudioSystem.resume(name);
                    else SoundboardAudioSystem.pause(name);
                } else {
                    SoundboardAudioSystem.playFile(file, data.getLocalVolume(), data.getPlayerVolume());
                }
            }
            case "play_restart" -> {
                if (playing) SoundboardAudioSystem.stop(name);
                SoundboardAudioSystem.playFile(file, data.getLocalVolume(), data.getPlayerVolume());
            }
            default -> {
                if (playing) SoundboardAudioSystem.stop(name);
                else SoundboardAudioSystem.playFile(file, data.getLocalVolume(), data.getPlayerVolume());
            }
        }
    }

    private void close() {
        McCompat.setScreen(this.minecraft, null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
