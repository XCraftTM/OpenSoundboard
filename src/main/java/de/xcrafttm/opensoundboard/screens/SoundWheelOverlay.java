package de.xcrafttm.opensoundboard.screens;

import de.xcrafttm.opensoundboard.OpenSoundboardClient;
import de.xcrafttm.opensoundboard.config.SoundboardConfig;
import de.xcrafttm.opensoundboard.config.WheelLayoutConfig;
import de.xcrafttm.opensoundboard.tools.GuiTools;
import de.xcrafttm.opensoundboard.tools.Keys;
import de.xcrafttm.opensoundboard.tools.McCompat;
import de.xcrafttm.opensoundboard.tools.SoundLibrary;
import de.xcrafttm.opensoundboard.tools.SoundboardAudioSystem;
import de.xcrafttm.opensoundboard.ui.Icons;
import de.xcrafttm.opensoundboard.ui.OsbScreen;
import de.xcrafttm.opensoundboard.ui.Theme;
import de.xcrafttm.opensoundboard.ui.UiCanvas;
import de.xcrafttm.opensoundboard.ui.UiStyle;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * In-world pie menu opened while the wheel key is held. Point in a direction to pick a slice,
 * scroll to change page, and release the key to play it (click folders/back to navigate).
 */
public class SoundWheelOverlay extends OsbScreen {

    private static final String BACK = "__BACK__";

    private static int page = 0;
    private static File currentFolder = null;

    private List<File> allSounds = new ArrayList<>();
    /** Entries on the current page, one per slice (null = empty slice). */
    private final List<File> slots = new ArrayList<>();
    private PieWheel wheel;
    private int hovered = -1;

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
        allSounds = new ArrayList<>();
        if (SoundboardConfig.data.isWheelCustomLayout()) {
            int total = perPage();
            WheelLayoutConfig.resize(total);
            for (int i = 0; i < total; i++) {
                allSounds.add(SoundLibrary.find(WheelLayoutConfig.get(i)));
            }
            return;
        }
        if (!SoundboardConfig.data.isShowSubfolders() || (currentFolder != null && !SoundLibrary.isInside(currentFolder))) {
            currentFolder = null;
        }
        File dir = currentFolder != null ? currentFolder : SoundLibrary.root();
        if (currentFolder != null) allSounds.add(new File(dir, BACK));
        if (SoundboardConfig.data.isShowSubfolders()) allSounds.addAll(SoundLibrary.folders(dir));
        SoundLibrary.sounds(dir).stream()
                .filter(f -> !SoundboardConfig.data.isWheelFavoritesOnly() || SoundboardConfig.get(f.getName()).isFavorite())
                .sorted(Comparator.comparing((File f) -> SoundboardConfig.get(f.getName()).isFavorite()).reversed()
                        .thenComparing(f -> f.getName().toLowerCase()))
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
        for (int i = 0; i < num; i++) slots.add(i < pageSounds.size() ? pageSounds.get(i) : null);
        if (wheel == null || wheel.count != num || wheel.cx != this.width / 2 || wheel.cy != this.height / 2) {
            wheel = PieWheel.fit(this.width, this.height, num, 0, 0);
        }
    }

    private static boolean isBack(File file) {
        return file != null && file.getName().equals(BACK);
    }

    // ---------------------------------------------------------------- render

    @Override
    protected void renderContent(UiCanvas c) {
        if (!vanilla()) c.fillRect(0, 0, this.width, this.height, 0x50000000);
        if (wheel == null) rebuild();

        int index = wheel.sliceAt(c.mouseX, c.mouseY);
        hovered = index >= 0 && index < slots.size() && slots.get(index) != null ? index : -1;

        List<PieWheel.Slice> slices = new ArrayList<>();
        for (File file : slots) slices.add(slice(file));

        String title;
        if (hovered >= 0) {
            title = slices.get(hovered).label();
        } else if (currentFolder != null) {
            title = currentFolder.getName();
        } else {
            title = Component.translatable(allSounds.isEmpty() ? "gui.opensoundboard.wheel.empty" : "gui.opensoundboard.library_root").getString();
        }
        String subtitle = totalPages() > 1
                ? Component.translatable("gui.opensoundboard.wheel.page", String.valueOf(page + 1), String.valueOf(totalPages())).getString()
                : null;
        wheel.render(c, slices, hovered, title, subtitle);
    }

    private PieWheel.Slice slice(File file) {
        if (file == null) return PieWheel.Slice.empty("");
        if (isBack(file)) {
            File parent = currentFolder != null ? currentFolder.getParentFile() : null;
            String text = SoundLibrary.isInside(parent)
                    ? parent.getName()
                    : Component.translatable("gui.opensoundboard.library_root").getString();
            int color = vanilla() ? UiStyle.VANILLA_TEXT_MUTED : Theme.textMuted;
            return new PieWheel.Slice(Icons.BACK, color, text, false, false);
        }
        if (file.isDirectory()) {
            return new PieWheel.Slice(Icons.FOLDER, Theme.FOLDER, file.getName(), false, false);
        }
        boolean fav = SoundboardConfig.get(file.getName()).isFavorite();
        boolean playing = SoundboardAudioSystem.isPlaying(file.getName());
        Icons icon = playing ? Icons.NOTE : (fav ? Icons.STAR : null);
        int iconColor = playing ? (vanilla() ? 0xFFFFFF55 : Theme.accent) : Theme.FAVORITE;
        return new PieWheel.Slice(icon, iconColor, GuiTools.baseName(file), false, playing);
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
        if (key == Keys.ESCAPE) {
            close();
            return true;
        }
        return false;
    }

    /** Click a folder/back slot while holding to navigate (stays open). */
    @Override
    protected boolean screenMouseClicked(double mx, double my, int button) {
        if (button != 0 || hovered < 0) return false;
        File target = slots.get(hovered);
        if (isBack(target)) {
            File parent = currentFolder != null ? currentFolder.getParentFile() : null;
            currentFolder = SoundLibrary.isInside(parent) ? parent : null;
            SoundboardConfig.saveLastOpenedFolder(currentFolder == null ? null : SoundLibrary.relativePath(currentFolder));
            page = 0;
            loadSounds();
            rebuild();
            return true;
        }
        if (target.isDirectory()) {
            currentFolder = target;
            SoundboardConfig.saveLastOpenedFolder(SoundLibrary.relativePath(target));
            page = 0;
            loadSounds();
            rebuild();
            return true;
        }
        return false;
    }

    /** Called by the client when the wheel key is released: play the hovered sound, then close. */
    public void playHoveredAndClose() {
        File target = hovered >= 0 && hovered < slots.size() ? slots.get(hovered) : null;
        if (target != null && !isBack(target) && !target.isDirectory()) {
            playSound(target);
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
}
