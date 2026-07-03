package de.xcrafttm.opensoundboard.screens;

import de.xcrafttm.opensoundboard.OpenSoundboardClient;
import de.xcrafttm.opensoundboard.config.SoundboardConfig;
import de.xcrafttm.opensoundboard.tools.GuiTools;
import de.xcrafttm.opensoundboard.tools.McCompat;
import de.xcrafttm.opensoundboard.tools.SoundboardAudioSystem;
import de.xcrafttm.opensoundboard.tools.YtDlpManager;
import de.xcrafttm.opensoundboard.ui.OsbScreen;
import de.xcrafttm.opensoundboard.ui.Theme;
import de.xcrafttm.opensoundboard.ui.UiCanvas;
import de.xcrafttm.opensoundboard.ui.Widget;
import de.xcrafttm.opensoundboard.ui.widgets.Button;
import de.xcrafttm.opensoundboard.ui.widgets.ScrollList;
import de.xcrafttm.opensoundboard.ui.widgets.Slider;
import de.xcrafttm.opensoundboard.ui.widgets.TextField;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Main soundboard screen: search, toolbar (refresh / folder / config / youtube / sort), a
 * scrollable folder+sound list, and a details pane for the selected sound (volume, keybind,
 * timeline, transport, set-start). Reproduces the behaviour of the old owo screen on the
 * custom flat-indigo toolkit.
 */
public class SoundboardScreen extends OsbScreen {

    private static final int STAR_W = 14;
    private static final int PLAY_W = 40;
    private static final int ROW_H = 18;

    private static final String BACK = "__BACK__";

    private static File currentFolder = null;

    private int px;
    private int py;
    private int pw;
    private int ph;
    private int cx;
    private int cw;

    private TextField search;
    private ScrollList list;
    private Button sortModeBtn;
    private Button sortDirBtn;

    // details
    private Slider localSlider;
    private Slider playerSlider;
    private Button bindBtn;
    private Slider timeline;
    private TextField timeField;
    private Button pauseBtn;
    private int detailsTop;

    private final List<Widget> details = new ArrayList<>();
    private File selected = null;
    private boolean binding = false;

    private final List<File> results = new ArrayList<>();

    public SoundboardScreen() {
        super(Component.translatable("gui.opensoundboard.title"));
    }

    @Override
    protected void buildUi() {
        ph = (int) (this.height * 0.9);
        pw = Math.max(420, Math.min(640, (int) (this.width * 0.7)));
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;
        cx = px + Theme.PAD;
        cw = pw - Theme.PAD * 2;

        if (currentFolder == null) {
            currentFolder = SoundboardConfig.resolveLastOpenedFolder(OpenSoundboardClient.soundDir);
        }

        int y = py + 26;
        search = add(new TextField().placeholder("Search sounds...").onChange(s -> scanSounds()));
        search.bounds(cx, y, cw, 18);
        y += 24;

        // Toolbar
        int n = 6;
        int gap = 4;
        int bw = (cw - gap * (n - 1)) / n;
        add(new Button(Component.translatable("gui.opensoundboard.refresh"), b -> refresh()).secondary())
                .bounds(cx, y, bw, 18);
        add(new Button(Component.translatable("gui.opensoundboard.folder"),
                b -> McCompat.openFolder(OpenSoundboardClient.soundDir)).secondary())
                .bounds(cx + (bw + gap), y, bw, 18);
        add(new Button(Component.translatable("gui.opensoundboard.config"),
                b -> this.minecraft.setScreen(new SoundboardConfigScreen(this))).secondary())
                .bounds(cx + (bw + gap) * 2, y, bw, 18);
        add(new Button(Component.translatable("gui.opensoundboard.youtube"),
                b -> this.minecraft.setScreen(new YouTubeScreen(this))).secondary())
                .bounds(cx + (bw + gap) * 3, y, bw, 18);
        sortModeBtn = add(new Button(sortModeLabel(), b -> cycleSortMode()).secondary());
        sortModeBtn.bounds(cx + (bw + gap) * 4, y, bw, 18);
        sortDirBtn = add(new Button(sortDirLabel(), b -> toggleSortDir()).secondary());
        sortDirBtn.bounds(cx + (bw + gap) * 5, y, bw, 18);
        y += 24;

        // Details block occupies a fixed strip at the bottom of the panel.
        int detailsH = 82;
        detailsTop = py + ph - Theme.PAD - 22 - 6 - detailsH;
        int listBottom = detailsTop - 14;

        list = add(new ScrollList().gap(2));
        list.bounds(cx, y, cw, listBottom - y);

        buildDetails();

        add(new Button(Component.translatable("gui.done"), b -> onClose()))
                .bounds(px + (pw - 160) / 2, py + ph - Theme.PAD - 22, 160, 22);

        scanSounds();
        File active = SoundboardAudioSystem.getActiveSoundFile();
        if (active != null && active.exists()) select(active);
        else refreshDetails();
    }

    private <T extends Widget> T detail(T w) {
        add(w);
        details.add(w);
        return w;
    }

    private void buildDetails() {
        details.clear();
        int y = detailsTop + 2;
        boolean sync = SoundboardConfig.data.isSyncAudio();
        int bindW = 88;
        int volW = cw - bindW - 6;

        localSlider = detail(new Slider(0, v -> onVolume(v, true)).readout(v -> Component.translatable(
                sync ? "gui.opensoundboard.sync_volume" : "gui.opensoundboard.local_volume",
                String.valueOf(pct(v)))));
        playerSlider = detail(new Slider(0, v -> onVolume(v, false))
                .readout(v -> Component.translatable("gui.opensoundboard.player_volume", String.valueOf(pct(v)))));
        if (sync) {
            localSlider.bounds(cx, y, volW, 18);
            playerSlider.bounds(cx, y, volW, 18);
            playerSlider.visible = false;
        } else {
            int half = (volW - 6) / 2;
            localSlider.bounds(cx, y, half, 18);
            playerSlider.bounds(cx + half + 6, y, half, 18);
        }

        bindBtn = detail(new Button(GuiTools.keyBindLabel(null), b -> startBinding()).secondary());
        bindBtn.bounds(cx + cw - bindW, y, bindW, 18);
        y += 24;

        timeline = detail(new Slider(0, v -> {
            if (selected != null && SoundboardAudioSystem.isPlaying(selected.getName()))
                SoundboardAudioSystem.setCursor(selected.getName(), v.floatValue());
        }).readout(v -> timelineLabel()));
        timeline.bounds(cx, y, cw, 18);
        y += 24;

        timeField = detail(new TextField().maxLength(10).onChange(t -> {
            if (selected == null || !timeField.isFocused()) return;
            if (!SoundboardAudioSystem.isPlaying(selected.getName())) return;
            long ms = GuiTools.parseTimeMillis(t);
            long dur = SoundboardAudioSystem.getDurationMillis(selected.getName());
            if (ms >= 0 && dur > 0) SoundboardAudioSystem.setCursor(selected.getName(), Math.max(0f, Math.min(1f, ms / (float) dur)));
        }));
        timeField.bounds(cx, y, 70, 18);

        int tb = cx + 76;
        int tbw = 20;
        detail(new Button(Component.literal("⏹"), b -> SoundboardAudioSystem.stopAll()).secondary()).bounds(tb, y, tbw, 18);
        detail(new Button(Component.literal("⏪"), b -> skip(-1)).secondary()).bounds(tb + 24, y, tbw, 18);
        pauseBtn = detail(new Button(Component.literal("⏸"), b -> togglePause()).secondary());
        pauseBtn.bounds(tb + 48, y, tbw, 18);
        detail(new Button(Component.literal("⏩"), b -> skip(1)).secondary()).bounds(tb + 72, y, tbw, 18);
        detail(new Button(Component.literal("🔁"), b -> toggleLoop()).secondary()).bounds(tb + 96, y, tbw, 18);
        detail(new Button(Component.translatable("gui.opensoundboard.set_start"), b -> setStart()).secondary())
                .bounds(cx + cw - 70, y, 70, 18);
    }

    // ---------------------------------------------------------------- scan / list

    private void scanSounds() {
        String query = search == null ? "" : search.getText().trim().toLowerCase();
        results.clear();
        list.clearRows();

        File dir = currentFolder != null ? currentFolder : OpenSoundboardClient.soundDir;
        if (currentFolder != null) {
            list.addRow(navRow("← " + currentFolder.getName(), 0xFF9A9AA6, () -> {
                currentFolder = null;
                SoundboardConfig.saveLastOpenedFolder(null);
                scanSounds();
            }));
        } else if (SoundboardConfig.data.isShowSubfolders()) {
            File[] subs = OpenSoundboardClient.soundDir.listFiles(File::isDirectory);
            if (subs != null) {
                Arrays.sort(subs, Comparator.comparing(File::getName));
                for (File sub : subs) {
                    File[] mp3s = sub.listFiles((d, nm) -> nm.endsWith(".mp3"));
                    if (mp3s == null || mp3s.length == 0) continue;
                    final File folder = sub;
                    list.addRow(navRow("📁 " + sub.getName(), 0xFFF0C044, () -> {
                        currentFolder = folder;
                        SoundboardConfig.saveLastOpenedFolder(folder);
                        scanSounds();
                    }));
                }
            }
        }

        File[] files = dir.listFiles((d, nm) -> nm.endsWith(".mp3"));
        if (files == null) files = new File[0];
        List<File> sorted = Arrays.stream(files)
                .filter(f -> f.getName().toLowerCase().contains(query))
                .sorted(comparator())
                .collect(Collectors.toList());
        results.addAll(sorted);
        for (File f : sorted) list.addRow(soundRow(f));

        SoundboardAudioSystem.scanDurations();
    }

    private ScrollList.Row navRow(String label, int color, Runnable onClick) {
        return new ScrollList.Row() {
            public int height() {
                return ROW_H;
            }

            public void draw(UiCanvas c, int rx, int ry, int rw, boolean hovered) {
                if (hovered) c.fillRoundRect(rx, ry, rw, ROW_H, Theme.ROW);
                c.text(GuiTools.trimName(font, label, rw - 10), rx + 5, ry + 5, color);
            }

            public boolean click(double mx, double my, int rx, int ry, int rw, int button) {
                if (button == 0) {
                    onClick.run();
                    return true;
                }
                return false;
            }
        };
    }

    private ScrollList.Row soundRow(File file) {
        final String name = file.getName();
        return new ScrollList.Row() {
            public int height() {
                return ROW_H;
            }

            public void draw(UiCanvas c, int rx, int ry, int rw, boolean hovered) {
                boolean sel = selected != null && selected.getName().equals(name);
                boolean playing = SoundboardAudioSystem.isPlaying(name);
                boolean fav = SoundboardConfig.get(name).isFavorite();
                if (sel) c.fillRoundRect(rx, ry, rw, ROW_H, Theme.ROW_HOVER);
                else if (hovered) c.fillRoundRect(rx, ry, rw, ROW_H, Theme.ROW);

                c.fillRoundRect(rx + 3, ry + 2, PLAY_W, ROW_H - 4, playing ? 0xFFB23A3A : Theme.ACCENT);
                c.centeredText(Component.translatable(playing ? "gui.opensoundboard.stop" : "gui.opensoundboard.play"),
                        rx + 3 + PLAY_W / 2, ry + 5, Theme.TEXT_ON_ACCENT);

                c.text(fav ? "★" : "☆", rx + PLAY_W + 8, ry + 5, fav ? 0xFFF0C044 : Theme.TEXT_MUTED);

                int nameX = rx + PLAY_W + 8 + STAR_W + 4;
                String label = GuiTools.trimName(font, GuiTools.baseName(file), rw - (nameX - rx) - 6);
                c.text(label, nameX, ry + 5, fav ? 0xFF8B85F0 : (playing ? 0xFFECD27A : Theme.TEXT));
            }

            public boolean click(double mx, double my, int rx, int ry, int rw, int button) {
                if (button != 0) return false;
                if (mx < rx + 3 + PLAY_W) {
                    select(file);
                    togglePlay(file);
                } else if (mx < rx + PLAY_W + 8 + STAR_W + 4) {
                    var data = SoundboardConfig.get(name);
                    data.setFavorite(!data.isFavorite());
                    SoundboardConfig.save();
                    scanSounds();
                } else {
                    select(file);
                }
                return true;
            }
        };
    }

    private Comparator<File> comparator() {
        Comparator<File> base = switch (SoundboardConfig.data.getSortMode()) {
            case "date" -> Comparator.comparingLong(File::lastModified);
            case "length" -> Comparator.comparingLong(f -> Math.max(0, SoundboardAudioSystem.getDurationMillis(f.getName())));
            default -> Comparator.comparing(f -> f.getName().toLowerCase());
        };
        if (!SoundboardConfig.data.isSortAscending()) base = base.reversed();
        return Comparator.comparing((File f) -> SoundboardConfig.get(f.getName()).isFavorite()).reversed().thenComparing(base);
    }

    // ---------------------------------------------------------------- selection / details

    private void select(File file) {
        selected = file;
        binding = false;
        refreshDetails();
    }

    private void refreshDetails() {
        // The details controls stay visible at all times (like the original); they just show a
        // neutral state and no-op safely when nothing is selected.
        if (selected == null) {
            localSlider.set(0);
            playerSlider.set(0);
            bindBtn.setLabel(GuiTools.keyBindLabel(null));
            if (!timeline.isFocused()) timeline.set(0);
            return;
        }
        var data = SoundboardConfig.get(selected.getName());
        localSlider.set(data.getLocalVolume());
        playerSlider.set(data.getPlayerVolume());
        bindBtn.setLabel(binding
                ? Component.translatable("gui.opensoundboard.keybind.listening")
                : GuiTools.keyBindLabel(data.getKeybind()));
        boolean playing = SoundboardAudioSystem.isPlaying(selected.getName());
        pauseBtn.setLabel(Component.literal(SoundboardAudioSystem.isPaused(selected.getName()) ? "▶" : "⏸"));
        if (playing) {
            if (!timeline.isFocused()) timeline.set(SoundboardAudioSystem.getProgress(selected.getName()));
            if (!timeField.isFocused())
                timeField.setText(GuiTools.formatTimeMillis(SoundboardAudioSystem.getTimeMillis(selected.getName())));
        }
    }

    @Override
    protected void renderContent(UiCanvas c) {
        c.fillRect(0, 0, this.width, this.height, Theme.SCRIM);
        c.fillRoundRect(px, py, pw, ph, Theme.PANEL);
        c.roundBorder(px, py, pw, ph, Theme.BORDER);
        c.fillRect(px + Theme.RADIUS, py, pw - Theme.RADIUS * 2, 3, Theme.ACCENT);
        c.centeredText(Component.translatable("gui.opensoundboard.title"), px + pw / 2, py + 12, Theme.TEXT);

        int hy = detailsTop - 11;
        if (selected == null) {
            c.centeredText(Component.translatable("gui.opensoundboard.select_hint"), px + pw / 2, hy, Theme.TEXT_MUTED);
        } else {
            c.centeredText(Component.translatable("gui.opensoundboard.settings_for", selected.getName()), px + pw / 2, hy, 0xFFECD27A);
        }
    }

    private void onVolume(double v, boolean local) {
        if (selected == null) return;
        var data = SoundboardConfig.get(selected.getName());
        float f = (float) v;
        if (SoundboardConfig.data.isSyncAudio()) {
            data.setLocalVolume(f);
            data.setPlayerVolume(f);
        } else if (local) {
            data.setLocalVolume(f);
        } else {
            data.setPlayerVolume(f);
        }
        SoundboardAudioSystem.setVolume(selected.getName(), data.getLocalVolume(), data.getPlayerVolume());
        SoundboardConfig.save();
    }

    private void togglePlay(File file) {
        String key = file.getName();
        if (SoundboardAudioSystem.isPlaying(key)) SoundboardAudioSystem.stop(key);
        else SoundboardAudioSystem.playFile(file, SoundboardConfig.get(key).getLocalVolume(), SoundboardConfig.get(key).getPlayerVolume());
    }

    private void togglePause() {
        if (selected == null) return;
        String key = selected.getName();
        if (SoundboardAudioSystem.isPaused(key)) SoundboardAudioSystem.resume(key);
        else SoundboardAudioSystem.pause(key);
    }

    private void skip(int dir) {
        if (selected != null) SoundboardAudioSystem.skip(selected.getName(), dir * SoundboardConfig.data.getSkipAmountSeconds());
    }

    private void toggleLoop() {
        SoundboardConfig.data.setLoopAll(!SoundboardConfig.data.isLoopAll());
        SoundboardConfig.save();
        SoundboardAudioSystem.setGlobalLooping(SoundboardConfig.data.isLoopAll());
    }

    private void setStart() {
        if (selected == null) return;
        var data = SoundboardConfig.get(selected.getName());
        data.setStartingPoint(Math.max(0f, SoundboardAudioSystem.getProgress(selected.getName())));
        SoundboardConfig.save();
    }

    private void startBinding() {
        if (selected != null) {
            binding = true;
            bindBtn.setLabel(Component.translatable("gui.opensoundboard.keybind.listening"));
        }
    }

    private Component timelineLabel() {
        if (selected == null) return Component.literal("0:00.0 / 0:00.0");
        String key = selected.getName();
        long dur = SoundboardAudioSystem.getDurationMillis(key);
        long passed = SoundboardAudioSystem.getTimeMillis(key);
        if (dur > 0) {
            passed = Math.max(0, Math.min(dur, passed));
            return Component.literal(GuiTools.formatTimeMillis(passed) + " / -" + GuiTools.formatTimeMillis(Math.max(0, dur - passed)));
        }
        return Component.literal("0:00.0 / 0:00.0");
    }

    // ---------------------------------------------------------------- toolbar helpers

    private Component sortModeLabel() {
        return Component.translatable("gui.opensoundboard.sort." + SoundboardConfig.data.getSortMode());
    }

    private Component sortDirLabel() {
        return Component.literal(SoundboardConfig.data.isSortAscending() ? "▲" : "▼");
    }

    private void cycleSortMode() {
        String[] modes = {"name", "date", "length"};
        String cur = SoundboardConfig.data.getSortMode();
        int next = 0;
        for (int i = 0; i < modes.length; i++) if (modes[i].equals(cur)) next = (i + 1) % modes.length;
        SoundboardConfig.data.setSortMode(modes[next]);
        SoundboardConfig.save();
        sortModeBtn.setLabel(sortModeLabel());
        scanSounds();
    }

    private void toggleSortDir() {
        SoundboardConfig.data.setSortAscending(!SoundboardConfig.data.isSortAscending());
        SoundboardConfig.save();
        sortDirBtn.setLabel(sortDirLabel());
        scanSounds();
    }

    private void refresh() {
        File[] all = OpenSoundboardClient.soundDir.listFiles((d, nm) -> nm.endsWith(".mp3"));
        if (all != null) {
            for (File file : all) {
                String base = file.getName().substring(0, file.getName().length() - 4);
                String sanitized = YtDlpManager.sanitizeTrackName(base);
                if (sanitized.isBlank()) sanitized = "track";
                String newName = sanitized + ".mp3";
                if (newName.equals(file.getName())) continue;
                File target = new File(OpenSoundboardClient.soundDir, newName);
                for (int i = 2; target.exists(); i++) target = new File(OpenSoundboardClient.soundDir, sanitized + " (" + i + ").mp3");
                if (file.renameTo(target)) SoundboardAudioSystem.scanFile(target);
            }
        }
        scanSounds();
    }

    private static int pct(double v) {
        return Math.max(0, Math.min(100, (int) Math.round(v * 100)));
    }

    // ---------------------------------------------------------------- input / tick

    @Override
    protected boolean screenKeyPressed(int key, int scan, int mods) {
        if (binding && selected != null) {
            var data = SoundboardConfig.get(selected.getName());
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                data.setKeybind(null);
            } else {
                data.setKeybind(new SoundboardConfig.KeyBind(key, scan, mods));
            }
            SoundboardConfig.save();
            binding = false;
            refreshDetails();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER && search != null && search.isFocused() && !results.isEmpty()) {
            select(results.get(0));
            togglePlay(results.get(0));
            return true;
        }
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (selected != null) refreshDetails();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
